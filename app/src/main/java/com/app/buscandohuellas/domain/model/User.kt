package com.app.buscandohuellas.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val role: String,
    val fullName: String? = null
)
