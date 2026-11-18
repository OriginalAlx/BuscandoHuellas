package com.app.buscandohuellas.ui.screens.chat

import com.app.buscandohuellas.domain.model.ChatConnectionState
import com.app.buscandohuellas.domain.model.ChatMessage

/**
 * Estado único de la pantalla de chat.
 *
 * La lista arranca vacía a propósito: no hay mensajes de bienvenida ni texto
 * precargado en el campo de entrada.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val ownerName: String = DEFAULT_OWNER_NAME,
    val ownerAvatarUrl: String? = null,
    val reportId: String? = null,
    val currentUserId: String? = null,
    val isAuthenticated: Boolean = true,
    val isSending: Boolean = false,
    val connectionState: ChatConnectionState = ChatConnectionState.CONNECTING,
    val errorMessage: String? = null
) {

    /** El usuario puede escribir si tiene sesión y el canal está disponible. */
    val canWrite: Boolean
        get() = isAuthenticated &&
            currentUserId != null &&
            connectionState == ChatConnectionState.CONNECTED

    /** Evita enviar mensajes vacíos, duplicados mientras se envía o sin canal. */
    val canSend: Boolean
        get() = canWrite && input.isNotBlank() && !isSending

    val isEmpty: Boolean
        get() = messages.isEmpty()

    companion object {
        const val DEFAULT_OWNER_NAME = "Dueño del reporte"
        const val MAX_MESSAGE_LENGTH = 1000
    }
}
