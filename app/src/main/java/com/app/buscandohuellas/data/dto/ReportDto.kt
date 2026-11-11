package com.app.buscandohuellas.data.dto

import com.app.buscandohuellas.domain.model.Report
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ReportDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val user_id: String,
    @SerialName("pet_name") val pet_name: String,
    @SerialName("description") val description: String?,
    @SerialName("status") val status: String,
    @SerialName("image_url") val image_url: String?,
    @SerialName("image_urls")
    @Serializable(with = StringListSerializer::class)
    val image_urls: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("location") val location: String? = null
) {
    fun toDomain(): Report {
        val (lat, lon) = parseLocation(location)
        return Report(
            id = id,
            userId = user_id,
            petName = pet_name.ifBlank { "Sin nombre" },
            description = description ?: "",
            status = status,
            imageUrl = image_url,
            imageUrls = image_urls
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() },
            createdAt = createdAt,
            location = location,
            latitude = lat,
            longitude = lon
        )
    }

    private fun parseLocation(location: String?): Pair<Double?, Double?> {
        if (location.isNullOrBlank()) return Pair(null, null)

        return try {
            val clean = location
                .replace("POINT(", "", ignoreCase = true)
                .replace(")", "")
                .trim()

            val parts = clean.split(" ")
            if (parts.size != 2) return Pair(null, null)

            val lon = parts[0].toDoubleOrNull()
            val lat = parts[1].toDoubleOrNull()

            Pair(lat, lon)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }
}
