package com.app.buscandohuellas.domain.model


data class Report(
    val id: String? = null,
    val userId: String,
    val petName: String,
    val description: String,
    val status: String = "LOST",
    val imageUrl: String? = null,
    val imageUrls: List<String>? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {

    val allImageUrls: List<String>
        get() = (listOfNotNull(imageUrl) + imageUrls.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}
