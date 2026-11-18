package com.app.buscandohuellas.domain.model

/**
 * Identifica la conversación entre el usuario actual y el dueño de un reporte.
 *
 * [id] combina ambos participantes y el reporte para que cada publicación tenga
 * su propio hilo. Así el mismo dueño puede tener conversaciones separadas por
 * mascota sin mezclar mensajes.
 */
data class ChatConversation(
    val id: String,
    val ownerId: String,
    val ownerName: String,
    val ownerAvatarUrl: String? = null,
    val reportId: String? = null
) {
    companion object {
        private const val SEPARATOR = ":"

        fun buildId(currentUserId: String, ownerId: String, reportId: String?): String =
            listOf(currentUserId, ownerId, reportId.orEmpty()).joinToString(SEPARATOR)
    }
}
