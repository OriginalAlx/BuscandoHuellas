package com.app.buscandohuellas.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.app.buscandohuellas.domain.model.Profile
import com.app.buscandohuellas.domain.repository.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "ProfileRepository"

/**
 * Implementación de [ProfileRepository] sobre Postgrest y Storage.
 *
 * Los mensajes de error se traducen a español para que la pantalla de edición
 * pueda mostrarlos directamente al usuario.
 */
class ProfileRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) : ProfileRepository {

    override suspend fun getProfile(userId: String): Result<Profile> {
        if (userId.isBlank()) {
            return Result.failure(Exception("No se pudo identificar al usuario autenticado."))
        }

        return try {
            val profile = supabaseClient
                .from(PROFILES_TABLE)
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Profile>()

            if (profile == null) {
                Result.failure(Exception("No se encontró el perfil del usuario."))
            } else {
                Result.success(profile)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpRequestException) {
            Log.e(TAG, "HttpRequestException en getProfile", error)
            Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
        } catch (error: Exception) {
            Log.e(TAG, "Error inesperado en getProfile", error)
            Result.failure(Exception(error.message ?: "No se pudo cargar el perfil."))
        }
    }

    override suspend fun updateProfile(userId: String, fullName: String): Result<Unit> {
        val cleanName = fullName.trim()

        if (userId.isBlank()) {
            return Result.failure(Exception("No se pudo identificar al usuario autenticado."))
        }
        if (cleanName.isEmpty()) {
            return Result.failure(Exception("El nombre no puede estar vacío."))
        }

        return try {
            val updatedRows = supabaseClient
                .from(PROFILES_TABLE)
                .update(
                    body = buildJsonObject {
                        put("full_name", cleanName)
                    },
                    request = {
                        select(Columns.raw("id"))
                        filter { eq("id", userId) }
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
            Log.e(TAG, "HttpRequestException en updateProfile", error)
            Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
        } catch (error: Exception) {
            Log.e(TAG, "Error inesperado en updateProfile", error)
            Result.failure(Exception(error.message ?: "No se pudo actualizar el perfil."))
        }
    }

    override suspend fun uploadAvatar(userId: String, imageUri: Uri): Result<String> {
        if (userId.isBlank()) {
            return Result.failure(Exception("No se pudo identificar al usuario autenticado."))
        }

        return try {
            val bytes = readImageBytes(imageUri)
            val fileName = "$userId.jpg"

            Log.d(TAG, "Subiendo avatar a bucket=$AVATARS_BUCKET path=$fileName size=${bytes.size}")

            supabaseClient.storage.from(AVATARS_BUCKET).upload(fileName, bytes) {
                // La ruta es fija por usuario, así que cada cambio reemplaza la
                // foto anterior en lugar de acumular archivos huérfanos.
                upsert = true
                contentType = ContentType.Image.JPEG
            }

            // El parámetro de versión evita que Coil sirva la imagen anterior
            // desde su caché cuando la URL pública no cambia.
            val publicUrl = supabaseClient.storage
                .from(AVATARS_BUCKET)
                .publicUrl(fileName)
            val versionedUrl = "$publicUrl?v=${System.currentTimeMillis()}"

            updateAvatarUrl(userId, versionedUrl)
                .onFailure { error ->
                    Log.e(TAG, "El avatar se subió pero no se pudo guardar en el perfil", error)
                    return Result.failure(
                        Exception(
                            error.message
                                ?: "La foto se subió, pero no se pudo guardar en tu perfil."
                        )
                    )
                }

            Log.d(TAG, "Avatar subido y guardado exitosamente: $versionedUrl")
            Result.success(versionedUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpRequestException) {
            Log.e(TAG, "HttpRequestException en uploadAvatar", error)
            Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
        } catch (error: Exception) {
            Log.e(TAG, "Error inesperado en uploadAvatar", error)
            Result.failure(Exception(error.message ?: "No se pudo subir la foto de perfil."))
        }
    }

    private suspend fun updateAvatarUrl(userId: String, avatarUrl: String): Result<Unit> = try {
        val updatedRows = supabaseClient
            .from(PROFILES_TABLE)
            .update(
                update = { set("avatar_url", avatarUrl) },
                request = {
                    select(Columns.raw("id"))
                    filter { eq("id", userId) }
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
        Log.e(TAG, "HttpRequestException en updateAvatarUrl", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en updateAvatarUrl", error)
        Result.failure(Exception(error.message ?: "No se pudo guardar la foto de perfil."))
    }

    private fun readImageBytes(uri: Uri): ByteArray {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo leer la imagen seleccionada.")

        if (bytes.size > MAX_IMAGE_SIZE_BYTES) {
            throw IllegalStateException("La imagen supera el tamaño máximo de 5MB.")
        }
        return bytes
    }

    private companion object {
        const val PROFILES_TABLE = "profiles"
        const val AVATARS_BUCKET = "avatars"
        const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
        const val NO_PERMISSION_MESSAGE =
            "No tienes permiso para actualizar este perfil o el perfil ya no existe."
    }
}
