package com.app.buscandohuellas.data.dto

import com.app.buscandohuellas.data.model.Alert
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fila de la tabla `public.alerts`. */
@Serializable
data class AlertDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
) {
    fun toDomain(): Alert = Alert(
        id = id.orEmpty(),
        userId = userId.orEmpty(),
        reportId = reportId.orEmpty(),
        title = title?.takeIf { it.isNotBlank() } ?: "Nuevo reporte cerca de ti",
        message = message.orEmpty(),
        isRead = isRead,
        createdAt = parseSocialTimestamp(createdAt)
    )
}
