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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ReportRepository"

/**
 * Implementación de [ReportRepository] sobre Supabase (PostgREST).
 *
 * NOTA sobre el "map": en vez de un `Map<String, Any?>` se usa un [JsonObject]
 * construido con `buildJsonObject`. Motivo: kotlinx.serialization (que usa
 * supabase-kt por debajo) no tiene serializer para `Any`, por lo que un
 * `Map<String, Any?>` fallaría al serializar. Un [JsonObject] ES un mapa
 * clave/valor JSON y sí es serializable, así que PostgREST lo inserta igual.
 */
class ReportRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ReportRepository {

    override suspend fun createReport(report: Report): Result<Unit> = try {
        // SEGURIDAD: el dueño del reporte es SIEMPRE el usuario de la sesión,
        // nunca un id que venga de la UI. El RLS de Supabase refuerza esto.
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(Exception("Usuario no autenticado"))

        val payload: JsonObject = buildJsonObject {
            put("user_id", userId)
            put("pet_name", report.petName)
            put("description", report.description)
            put("status", report.status)
            put("image_url", report.imageUrl) // String? -> JSON null si es null

            // PostGIS acepta WKT con orden (longitud latitud): "POINT(lon lat)"
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
        // Genera un nombre único para evitar colisiones y problemas de caché
        val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.$fileExtension"
        val path = fileName

        Log.d(TAG, "Subiendo imagen a bucket=$BUCKET_NAME path=$path size=${imageBytes.size}")
        // Sube la imagen al bucket "pet-reports"
        // El bucket debe estar marcado como público en Supabase Dashboard (Storage > pet-reports)
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

        // Obtiene la URL pública de la imagen
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
        // Extrae el path del archivo desde la URL pública
        val path = imageUrl.substringAfterLast("/").substringBefore("?")
        if (path.isNotBlank()) {
            supabaseClient.storage.from(BUCKET_NAME).delete(path)
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Operación best-effort: no bloquear la UI si falla
        Log.w(TAG, "No se pudo eliminar la imagen del storage", error)
        Result.success(Unit)
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
