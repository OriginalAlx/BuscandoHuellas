package com.app.buscandohuellas.data.dto

import com.app.buscandohuellas.domain.model.Report
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Perfil público del usuario que publicó un reporte. Se obtiene con la
 * relación PostgREST `profiles(full_name, avatar_url)`.
 */
@Serializable
data class ProfileDto(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class ReportDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("pet_name") val petName: String,
    @SerialName("description") val description: String? = null,
    @SerialName("status") val status: String = "LOST",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_urls")
    @Serializable(with = StringListSerializer::class)
    val imageUrls: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    /** Texto WKT que expone `reports_view` (por ejemplo, `POINT(lon lat)`). */
    @SerialName("location_text") val locationText: String? = null,
    /** Columna geometry cruda de la tabla `reports` (EWKB en hexadecimal). */
    @SerialName("location") val rawLocation: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_avatar_url") val userAvatarUrl: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("reference") val reference: String? = null,
    @SerialName("profiles") val profile: ProfileDto? = null
) {
    fun toDomain(): Report {
        val readableLocation = locationText?.takeIf { it.isNotBlank() }
        val point = parsePoint(readableLocation ?: rawLocation)
        val profileName = profile?.fullName?.takeIf { it.isNotBlank() }
        val profileAvatar = profile?.avatarUrl?.takeIf { it.isNotBlank() }

        return Report(
            id = id,
            userId = userId,
            petName = petName.ifBlank { "Sin nombre" },
            description = description ?: "",
            status = status,
            imageUrl = imageUrl,
            imageUrls = imageUrls
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() },
            createdAt = createdAt,
            // Solo guardamos texto legible; el EWKB crudo nunca debe mostrarse.
            location = readableLocation,
            latitude = point?.second,
            longitude = point?.first,
            userName = profileName
                ?: userName?.takeIf { it.isNotBlank() }
                ?: Report.DEFAULT_USER_NAME,
            userAvatarUrl = profileAvatar ?: userAvatarUrl?.takeIf { it.isNotBlank() },
            locationName = locationName?.takeIf { it.isNotBlank() },
            reference = reference?.takeIf { it.isNotBlank() }
        )
    }
}

/**
 * Acepta tanto WKT (`POINT(lon lat)`) como EWKB hexadecimal, que es lo que
 * devuelve PostgREST para columnas `geometry` de PostGIS.
 *
 * @return par `longitud to latitud`, o `null` si no se puede interpretar.
 */
internal fun parsePoint(value: String?): Pair<Double, Double>? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()
    return parseWktPoint(trimmed) ?: parseEwkbPoint(trimmed)
}

private fun parseWktPoint(value: String): Pair<Double, Double>? {
    if (!value.startsWith("POINT", ignoreCase = true)) return null

    val clean = value
        .substringAfter("(", "")
        .substringBefore(")")
        .trim()
        .replace(",", " ")

    val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.size < 2) return null

    val longitude = parts[0].toDoubleOrNull() ?: return null
    val latitude = parts[1].toDoubleOrNull() ?: return null
    return longitude to latitude
}

private fun parseEwkbPoint(value: String): Pair<Double, Double>? {
    val hex = value.removePrefix("\\x").trim()
    if (hex.length < MIN_EWKB_HEX_LENGTH || hex.length % 2 != 0 || !hex.matches(HEX_REGEX)) {
        return null
    }

    return try {
        val bytes = ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val endianness = bytes[0].toInt()
        if (endianness != 0 && endianness != 1) return null

        val buffer = ByteBuffer.wrap(bytes).order(
            if (endianness == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        )

        buffer.position(1)
        val type = buffer.int
        val hasSrid = (type and EWKB_SRID_FLAG) != 0
        val geometryType = type and EWKB_GEOMETRY_TYPE_MASK
        if (geometryType != EWKB_POINT_TYPE) return null

        if (hasSrid) buffer.int
        if (buffer.remaining() < DOUBLE_BYTES * 2) return null

        val longitude = buffer.double
        val latitude = buffer.double
        longitude to latitude
    } catch (error: Exception) {
        null
    }
}

private const val EWKB_POINT_TYPE = 1
private const val EWKB_SRID_FLAG = 0x20000000
private const val EWKB_GEOMETRY_TYPE_MASK = 0x1FFFFFFF
private const val DOUBLE_BYTES = 8
private const val MIN_EWKB_HEX_LENGTH = 42

private val HEX_REGEX = Regex("^[0-9a-fA-F]+$")
