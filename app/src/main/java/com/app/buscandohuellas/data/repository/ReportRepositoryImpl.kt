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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ReportRepository"

@Serializable
data class ReportDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("location") val location: JsonElement? = null
)


class ReportRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ReportRepository {

    override suspend fun createReport(report: Report): Result<Unit> = try {
        
        
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(Exception("Usuario no autenticado"))

        val payload: JsonObject = buildJsonObject {
            put("user_id", userId)
            put("pet_name", report.petName)
            put("description", report.description)
            put("status", report.status)
            put("image_url", report.imageUrl) 

            
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
        Result.failure(
            Exception(error.message ?: "No se pudo publicar el reporte. Inténtalo de nuevo.")
        )
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
        val dtos = supabaseClient
            .from("reports")
            .select()
            .decodeList<ReportDto>()

        val reports = dtos.mapNotNull { dto ->
            val petName = dto.petName ?: return@mapNotNull null
            val location = dto.location?.let { parseLocation(it) }

            Report(
                id = dto.id,
                userId = dto.userId.orEmpty(),
                petName = petName,
                description = dto.description.orEmpty(),
                status = dto.status ?: "LOST",
                imageUrl = dto.imageUrl,
                latitude = location?.first,
                longitude = location?.second
            )
        }

        Result.success(reports)
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
        Result.failure(
            Exception(error.message ?: "No se pudieron cargar los reportes. Inténtalo de nuevo.")
        )
    }

    private fun parseLocation(location: JsonElement): Pair<Double, Double>? = when (location) {
        is JsonPrimitive -> if (location.isString) parsePoint(location.content) else null
        is JsonObject -> {
            val coordinates = (location["coordinates"] as? JsonArray) ?: return null
            val lon = (coordinates.getOrNull(0) as? JsonPrimitive)?.content?.toDoubleOrNull()
            val lat = (coordinates.getOrNull(1) as? JsonPrimitive)?.content?.toDoubleOrNull()
            if (lon != null && lat != null) lon to lat else null
        }
        else -> null
    }

    private fun parsePoint(pointString: String): Pair<Double, Double>? {
        val inside = pointString
            .substringAfter('(', "")
            .substringBefore(')')
            .trim()

        val parts = inside
            .split(' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val lon = parts.getOrNull(0)?.replace(",", ".")?.toDoubleOrNull()
        val lat = parts.getOrNull(1)?.replace(",", ".")?.toDoubleOrNull()

        return if (lon != null && lat != null) lon to lat else null
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
