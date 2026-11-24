package com.app.buscandohuellas.domain.model

/**
 * Fila de la lista de conversaciones.
 *
 * Reúne en un solo objeto todo lo que la UI necesita pintar: con quién habla el
 * usuario, qué se dijo por última vez y cuándo. Los campos están aplanados a
 * propósito para que `ChatListScreen` no tenga que hacer consultas adicionales.
 */
data class ConversationWithLastMessage(
    val conversationId: String,
    val reportId: String? = null,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserAvatarUrl: String? = null,
    val lastMessage: Message? = null,
    val lastMessageText: String = "",
    val lastMessageAt: Long = 0L,
    /** Reservado para cuando se añada la columna `read_at` en `messages`. */
    val unreadCount: Int = 0
)
