package com.app.buscandohuellas.domain.model

/**
 * Comentario persistido en Supabase (tabla `comments`).
 *
 * [createdAt] se guarda como epoch milisegundos para que la UI solo se
 * ocupe de formatearlo, igual que ocurre con los mensajes del chat.
 */
data class Comment(
    val id: String,
    val reportId: String,
    val userId: String,
    val userName: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
