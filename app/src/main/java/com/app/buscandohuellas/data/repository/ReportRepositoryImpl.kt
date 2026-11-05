package com.app.buscandohuellas.data.repository

import android.util.Log
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.repository.ReportRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ReportRepository"

@Serializable
data class ReportDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("pet_name") val petName: String,
    @SerialName("description") val description: String,
    @SerialName("status") val status: String = "LOST",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("location_text") val location: String? = null
)

class ReportRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ReportRepository {

    override suspend fun createReport(report: Report): Result<Unit> = try {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(Exception("Usuario no autenticado"))

        val payload = buildJsonObject {
            put("user_id", userId)
            put("pet_name", report.petName)
            put("description", report.description)
            put("status", report.status)
            put("image_url", report.imageUrl ?: "")

            if (report.longitude != null && report.latitude != null) {
                put("location", "POINT(${report.longitude} ${report.latitude})")
            }
        }

        supabaseClient.from("reports").insert(payload)
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

    override suspend fun uploadReportImage(imageBytes: ByteArray, fileExtension: String): Result<String> = try {
        val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.$fileExtension"
        val path = fileName

        Log.d(TAG, "Subiendo imagen a bucket=$BUCKET_NAME path=$path size=${imageBytes.size}")

        supabaseClient.storage.from(BUCKET_NAME).upload(path, imageBytes) {
            upsert = false
            contentType = when (fileExtension.lowercase()) {
                "png" -> ContentType.Image.PNG
                "webp" -> ContentType.Image.Any
                "gif" -> ContentType.Image.GIF
                "bmp" -> ContentType.Image.Any
                else -> ContentType.Image.JPEG
            }
        }

        val publicUrl = supabaseClient.storage.from(BUCKET_NAME).publicUrl(path)
        Log.d(TAG, "Imagen subida exitosamente: $publicUrl")
        Result.success(publicUrl)
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en uploadReportImage: ${error.message}", error)
        Result.failure(Exception("No se pudo subir la imagen. Inténtalo de nuevo."))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en uploadReportImage", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en uploadReportImage: ${error.message}", error)
        Result.failure(Exception(error.message ?: "No se pudo subir la imagen."))
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
        Log.d(TAG, "🔍 PASO 1: Iniciando consulta a reports_view...")

        val response = supabaseClient
            .from("reports_view")
            .select()

        Log.d(TAG, "PASO 2: Respuesta raw de Supabase: ${response.toString().take(500)}")

        val dtos = response.decodeList<ReportDto>()
        Log.d(TAG, "PASO 3: DTOs decodificados: ${dtos.size}")

        dtos.forEachIndexed { index, dto ->
            Log.d(TAG, "PASO 3.$index: id=${dto.id}, petName=${dto.petName}, location=${dto.location}")
        }

        val reports = dtos.mapNotNull { dto ->
            val petName = dto.petName
            val location = dto.location?.let { parsePoint(it) }

            Log.d(TAG, "🔍 PASO 4: Parseando ${dto.petName} -> lat=${location?.second}, lon=${location?.first}")

            Report(
                id = dto.id,
                userId = dto.userId,
                petName = petName,
                description = dto.description,
                status = dto.status,
                imageUrl = dto.imageUrl,
                latitude = location?.second,
                longitude = location?.first
            )
        }

        Log.d(TAG, "🔍 PASO 5: Reportes finales: ${reports.size}")
        Result.success(reports)

    } catch (error: Exception) {
        Log.e(TAG, "ERROR en getAllReports: ${error.message}", error)
        Result.failure(error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en getAllReports", error)
        Result.failure(Exception(friendlyMessage(error)))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en getAllReports", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en getAllReports", error)
        Result.failure(Exception(error.message ?: "No se pudieron cargar los reportes. Inténtalo de nuevo."))
    }

    private fun parsePoint(pointString: String): Pair<Double, Double>? {
        return try {
            Log.d(TAG, "📍 Parseando ubicación: $pointString")

            val clean = pointString
                .substringAfter("POINT(", "")
                .substringBefore(")")
                .trim()
                .replace(",", " ")

            val parts = clean.split(" ").filter { it.isNotBlank() }
            if (parts.size < 2) {
                Log.w(TAG, "Formato de ubicación inválido: $pointString")
                return null
            }

            val lon = parts[0].toDoubleOrNull()
            val lat = parts[1].toDoubleOrNull()

            if (lon != null && lat != null) {
                Log.d(TAG, "Coordenadas parseadas: lat=$lat, lon=$lon")
                lon to lat
            } else {
                Log.w(TAG, "⚠️ No se pudieron convertir a Double: $parts")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al parsear punto: $pointString", e)
            null
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
    }
}