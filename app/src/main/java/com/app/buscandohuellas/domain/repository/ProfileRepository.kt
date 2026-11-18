package com.app.buscandohuellas.domain.repository

import android.net.Uri
import com.app.buscandohuellas.domain.model.Profile

/**
 * Acceso a los datos del perfil del usuario.
 *
 * Las operaciones devuelven [Result] para que la capa de presentación pueda
 * distinguir entre éxito y error sin manejar excepciones de red o de Supabase.
 */
interface ProfileRepository {

    /** Obtiene el perfil asociado a [userId]. */
    suspend fun getProfile(userId: String): Result<Profile>

    /** Actualiza únicamente el nombre del perfil [userId]. */
    suspend fun updateProfile(userId: String, fullName: String): Result<Unit>

    /**
     * Sube [imageUri] a `avatars/{userId}.jpg`, actualiza `avatar_url` en el
     * perfil y devuelve la URL pública de la imagen.
     */
    suspend fun uploadAvatar(userId: String, imageUri: Uri): Result<String>
}
