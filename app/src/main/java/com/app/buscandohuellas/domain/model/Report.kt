package com.app.buscandohuellas.domain.model


data class Report(
    val id: String? = null,
    val userId: String,
    val petName: String,
    val description: String,
    val status: ReportStatus = ReportStatus.LOST,
    val imageUrl: String? = null,
    val imageUrls: List<String>? = null,
    val createdAt: String? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val userName: String = DEFAULT_USER_NAME,
    val userAvatarUrl: String? = null,
    val locationName: String? = null,
    val reference: String? = null
) {

    val allImageUrls: List<String>
        get() = (listOfNotNull(imageUrl) + imageUrls.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    companion object {
        const val DEFAULT_USER_NAME = "Usuario de la comunidad"
    }
}
