package com.app.buscandohuellas.ui.screens.chatlist

import com.app.buscandohuellas.domain.model.ConversationWithLastMessage

/** Estado de la lista de conversaciones del usuario autenticado. */
data class ChatListUiState(
    val conversations: List<ConversationWithLastMessage> = emptyList(),
    val isLoading: Boolean = false,
    /** Solo para el indicador de pull-to-refresh. */
    val isRefreshing: Boolean = false,
    val error: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && conversations.isEmpty()
}
