package com.app.buscandohuellas.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.app.buscandohuellas.data.dto.ReportDto
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportImageUploadResult
import com.app.buscandohuellas.domain.repository.ReportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ReportRepository"

class ReportRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) : ReportRepository {

    override suspend fun createReport(report: Report): Result<Unit> = try {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(Exception("Usuario no autenticado"))

        val imageUrls = report.imageUrls.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(report.imageUrl?.takeIf { it.isNotBlank() }) }

        val payloadWithImageUrls = buildReportPayload(
            report = report,
            userId = userId,
            imageUrls = imageUrls,
            includeImageUrls = true
        )

        try {
            supabaseClient.from("reports").insert(payloadWithImageUrls)
        } catch (error: Exception) {
            if (imageUrls.isEmpty() || !isMissingImageUrlsColumn(error)) throw error

            Log.w(
                TAG,
                "La columna image_urls no existe en Supabase. " +
                    "Se guardará solo la primera foto hasta aplicar la migración."
            )
            supabaseClient.from("reports").insert(
                buildReportPayload(
                    report = report,
                    userId = userId,
                    imageUrls = imageUrls,
                    includeImageUrls = false
                )
            )
        }

        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en createReport", error)
        Result.failure(Exception(friendlyMessage(error)))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en createReport", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en createReport", error)
        Result.failure(Exception(error.message ?: "No se pudo publicar el reporte. Inténtalo de nuevo."))
    }

    private fun buildReportPayload(
        report: Report,
        userId: String,
        imageUrls: List<String>,
        includeImageUrls: Boolean
    ): JsonObject = buildJsonObject {
        put("user_id", userId)
        put("pet_name", report.petName)
        put("description", report.description)
        put("status", report.status)
        put("image_url", imageUrls.firstOrNull() ?: "")

        if (includeImageUrls && imageUrls.isNotEmpty()) {
            put("image_urls", buildJsonArray { imageUrls.forEach { add(it) } })
        }

        if (report.longitude != null && report.latitude != null) {
            put("location", "POINT(${report.longitude} ${report.latitude})")
        }
    }

    private fun isMissingImageUrlsColumn(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty()
            val mentionsColumn = message.contains("image_urls", ignoreCase = true)
            val isMissing = message.contains("does not exist", ignoreCase = true) ||
                message.contains("could not find", ignoreCase = true) ||
                message.contains("schema cache", ignoreCase = true)

            if (mentionsColumn && isMissing) return true
            current = current.cause
        }
        return false
    }

    override suspend fun uploadReportImages(uris: List<Uri>): Result<ReportImageUploadResult> {
        if (uris.isEmpty()) {
            return Result.success(ReportImageUploadResult(urls = emptyList(), failedCount = 0))
        }

        return try {
            val uploadedUrls = mutableListOf<String>()
            var failedCount = 0
            val timestamp = System.currentTimeMillis()

            uris.forEachIndexed { index, uri ->
                try {
                    val bytes = readImageBytes(uri)
                    val extension = resolveImageExtension(uri)
                    val fileName = "${timestamp}_${UUID.randomUUID()}_$index.$extension"

                    uploadedUrls += uploadImage(bytes, extension, fileName)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failedCount++
                    Log.e(TAG, "No se pudo subir la imagen $index", error)
                }
            }

            Result.success(
                ReportImageUploadResult(
                    urls = uploadedUrls,
                    failedCount = failedCount
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Error inesperado al subir imágenes: ${error.message}", error)
            Result.failure(Exception(error.message ?: "No se pudieron subir las imágenes."))
        }
    }

    private fun readImageBytes(uri: Uri): ByteArray {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo leer la imagen seleccionada.")

        if (bytes.size > MAX_IMAGE_SIZE_BYTES) {
            throw IllegalStateException("La imagen supera el tamaño máximo de 5MB.")
        }
        return bytes
    }

    private fun resolveImageExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> {
                val path = uri.lastPathSegment.orEmpty()
                if (path.contains(".")) {
                    path.substringAfterLast(".").lowercase().ifBlank { "jpg" }
                } else {
                    "jpg"
                }
            }
        }
    }

    private suspend fun uploadImage(bytes: ByteArray, extension: String, fileName: String): String {
        Log.d(TAG, "Subiendo imagen a bucket=$BUCKET_NAME path=$fileName size=${bytes.size}")

        supabaseClient.storage.from(BUCKET_NAME).upload(fileName, bytes) {
            upsert = false
            contentType = when (extension.lowercase()) {
                "png" -> ContentType.Image.PNG
                "webp" -> ContentType.Image.Any
                "gif" -> ContentType.Image.GIF
                "bmp" -> ContentType.Image.Any
                else -> ContentType.Image.JPEG
            }
        }

        val publicUrl = supabaseClient.storage.from(BUCKET_NAME).publicUrl(fileName)
        Log.d(TAG, "Imagen subida exitosamente: $publicUrl")
        return publicUrl
    }

    override suspend fun deleteReportImage(imageUrl: String): Result<Unit> = try {
        val path = imageUrl.substringAfterLast("/").substringBefore("?")
        if (path.isNotBlank()) {
            supabaseClient.storage.from(BUCKET_NAME).delete(path)
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "No se pudo eliminar la imagen del storage", error)
        Result.success(Unit)
    }

    override suspend fun getAllReports(): Result<List<Report>> = try {
        Log.d(TAG, "🔍 Iniciando consulta de reportes con perfil del reportante...")

        val reports = selectReportDtos()
            .map { dto -> dto.toDomain() }

        // Ordeno en el repositorio para que mapa e Inicio reciban la misma lista descendente por fecha.
        val sortedReports = reports
            .map { report -> report to (parseCreatedAt(report.createdAt) ?: Instant.MIN) }
            .sortedByDescending { it.second }
            .map { it.first }

        Log.d(TAG, "Reportes finales: ${sortedReports.size}")
        Result.success(sortedReports)
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en getAllReports", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: RestException) {
        Log.e(TAG, "RestException en getAllReports", error)
        Result.failure(Exception(friendlyMessage(error)))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en getAllReports", error)
        Result.failure(Exception(error.message ?: "No se pudieron cargar los reportes. Inténtalo de nuevo."))
    }

    override suspend fun getReportById(reportId: String): Result<Report> = try {
        Log.d(TAG, "Consultando reporte por id=$reportId")

        val dto = selectReportDtos(reportId).firstOrNull()
            ?: return Result.failure(Exception("El reporte no existe o fue eliminado."))

        Result.success(dto.toDomain())
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en getReportById", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: RestException) {
        Log.e(TAG, "RestException en getReportById", error)
        Result.failure(Exception(friendlyMessage(error)))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en getReportById", error)
        Result.failure(Exception(error.message ?: "No se pudo cargar el reporte. Inténtalo de nuevo."))
    }

    /**
     * Consulta los reportes con el perfil del reportante embebido mediante la
     * relación PostgREST `reports.user_id -> profiles.id`.
     *
     * Si la relación todavía no existe (por ejemplo, la migración SQL no se
     * aplicó), se reintenta contra `reports_view` para no romper la pantalla.
     */
    private suspend fun selectReportDtos(reportId: String? = null): List<ReportDto> {
        return try {
            supabaseClient
                .from(REPORTS_TABLE)
                .select(Columns.raw(REPORTS_WITH_PROFILE_SELECT)) {
                    if (reportId != null) {
                        filter { eq("id", reportId) }
                    }
                }
                .decodeList<ReportDto>()
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpRequestTimeoutException) {
            throw error
        } catch (error: HttpRequestException) {
            throw error
        } catch (error: Exception) {
            Log.w(
                TAG,
                "No se pudo embeber el perfil del reportante; se consulta reports_view",
                error
            )
            supabaseClient
                .from(REPORTS_VIEW)
                .select {
                    if (reportId != null) {
                        filter { eq("id", reportId) }
                    }
                }
                .decodeList<ReportDto>()
        }
    }

    private fun parseCreatedAt(createdAt: String?): Instant? {
        if (createdAt.isNullOrBlank()) return null

        val value = createdAt.trim()
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (error: DateTimeParseException) {
            try {
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
            } catch (fallbackError: DateTimeParseException) {
                Log.w(TAG, "Fecha de creación inválida: $createdAt", fallbackError)
                null
            }
        }
    }

    private fun friendlyMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("row-level security", ignoreCase = true) ||
                    message.contains("permission denied", ignoreCase = true) ||
                    message.contains("violates row-level", ignoreCase = true) ->
                "No tienes permiso para publicar el reporte."

            message.contains("does not exist", ignoreCase = true) ->
                "El almacenamiento de reportes no está disponible."

            message.isNotBlank() -> message

            else -> "No se pudo publicar el reporte. Inténtalo de nuevo."
        }
    }

    companion object {
        private const val BUCKET_NAME = "pet-reports"
        private const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024

        private const val REPORTS_TABLE = "reports"
        private const val REPORTS_VIEW = "reports_view"

        /**
         * `profiles(...)` usa la relación PostgREST creada por la FK
         * `reports.user_id -> profiles.id` de la migración de perfiles.
         */
        private const val REPORTS_WITH_PROFILE_SELECT =
            "*,profiles(full_name,avatar_url)"
    }
}