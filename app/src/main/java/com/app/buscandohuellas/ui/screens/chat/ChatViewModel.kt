package com.app.buscandohuellas.ui.screens.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.ChatConnectionState
import com.app.buscandohuellas.domain.model.ChatConversation
import com.app.buscandohuellas.domain.repository.AuthRepository
import com.app.buscandohuellas.domain.repository.ChatRepository
import com.app.buscandohuellas.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la pantalla de chat.
 *
 * Responsabilidades:
 * - Resolver el usuario autenticado y construir el identificador del hilo.
 * - Observar mensajes y estado de conexión del [ChatRepository].
 * - Validar y enviar mensajes, restaurando el texto si el envío falla.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val ownerId: String =
        savedStateHandle.get<String>(Screen.ARG_OWNER_ID).orEmpty()

    private val reportId: String? =
        savedStateHandle.get<String>(Screen.ARG_REPORT_ID)?.takeIf { it.isNotBlank() }

    private val ownerName: String =
        savedStateHandle.get<String>(Screen.ARG_OWNER_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: ChatUiState.DEFAULT_OWNER_NAME

    private val ownerAvatarUrl: String? =
        savedStateHandle.get<String>(Screen.ARG_OWNER_AVATAR)?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(
        ChatUiState(
            ownerName = ownerName,
            ownerAvatarUrl = ownerAvatarUrl,
            reportId = reportId
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var conversationId: String? = null

    init {
        observeConversation()
    }

    private fun observeConversation() {
        viewModelScope.launch {
            val currentUser = try {
                authRepository.getCurrentUser()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo obtener el usuario actual", error)
                null
            }

            // Sin sesión no se puede identificar al remitente. La pantalla
            // muestra un aviso y mantiene el input deshabilitado.
            if (currentUser == null) {
                _uiState.update {
                    it.copy(
                        isAuthenticated = false,
                        connectionState = ChatConnectionState.DISCONNECTED
                    )
                }
                return@launch
            }

            if (ownerId.isBlank()) {
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        connectionState = ChatConnectionState.DISCONNECTED,
                        errorMessage = "No se pudo identificar al dueño del reporte."
                    )
                }
                return@launch
            }

            val id = ChatConversation.buildId(
                currentUserId = currentUser.id,
                ownerId = ownerId,
                reportId = reportId
            )
            conversationId = id

            _uiState.update {
                it.copy(
                    currentUserId = currentUser.id,
                    isAuthenticated = true
                )
            }

            launch {
                chatRepository.observeMessages(id).collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
            }

            launch {
                chatRepository.observeConnection(id).collect { connection ->
                    _uiState.update { it.copy(connectionState = connection) }
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { current ->
            current.copy(input = value.take(ChatUiState.MAX_MESSAGE_LENGTH))
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        if (!state.canSend) return

        val conversation = conversationId ?: return
        val senderId = state.currentUserId ?: return
        val text = state.input.trim()
        if (text.isEmpty()) return

        // Limpieza optimista: si el envío falla se restaura el texto.
        _uiState.update { it.copy(input = "", isSending = true) }

        viewModelScope.launch {
            chatRepository.sendMessage(
                conversationId = conversation,
                senderId = senderId,
                text = text
            ).onSuccess {
                _uiState.update { it.copy(isSending = false) }
            }.onFailure { error ->
                Log.e(TAG, "No se pudo enviar el mensaje", error)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        input = text,
                        errorMessage = error.message ?: "No se pudo enviar el mensaje."
                    )
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
