package com.app.buscandohuellas.data.repository

import com.app.buscandohuellas.data.network.NetworkMonitor
import com.app.buscandohuellas.domain.model.ChatConnectionState
import com.app.buscandohuellas.domain.model.ChatMessage
import com.app.buscandohuellas.domain.model.ChatMessageStatus
import com.app.buscandohuellas.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Implementación en memoria del chat.
 *
 * Todavía no existe backend de mensajería, por lo que las conversaciones viven
 * solo mientras el proceso está activo. La interfaz [ChatRepository] ya expone
 * los flujos necesarios para sustituir esta clase por una implementación con
 * Supabase Realtime, WebSocket o Firebase sin tocar el ViewModel.
 *
 * Mientras tanto, el estado de conexión se deriva de [NetworkMonitor] para que
 * la UI pueda deshabilitar el envío cuando el dispositivo está sin red.
 */
class ChatRepositoryImpl @Inject constructor(
    private val networkMonitor: NetworkMonitor
) : ChatRepository {

    private val messagesByConversation =
        ConcurrentHashMap<String, MutableStateFlow<List<ChatMessage>>>()

    // Serializa los envíos para que dos mensajes simultáneos no se pisen al
    // actualizar la lista en memoria.
    private val sendMutex = Mutex()

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messagesFlow(conversationId).asStateFlow()

    override fun observeConnection(conversationId: String): Flow<ChatConnectionState> =
        networkMonitor.isOnline.map { online ->
            if (online) ChatConnectionState.CONNECTED else ChatConnectionState.DISCONNECTED
        }

    override suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String
    ): Result<ChatMessage> {
        val cleanText = text.trim()

        if (conversationId.isBlank() || senderId.isBlank()) {
            return Result.failure(
                IllegalStateException("No se pudo identificar la conversación.")
            )
        }

        if (cleanText.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("El mensaje no puede estar vacío.")
            )
        }

        if (!networkMonitor.isOnline.first()) {
            return Result.failure(
                IllegalStateException("Sin conexión con el dueño. Inténtalo de nuevo.")
            )
        }

        return sendMutex.withLock {
            val message = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = senderId,
                text = cleanText,
                status = ChatMessageStatus.SENT
            )

            messagesFlow(conversationId).update { messages -> messages + message }
            Result.success(message)
        }
    }

    private fun messagesFlow(conversationId: String): MutableStateFlow<List<ChatMessage>> =
        messagesByConversation.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
}
