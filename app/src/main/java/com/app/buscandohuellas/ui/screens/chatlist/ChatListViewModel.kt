package com.app.buscandohuellas.ui.screens.chatlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ChatListViewModel"

/** Carga las conversaciones del usuario y mantiene el estado de la lista. */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private var loading = false

    /** [refresh] se usa con pull-to-refresh para no mostrar el loader completo. */
    fun loadConversations(refresh: Boolean = false) {
        if (loading) return
        loading = true

        _uiState.update {
            it.copy(
                isLoading = !refresh && it.conversations.isEmpty(),
                isRefreshing = refresh,
                error = null
            )
        }

        viewModelScope.launch {
            chatRepository.getConversationsForCurrentUser()
                .onSuccess { conversations ->
                    _uiState.update {
                        it.copy(
                            conversations = conversations,
                            isLoading = false,
                            isRefreshing = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudieron cargar las conversaciones", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message
                                ?: "No se pudieron cargar las conversaciones."
                        )
                    }
                }
            loading = false
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}
