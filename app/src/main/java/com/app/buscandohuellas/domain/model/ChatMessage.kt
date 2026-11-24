package com.app.buscandohuellas.domain.model

/**
 * Mensaje de una conversación de chat.
 *
 * El modelo es independiente del transporte: la misma estructura servirá para
 * Supabase Realtime, WebSocket, Firebase o una API REST cuando exista backend.
 * Soporta tres tipos de contenido: texto, imagen y ubicación.
 */
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long = System.currentTimeMillis(),
    val status: ChatMessageStatus = ChatMessageStatus.SENT,
    /** Nombre resuelto desde `profiles`; la UI le añade " (Tú)" si procede. */
    val senderName: String? = null,
    val senderAvatarUrl: String? = null,
    /** Tipo de contenido del mensaje. */
    val messageType: MessageType = MessageType.TEXT,
    /** URL pública de la imagen (solo cuando [messageType] es IMAGE). */
    val imageUrl: String? = null,
    /** Latitud compartida (solo cuando [messageType] es LOCATION). */
    val latitude: Double? = null,
    /** Longitud compartida (solo cuando [messageType] es LOCATION). */
    val longitude: Double? = null,
    /** Etiqueta legible de la ubicación (ej. "Carapeguá, Paraguay"). */
    val locationLabel: String? = null
) {
    /** Texto que se muestra como preview en la lista de conversaciones. */
    val previewText: String
        get() = when (messageType) {
            MessageType.TEXT -> text
            MessageType.IMAGE -> "📷 Foto"
            MessageType.LOCATION -> "📍 ${locationLabel ?: "Ubicación compartida"}"
        }
}

/** Tipos de mensaje soportados por el chat. */
enum class MessageType {
    TEXT, IMAGE, LOCATION
}

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
