package com.app.buscandohuellas.domain.model


data class Report(
    val id: String? = null,
    val userId: String,
    val petName: String,
    val description: String,
    val status: String = "LOST",
    val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
