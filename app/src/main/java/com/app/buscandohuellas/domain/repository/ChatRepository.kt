package com.app.buscandohuellas.domain.repository

import com.app.buscandohuellas.domain.model.ChatConnectionState
import com.app.buscandohuellas.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del chat.
 *
 * Se apoya en [Flow] para que cualquier backend (WebSocket, Supabase Realtime,
 * Firebase o REST con polling) pueda emitir mensajes y cambios de conexión sin
 * modificar el ViewModel ni la UI.
 */
interface ChatRepository {

    /** Emite la lista completa de mensajes de la conversación. */
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

    /** Emite el estado del canal con el dueño. */
    fun observeConnection(conversationId: String): Flow<ChatConnectionState>

    /**
     * Envía un mensaje. Devuelve el mensaje persistido o un error de dominio
     * listo para mostrarse al usuario.
     */
    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String
    ): Result<ChatMessage>
}
