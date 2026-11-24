package com.app.buscandohuellas.data.dto

import com.app.buscandohuellas.domain.model.ChatConversation
import com.app.buscandohuellas.domain.model.ChatMessage
import com.app.buscandohuellas.domain.model.ChatReportContext
import com.app.buscandohuellas.domain.model.Comment
import com.app.buscandohuellas.domain.model.MessageType
import com.app.buscandohuellas.domain.model.ReportMatch
import com.app.buscandohuellas.domain.model.ReportStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Serializable
data class CommentDto(
    @SerialName("id") val id: String? = null,
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    fun toDomain(): Comment = Comment(
        id = id.orEmpty(),
        reportId = reportId.orEmpty(),
        userId = userId.orEmpty(),
        userName = userName?.takeIf { it.isNotBlank() } ?: "Usuario",
        content = content.orEmpty(),
        createdAt = parseSocialTimestamp(createdAt)
    )
}

@Serializable
data class ConversationDto(
    @SerialName("id") val id: String? = null,
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("user_one_id") val userOneId: String? = null,
    @SerialName("user_two_id") val userTwoId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    fun toDomain(ownerId: String, ownerName: String): ChatConversation = ChatConversation(
        id = id.orEmpty(),
        ownerId = ownerId,
        ownerName = ownerName,
        reportId = reportId
    )
}

@Serializable
data class MessageDto(
    @SerialName("id") val id: String? = null,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("message_type") val messageType: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("location_label") val locationLabel: String? = null
) {
    /**
     * El nombre viaja en la propia fila (`sender_name`). El parámetro permite
     * sobrescribirlo con el perfil ya cargado cuando se dispone de él.
     */
    fun toDomain(senderName: String? = null, senderAvatarUrl: String? = null): ChatMessage =
        ChatMessage(
            id = id.orEmpty(),
            conversationId = conversationId.orEmpty(),
            senderId = senderId.orEmpty(),
            text = content.orEmpty(),
            sentAt = parseSocialTimestamp(createdAt),
            senderName = senderName?.takeIf { it.isNotBlank() }
                ?: this.senderName?.takeIf { it.isNotBlank() },
            senderAvatarUrl = senderAvatarUrl,
            messageType = parseMessageType(messageType),
            imageUrl = imageUrl?.takeIf { it.isNotBlank() },
            latitude = latitude,
            longitude = longitude,
            locationLabel = locationLabel?.takeIf { it.isNotBlank() }
        )
}

/** Convierte el valor TEXT de Supabase al enum de dominio, con fallback a TEXT. */
internal fun parseMessageType(value: String?): MessageType = when (value?.lowercase()) {
    "image" -> MessageType.IMAGE
    "location" -> MessageType.LOCATION
    else -> MessageType.TEXT
}

/** Fila de `public.report_matches` usada por el banner de confirmación. */
@Serializable
data class ReportMatchDto(
    @SerialName("id") val id: String? = null,
    @SerialName("lost_report_id") val lostReportId: String? = null,
    @SerialName("found_report_id") val foundReportId: String? = null,
    @SerialName("matched_by") val matchedBy: String? = null,
    @SerialName("confirmed_by_owner") val confirmedByOwner: Boolean? = null,
    @SerialName("rejected_by_owner") val rejectedByOwner: Boolean? = null,
    @SerialName("similarity_score") val similarityScore: Double? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    fun toDomain(): ReportMatch = ReportMatch(
        id = id.orEmpty(),
        lostReportId = lostReportId.orEmpty(),
        foundReportId = foundReportId?.takeIf { it.isNotBlank() },
        matchedBy = matchedBy.orEmpty(),
        confirmedByOwner = confirmedByOwner == true,
        rejectedByOwner = rejectedByOwner == true,
        similarityScore = similarityScore?.toFloat(),
        createdAt = createdAt?.let { parseSocialTimestamp(it) }
    )
}

/** Fila mínima de `public.reports` necesaria para gobernar el banner del chat. */
@Serializable
data class ReportContextDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("status") val status: String? = null
) {
    fun toDomain(): ChatReportContext = ChatReportContext(
        reportId = id.orEmpty(),
        ownerId = userId.orEmpty(),
        status = ReportStatus.fromString(status)
    )
}

/**
 * PostgREST devuelve `timestamptz` en distintos formatos según la versión y la
 * columna. Se prueban los más habituales y se cae al momento actual si la
 * cadena no es interpretable, para que un dato inesperado nunca rompa la UI.
 */
internal fun parseSocialTimestamp(value: String?): Long {
    if (value.isNullOrBlank()) return System.currentTimeMillis()

    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching {
            LocalDateTime.parse(value.replace(' ', 'T'))
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }
        .getOrElse { System.currentTimeMillis() }
}
