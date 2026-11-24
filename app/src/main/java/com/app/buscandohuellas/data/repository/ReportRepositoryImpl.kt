package com.app.buscandohuellas.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.app.buscandohuellas.data.dto.ReportDto
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportImageUploadResult
import com.app.buscandohuellas.domain.model.ReportReason
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.model.ReportedPost
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ReportRepository"
private const val BH_DEBUG_TAG = "BH_DEBUG"

class ReportRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) : ReportRepository {

    override suspend fun createReport(report: Report): Result<String> = try {
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

        val insertedRows = try {
            supabaseClient.from(REPORTS_TABLE).insert(payloadWithImageUrls) {
                select(Columns.raw("id"))
            }.decodeList<JsonObject>()
        } catch (error: Exception) {
            if (imageUrls.isEmpty() || !isMissingImageUrlsColumn(error)) throw error

            Log.w(
                TAG,
                "La columna image_urls no existe en Supabase. " +
                    "Se guardará solo la primera foto hasta aplicar la migración."
            )
            supabaseClient.from(REPORTS_TABLE).insert(
                buildReportPayload(
                    report = report,
                    userId = userId,
                    imageUrls = imageUrls,
                    includeImageUrls = false
                )
            ) {
                select(Columns.raw("id"))
            }.decodeList<JsonObject>()
        }

        val reportId = insertedRows.firstOrNull()?.get("id")
            ?.toString()
            ?.trim('"')
            .orEmpty()

        if (reportId.isBlank()) {
            Result.failure(Exception("No se pudo obtener el ID del reporte creado."))
        } else {
            Result.success(reportId)
        }
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
        put("status", report.status.value)
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

    /**
     * Reportes del usuario autenticado, ordenados por `created_at` descendente.
     *
     * Reutiliza [selectReportDtos] (con el perfil del reportante embebido) y el
     * mismo parseo de fechas que [getAllReports] para mantener la coherencia.
     */
    override fun getReportsByUser(userId: String): Flow<List<Report>> = flow {
        if (userId.isBlank()) {
            throw IllegalStateException("No se pudo identificar al usuario autenticado.")
        }

        Log.d(TAG, "🔍 Consultando reportes del usuario $userId...")

        val reports = try {
            selectReportDtos(userId = userId)
                .map { dto -> dto.toDomain() }
                .map { report -> report to (parseCreatedAt(report.createdAt) ?: Instant.MIN) }
                .sortedByDescending { it.second }
                .map { it.first }
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpRequestException) {
            Log.e(TAG, "HttpRequestException en getReportsByUser", error)
            throw Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo.")
        } catch (error: RestException) {
            Log.e(TAG, "RestException en getReportsByUser", error)
            throw Exception(friendlyMessage(error))
        } catch (error: Exception) {
            Log.e(TAG, "Error inesperado en getReportsByUser", error)
            throw Exception(
                error.message ?: "No se pudieron cargar tus reportes. Inténtalo de nuevo."
            )
        }

        Log.d(TAG, "Reportes del usuario $userId: ${reports.size}")
        emit(reports)
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

    override suspend fun updateReport(
        reportId: String,
        report: Report
    ): Result<Unit> = try {
        val imageUrls = report.imageUrls.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val payload = buildJsonObject {
            put("pet_name", report.petName.trim())
            put("description", report.description.trim())
            put("status", report.status.value)
            put("image_url", imageUrls.firstOrNull() ?: "")
        }

        val payloadWithImageUrls = buildJsonObject {
            payload.forEach { (key, value) -> put(key, value) }
            // Se envía incluso cuando está vacío para poder eliminar todas las fotos.
            put("image_urls", buildJsonArray { imageUrls.forEach { add(it) } })
        }

        val updatedRows = try {
            patchReport(reportId, payloadWithImageUrls)
        } catch (error: Exception) {
            if (!isMissingImageUrlsColumn(error)) throw error

            Log.w(
                TAG,
                "La columna image_urls no existe en Supabase. " +
                    "Se actualizará solo la primera foto hasta aplicar la migración."
            )
            patchReport(reportId, payload)
        }

        if (updatedRows.isEmpty()) {
            Result.failure(
                Exception(
                    "No se pudo actualizar el reporte. " +
                        "Verifica que seas el dueño del reporte."
                )
            )
        } else {
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en updateReport", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: RestException) {
        Log.e(TAG, "RestException en updateReport", error)
        Result.failure(
            Exception(
                friendlyMessage(error)
                    .replace("publicar el reporte", "actualizar el reporte")
            )
        )
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en updateReport", error)
        Result.failure(
            Exception(error.message ?: "No se pudo actualizar el reporte. Inténtalo de nuevo.")
        )
    }

    private suspend fun patchReport(
        reportId: String,
        payload: JsonObject
    ): List<JsonObject> = supabaseClient
        .from(REPORTS_TABLE)
        .update(
            body = payload,
            request = {
                select(Columns.raw("id"))
                filter { eq("id", reportId) }
            }
        )
        .decodeList<JsonObject>()

    override suspend fun updateReportStatus(
        reportId: String,
        status: ReportStatus
    ): Result<Unit> = try {
        val updatedRows = supabaseClient
            .from(REPORTS_TABLE)
            .update(
                update = { set("status", status.value) },
                request = {
                    select(Columns.raw("id"))
                    filter { eq("id", reportId) }
                }
            )
            .decodeList<JsonObject>()

        if (updatedRows.isEmpty()) {
            Result.failure(Exception(NO_PERMISSION_MESSAGE))
        } else {
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en updateReportStatus", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: RestException) {
        Log.e(TAG, "RestException en updateReportStatus", error)
        Result.failure(
            Exception(
                friendlyMessage(error)
                    .replace("publicar el reporte", "actualizar el estado")
            )
        )
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en updateReportStatus", error)
        Result.failure(
            Exception(error.message ?: "No se pudo actualizar el estado. Inténtalo de nuevo.")
        )
    }

    override suspend fun submitReport(
        reportId: String,
        reporterId: String,
        reason: ReportReason,
        customReasonText: String?
    ): Result<Unit> = try {
        val trimmedCustomReason = customReasonText?.trim().orEmpty()

        val payload = buildJsonObject {
            put("report_id", reportId)
            put("reporter_id", reporterId)
            put("reason", reason.value)
            if (trimmedCustomReason.isNotBlank()) {
                put("custom_reason_text", trimmedCustomReason)
            }
        }

        supabaseClient.from(REPORT_FLAGS_TABLE).insert(payload)
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en submitReport", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: RestException) {
        Log.e(TAG, "RestException en submitReport", error)
        val message = error.message.orEmpty()
        val messageForUser = when {
            message.contains("duplicate key", ignoreCase = true) ||
                message.contains("23505") ||
                message.contains("unique", ignoreCase = true) ->
                "Ya reportaste esta publicación."

            message.contains("row-level security", ignoreCase = true) ||
                message.contains("permission denied", ignoreCase = true) ||
                message.contains("violates row-level", ignoreCase = true) ->
                "No tienes permiso para reportar esta publicación."

            else -> "No se pudo enviar el reporte. Inténtalo de nuevo."
        }
        Result.failure(Exception(messageForUser))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en submitReport", error)
        Result.failure(
            Exception(error.message ?: "No se pudo enviar el reporte. Inténtalo de nuevo.")
        )
    }

    /**
     * Rol del usuario según `public.user_roles`. Es un Flow frío: cada
     * colección vuelve a consultar Supabase, así que un cambio de rol se
     * refleja sin necesidad de cerrar sesión. Ante cualquier error se emite
     * `null` y la app aplica el rol `user` por defecto (fail-safe).
     */
    override fun getUserRole(userId: String): Flow<String?> = flow {
        if (userId.isBlank()) {
            Log.w(BH_DEBUG_TAG, "Verificando rol del usuario: (id vacío)")
            emit(null)
            return@flow
        }

        Log.d(BH_DEBUG_TAG, "Verificando rol del usuario: $userId")
        val role = try {
            supabaseClient
                .from(USER_ROLES_TABLE)
                .select(Columns.raw("role")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<UserRoleRow>()
                .firstOrNull()
                ?.role
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it in USER_ROLES }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(BH_DEBUG_TAG, "No se pudo consultar el rol de $userId", error)
            null
        }

        Log.d(
            BH_DEBUG_TAG,
            if (role == null) "Rol obtenido: $ROLE_USER (por defecto)" else "Rol obtenido: $role"
        )
        emit(role)
    }

    override suspend fun deleteReport(reportId: String): Result<Unit> = try {
        Log.d(BH_DEBUG_TAG, "Intentando eliminar reporte: $reportId")

        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: run {
                Log.e(BH_DEBUG_TAG, "Eliminación fallida: usuario no autenticado")
                return Result.failure(Exception("Usuario no autenticado."))
            }

        val role = getUserRole(currentUserId).first() ?: ROLE_USER
        val isModerator = role == ROLE_MODERATOR || role == ROLE_ADMIN

        // La lectura es best-effort: sirve para recuperar las imágenes y para
        // distinguir "no existe" de "sin permiso" cuando el usuario es dueño.
        val existingReport = getReportById(reportId)
        val report = existingReport.getOrNull()
        val imageUrls = report?.allImageUrls.orEmpty()

        if (!isModerator) {
            if (report == null) {
                val message = if (existingReport.exceptionOrNull()?.message
                        ?.contains("no existe", ignoreCase = true) == true
                ) {
                    REPORT_GONE_MESSAGE
                } else {
                    NO_PERMISSION_MESSAGE
                }
                Log.e(BH_DEBUG_TAG, "Eliminación fallida: $message")
                return Result.failure(Exception(message))
            }
            if (report.userId != currentUserId) {
                Log.e(
                    BH_DEBUG_TAG,
                    "Eliminación fallida: el usuario $currentUserId no es dueño ni moderador"
                )
                return Result.failure(Exception(MODERATOR_PERMISSION_MESSAGE))
            }
        }

        val deletedRows = supabaseClient
            .from(REPORTS_TABLE)
            .delete(
                request = {
                    select(Columns.raw("id"))
                    filter { eq("id", reportId) }
                }
            )
            .decodeList<JsonObject>()

        if (deletedRows.isEmpty()) {
            // PostgREST devuelve 0 filas tanto si no existe como si RLS bloquea.
            val message = if (report == null) REPORT_GONE_MESSAGE else MODERATOR_PERMISSION_MESSAGE
            Log.e(BH_DEBUG_TAG, "Eliminación fallida: $message")
            Result.failure(Exception(message))
        } else {
            imageUrls.forEach { imageUrl ->
                runBestEffort("No se pudo eliminar la imagen $imageUrl") {
                    deleteReportImage(imageUrl)
                }
            }
            runBestEffort("No se pudo registrar la acción DELETE") {
                logModerationAction(reportId, ACTION_DELETE, null)
            }
            Log.d(BH_DEBUG_TAG, "Eliminación exitosa: $reportId")
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(BH_DEBUG_TAG, "Error al eliminar $reportId: ${error.message}", error)
        Result.failure(Exception(moderationRestMessage(error, "eliminar el reporte")))
    } catch (error: HttpRequestException) {
        Log.e(BH_DEBUG_TAG, "Error de conexión al eliminar $reportId", error)
        Result.failure(Exception(CONNECTION_MESSAGE))
    } catch (error: Exception) {
        Log.e(BH_DEBUG_TAG, "Error inesperado al eliminar $reportId: ${error.message}", error)
        Result.failure(Exception("No se pudo eliminar el reporte. Inténtalo de nuevo."))
    }

    override suspend fun updateReportAsModerator(
        reportId: String,
        description: String,
        status: ReportStatus,
        reason: String?
    ): Result<Unit> = try {
        val cleanDescription = description.trim()
        if (cleanDescription.isBlank()) {
            return Result.failure(Exception("La descripción no puede estar vacía."))
        }

        val updatedRows = supabaseClient
            .from(REPORTS_TABLE)
            .update(
                body = buildJsonObject {
                    put("description", cleanDescription)
                    put("status", status.value)
                },
                request = {
                    select(Columns.raw("id"))
                    filter { eq("id", reportId) }
                }
            )
            .decodeList<JsonObject>()

        if (updatedRows.isEmpty()) {
            Result.failure(Exception(MODERATOR_PERMISSION_MESSAGE))
        } else {
            runBestEffort("No se pudieron resolver las denuncias del reporte") {
                resolveReportFlags(reportId)
            }
            runBestEffort("No se pudo registrar la acción EDIT") {
                logModerationAction(
                    reportId = reportId,
                    action = ACTION_EDIT,
                    reason = reason?.trim()?.takeIf { it.isNotBlank() }
                )
            }
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en updateReportAsModerator", error)
        Result.failure(Exception(moderationRestMessage(error, "actualizar el reporte")))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en updateReportAsModerator", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en updateReportAsModerator", error)
        Result.failure(Exception("No se pudo actualizar el reporte. Inténtalo de nuevo."))
    }

    override suspend fun getReportedPosts(): Result<List<ReportedPost>> = try {
        // Un solo query con `reports(*)` embebido gracias a la FK
        // report_flags.report_id -> reports.id (ON DELETE CASCADE).
        val flags = supabaseClient
            .from(REPORT_FLAGS_TABLE)
            .select(Columns.raw(REPORT_FLAGS_WITH_REPORT_SELECT)) {
                filter { eq("status", FLAG_STATUS_PENDING) }
            }
            .decodeList<ReportFlagRow>()

        // Segunda consulta en bloque para los nombres, nunca una por denuncia.
        val reporterNames = loadReporterNames(flags)

        val reportedPosts = flags
            .groupBy { it.reportId }
            .mapNotNull { (reportId, reportFlags) ->
                val latestFlag = reportFlags.maxByOrNull {
                    parseCreatedAt(it.createdAt) ?: Instant.MIN
                } ?: return@mapNotNull null

                val report = reportFlags
                    .firstNotNullOfOrNull { it.reports }
                    ?.toDomain()
                    ?: return@mapNotNull null

                val normalizedReport = if (report.id.isNullOrBlank()) {
                    report.copy(id = reportId)
                } else {
                    report
                }

                ReportedPost(
                    report = normalizedReport,
                    reporterCount = reportFlags.size,
                    latestReason = reportReasonLabel(
                        reason = latestFlag.reason,
                        customReasonText = latestFlag.customReasonText
                    ),
                    latestReportedAt = latestFlag.createdAt,
                    latestReporterName = latestFlag.reporterId
                        ?.let { reporterNames[it] }
                        ?.takeIf { it.isNotBlank() }
                )
            }
            .sortedByDescending { parseCreatedAt(it.latestReportedAt) ?: Instant.MIN }

        Result.success(reportedPosts)
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en getReportedPosts", error)
        Result.failure(Exception(moderationRestMessage(error, "cargar las denuncias")))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en getReportedPosts", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en getReportedPosts", error)
        Result.failure(Exception("No se pudieron cargar las denuncias. Inténtalo de nuevo."))
    }

    override suspend fun dismissReportFlags(reportId: String): Result<Unit> = try {
        val updatedRows = updateFlagStatus(reportId, FLAG_STATUS_DISMISSED)

        if (updatedRows.isEmpty()) {
            Result.failure(Exception(MODERATOR_PERMISSION_MESSAGE))
        } else {
            runBestEffort("No se pudo registrar la acción DISMISS") {
                logModerationAction(reportId, ACTION_DISMISS, null)
            }
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en dismissReportFlags", error)
        Result.failure(Exception(moderationRestMessage(error, "descartar las denuncias")))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en dismissReportFlags", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en dismissReportFlags", error)
        Result.failure(Exception("No se pudieron descartar las denuncias. Inténtalo de nuevo."))
    }

    override suspend fun resolveReportFlags(reportId: String): Result<Unit> = try {
        val updatedRows = updateFlagStatus(reportId, FLAG_STATUS_RESOLVED)

        if (updatedRows.isEmpty()) {
            Result.failure(Exception(NO_PERMISSION_MESSAGE))
        } else {
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en resolveReportFlags", error)
        Result.failure(Exception(moderationRestMessage(error, "resolver las denuncias")))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en resolveReportFlags", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en resolveReportFlags", error)
        Result.failure(Exception("No se pudieron resolver las denuncias. Inténtalo de nuevo."))
    }

    override suspend fun logModerationAction(
        reportId: String,
        action: String,
        reason: String?
    ): Result<Unit> = try {
        val normalizedAction = action.trim().uppercase(Locale.ROOT)
        if (normalizedAction !in MODERATION_ACTIONS) {
            return Result.failure(Exception("Acción de moderación no válida."))
        }

        val payload = buildJsonObject {
            put("report_id", reportId)
            put("action", normalizedAction)
            reason?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { put("reason", it) }
        }

        supabaseClient.from(MODERATION_LOGS_TABLE).insert(payload)
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en logModerationAction", error)
        Result.failure(Exception(moderationRestMessage(error, "registrar la acción de moderación")))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en logModerationAction", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en logModerationAction", error)
        Result.failure(Exception("No se pudo registrar la acción de moderación."))
    }

    private suspend fun updateFlagStatus(reportId: String, status: String): List<JsonObject> =
        supabaseClient
            .from(REPORT_FLAGS_TABLE)
            .update(
                body = buildJsonObject { put("status", status) },
                request = {
                    select(Columns.raw("id"))
                    filter {
                        eq("report_id", reportId)
                        eq("status", FLAG_STATUS_PENDING)
                    }
                }
            )
            .decodeList<JsonObject>()

    private suspend fun loadReporterNames(flags: List<ReportFlagRow>): Map<String, String?> {
        val reporterIds = flags.mapNotNull { it.reporterId }.distinct()
        if (reporterIds.isEmpty()) return emptyMap()

        return try {
            supabaseClient
                .from(PROFILES_TABLE)
                .select(Columns.raw("id,full_name")) {
                    filter { isIn("id", reporterIds) }
                }
                .decodeList<ReporterProfileRow>()
                .mapNotNull { row -> row.id?.let { id -> id to row.fullName } }
                .toMap()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // El nombre es información secundaria: la tarjeta puede mostrarse
            // con el alias "usuario" sin romper el panel.
            Log.w(TAG, "No se pudieron cargar los nombres de los denunciantes", error)
            emptyMap()
        }
    }

    private suspend fun runBestEffort(
        description: String,
        action: suspend () -> Result<Unit>
    ) {
        try {
            action().exceptionOrNull()?.let { error -> Log.w(TAG, description, error) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, description, error)
        }
    }

    private fun reportReasonLabel(reason: String?, customReasonText: String?): String {
        val customReason = customReasonText?.trim().orEmpty()
        if (customReason.isNotBlank()) return customReason

        val normalizedReason = reason?.trim().orEmpty()
        return ReportReason.entries
            .firstOrNull { it.value.equals(normalizedReason, ignoreCase = true) }
            ?.displayName
            ?: normalizedReason.takeIf { it.isNotBlank() }
            ?: "Motivo no especificado"
    }

    private fun moderationRestMessage(error: Throwable, action: String): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("row-level security", ignoreCase = true) ||
                message.contains("permission denied", ignoreCase = true) ||
                message.contains("violates row-level", ignoreCase = true) ->
                MODERATOR_PERMISSION_MESSAGE

            message.contains("does not exist", ignoreCase = true) ||
                message.contains("could not find", ignoreCase = true) ||
                message.contains("schema cache", ignoreCase = true) ->
                "La función de moderación no está disponible. Verifica la configuración de Supabase."

            else -> "No se pudo $action. Inténtalo de nuevo."
        }
    }

    /**
     * Consulta los reportes con el perfil del reportante embebido mediante la
     * relación PostgREST `reports.user_id -> profiles.id`.
     *
     * Si la relación todavía no existe (por ejemplo, la migración SQL no se
     * aplicó), se reintenta contra `reports_view` para no romper la pantalla.
     */
    private suspend fun selectReportDtos(
        reportId: String? = null,
        userId: String? = null
    ): List<ReportDto> {
        return try {
            supabaseClient
                .from(REPORTS_TABLE)
                .select(Columns.raw(REPORTS_WITH_PROFILE_SELECT)) {
                    if (reportId != null) {
                        filter { eq("id", reportId) }
                    }
                    if (userId != null) {
                        filter { eq("user_id", userId) }
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
                    if (userId != null) {
                        filter { eq("user_id", userId) }
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

    override suspend fun getNearbyLostReports(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        maxDays: Int
    ): Result<List<Report>> = try {
        val now = Instant.now()
        val maxAgeMillis = maxDays.toLong() * 24L * 60L * 60L * 1000L

        val reports = selectReportDtos()
            .map { it.toDomain() }
            .filter { report ->
                report.status == ReportStatus.LOST &&
                    report.latitude != null &&
                    report.longitude != null
            }
            .filter { report ->
                val created = parseCreatedAt(report.createdAt)
                created == null || (now.toEpochMilli() - created.toEpochMilli()) <= maxAgeMillis
            }
            .mapNotNull { report ->
                val distance = haversineKm(
                    latitude,
                    longitude,
                    report.latitude!!,
                    report.longitude!!
                )
                if (distance <= radiusKm) report to distance else null
            }
            .sortedBy { it.second }
            .map { it.first }

        Result.success(reports)
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en getNearbyLostReports", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: RestException) {
        Log.e(TAG, "RestException en getNearbyLostReports", error)
        Result.failure(Exception(friendlyMessage(error)))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en getNearbyLostReports", error)
        Result.failure(Exception(error.message ?: "No se pudieron cargar reportes cercanos."))
    }

    override suspend fun createReportMatch(
        foundReportId: String,
        lostReportId: String,
        similarityScore: Float
    ): Result<Unit> = try {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(Exception("Usuario no autenticado"))

        supabaseClient.from(REPORT_MATCHES_TABLE).insert(
            buildJsonObject {
                put("found_report_id", foundReportId)
                put("lost_report_id", lostReportId)
                put("matched_by", userId)
                put("similarity_score", similarityScore.toDouble())
            }
        )
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en createReportMatch", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: RestException) {
        Log.e(TAG, "RestException en createReportMatch", error)
        Result.failure(Exception("No se pudo registrar la coincidencia."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en createReportMatch", error)
        Result.failure(Exception(error.message ?: "No se pudo registrar la coincidencia."))
    }

    private fun haversineKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earthRadiusKm * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    companion object {
        private const val BUCKET_NAME = "pet-reports"
        private const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024

        private const val REPORTS_TABLE = "reports"
        private const val REPORTS_VIEW = "reports_view"
        private const val REPORT_FLAGS_TABLE = "report_flags"
        private const val REPORT_MATCHES_TABLE = "report_matches"
        private const val PROFILES_TABLE = "profiles"
        private const val MODERATION_LOGS_TABLE = "moderation_logs"
        private const val USER_ROLES_TABLE = "user_roles"

        private const val FLAG_STATUS_PENDING = "PENDING"
        private const val FLAG_STATUS_DISMISSED = "DISMISSED"
        private const val FLAG_STATUS_RESOLVED = "RESOLVED"

        private const val ACTION_EDIT = "EDIT"
        private const val ACTION_DELETE = "DELETE"
        private const val ACTION_DISMISS = "DISMISS"

        private val MODERATION_ACTIONS = setOf(ACTION_EDIT, ACTION_DELETE, ACTION_DISMISS)

        private const val ROLE_USER = "user"
        private const val ROLE_MODERATOR = "moderator"
        private const val ROLE_ADMIN = "admin"
        private val USER_ROLES = setOf(ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN)

        private const val NO_PERMISSION_MESSAGE =
            "No tienes permiso o el reporte ya no existe."
        private const val MODERATOR_PERMISSION_MESSAGE =
            "No tienes permisos para realizar esta acción. Contacta al administrador."
        private const val REPORT_GONE_MESSAGE = "El reporte ya fue eliminado."
        private const val CONNECTION_MESSAGE = "Error de conexión, intenta nuevamente."

        /**
         * `profiles(...)` usa la relación PostgREST creada por la FK
         * `reports.user_id -> profiles.id` de la migración de perfiles.
         */
        private const val REPORTS_WITH_PROFILE_SELECT =
            "*,profiles(full_name,avatar_url)"

        /**
         * `reports(*)` se resuelve con la FK
         * `report_flags.report_id -> reports.id`, sin consultas N+1.
         */
        private const val REPORT_FLAGS_WITH_REPORT_SELECT = "*,reports(*)"
    }
}

@Serializable
private data class ReportFlagRow(
    @SerialName("report_id") val reportId: String,
    @SerialName("reporter_id") val reporterId: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("custom_reason_text") val customReasonText: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reports") val reports: ReportDto? = null
)

@Serializable
private data class ReporterProfileRow(
    @SerialName("id") val id: String? = null,
    @SerialName("full_name") val fullName: String? = null
)

@Serializable
private data class UserRoleRow(
    @SerialName("role") val role: String? = null
)