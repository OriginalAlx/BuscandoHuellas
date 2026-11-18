package com.app.buscandohuellas.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Perfil público del usuario almacenado en la tabla `profiles` de Supabase.
 *
 * `id` coincide con el identificador de `auth.users`, de modo que cada cuenta
 * de autenticación tiene exactamente un perfil.
 */
@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)
