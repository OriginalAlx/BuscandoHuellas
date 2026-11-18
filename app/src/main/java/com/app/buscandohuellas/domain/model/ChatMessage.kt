package com.app.buscandohuellas.domain.model

/**
 * Mensaje de una conversación de chat.
 *
 * El modelo es independiente del transporte: la misma estructura servirá para
 * Supabase Realtime, WebSocket, Firebase o una API REST cuando exista backend.
 */
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long = System.currentTimeMillis(),
    val status: ChatMessageStatus = ChatMessageStatus.SENT
)

/**
 * Estado de entrega de un mensaje. La UI puede usarlo para mostrar
 * confirmaciones de lectura o reintentos sin cambiar el modelo.
 */
enum class ChatMessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
