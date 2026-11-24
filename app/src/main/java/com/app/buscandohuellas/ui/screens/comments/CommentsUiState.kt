package com.app.buscandohuellas.ui.screens.comments

import com.app.buscandohuellas.domain.model.Comment

/** Estado de la pantalla de comentarios de un reporte. */
data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    val input: String = "",
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val deletingCommentId: String? = null,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && comments.isEmpty()

    val canSend: Boolean
        get() = input.isNotBlank() && !isSending

    companion object {
        const val MAX_COMMENT_LENGTH = 1000
    }
}
