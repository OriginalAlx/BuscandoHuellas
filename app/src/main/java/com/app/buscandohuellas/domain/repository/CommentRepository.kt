package com.app.buscandohuellas.domain.repository

import com.app.buscandohuellas.domain.model.Comment

/**
 * Persistencia de los comentarios de un reporte.
 *
 * La implementación usa la tabla `comments` de Supabase; la interfaz mantiene
 * desacoplada la UI del transporte y facilita sustituir el backend en el futuro.
 */
interface CommentRepository {

    /** Devuelve los comentarios del reporte ordenados por fecha ascendente. */
    suspend fun getComments(reportId: String): Result<List<Comment>>

    /** Inserta un comentario del usuario autenticado. */
    suspend fun addComment(reportId: String, content: String): Result<Comment>

    /** Elimina un comentario propio. RLS impide borrar los ajenos. */
    suspend fun deleteComment(commentId: String): Result<Unit>
}
