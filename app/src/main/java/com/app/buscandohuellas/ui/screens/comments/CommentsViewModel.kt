package com.app.buscandohuellas.ui.screens.comments

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.repository.CommentRepository
import com.app.buscandohuellas.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CommentsViewModel"

/**
 * ViewModel de comentarios.
 *
 * Obtiene `reportId` del [SavedStateHandle] que llena NavHost y expone todo su
 * estado como `StateFlow`. La pantalla dispara [loadComments] desde un
 * `LaunchedEffect`.
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val commentRepository: CommentRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val reportId: String =
        savedStateHandle.get<String>(Screen.ARG_REPORT_ID).orEmpty()

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    fun loadComments() {
        if (reportId.isBlank()) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "No se pudo identificar el reporte.")
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            )
        }

        viewModelScope.launch {
            commentRepository.getComments(reportId)
                .onSuccess { comments ->
                    _uiState.update { it.copy(isLoading = false, comments = comments) }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudieron cargar los comentarios", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                                ?: "No se pudieron cargar los comentarios."
                        )
                    }
                }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update {
            it.copy(input = value.take(CommentsUiState.MAX_COMMENT_LENGTH))
        }
    }

    fun addComment() {
        val state = _uiState.value
        val text = state.input.trim()
        if (text.isBlank() || state.isSending) return

        _uiState.update { it.copy(isSending = true, errorMessage = null) }

        viewModelScope.launch {
            commentRepository.addComment(reportId, text)
                .onSuccess { comment ->
                    _uiState.update { current ->
                        current.copy(
                            comments = (current.comments + comment).sortedBy { it.createdAt },
                            input = "",
                            isSending = false
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudo publicar el comentario", error)
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = error.message
                                ?: "No se pudo publicar el comentario."
                        )
                    }
                }
        }
    }

    fun deleteComment(commentId: String) {
        if (_uiState.value.deletingCommentId != null) return

        _uiState.update { it.copy(deletingCommentId = commentId, errorMessage = null) }

        viewModelScope.launch {
            commentRepository.deleteComment(commentId)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            comments = current.comments.filterNot { it.id == commentId },
                            deletingCommentId = null
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudo eliminar el comentario", error)
                    _uiState.update {
                        it.copy(
                            deletingCommentId = null,
                            errorMessage = error.message
                                ?: "No se pudo eliminar el comentario."
                        )
                    }
                }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Añade el sufijo "(Tú)" cuando el comentario pertenece al usuario
     * autenticado. La UI usa este único punto para mantener el criterio.
     */
    fun displayName(name: String, userId: String): String = buildAuthorName(name, userId)

    fun isMine(userId: String): Boolean = userId == _uiState.value.currentUserId

    private fun buildAuthorName(name: String, userId: String): String {
        val cleanName = name.takeIf { it.isNotBlank() } ?: "Usuario"
        return if (userId == _uiState.value.currentUserId) {
            "$cleanName (Tú)"
        } else {
            cleanName
        }
    }
}
