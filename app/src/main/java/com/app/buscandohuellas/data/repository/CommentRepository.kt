package com.app.buscandohuellas.data.repository

import android.util.Log
import com.app.buscandohuellas.data.dto.CommentDto
import com.app.buscandohuellas.domain.model.Comment
import com.app.buscandohuellas.domain.repository.AuthRepository
import com.app.buscandohuellas.domain.repository.CommentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CommentRepository"
private const val COMMENTS_TABLE = "comments"

/**
 * Repositorio de comentarios sobre PostgREST.
 *
 * Las consultas recuperan todas las columnas (`*`) y el orden se aplica en
 * memoria a partir de `created_at`, de modo que el comportamiento no depende de
 * la configuración de ordenación del servidor.
 */
@Singleton
class CommentRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) : CommentRepository {

    override suspend fun getComments(reportId: String): Result<List<Comment>> {
        if (reportId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar el reporte."))
        }

        return try {
            val comments = supabaseClient
                .from(COMMENTS_TABLE)
                .select(Columns.raw("*")) {
                    filter { eq("report_id", reportId) }
                }
                .decodeList<CommentDto>()
                .map { it.toDomain() }
                .sortedBy { it.createdAt }

            Result.success(comments)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudieron cargar los comentarios.")
            )
        }
    }

    override suspend fun addComment(reportId: String, content: String): Result<Comment> {
        val cleanContent = content.trim()
        if (reportId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar el reporte."))
        }
        if (cleanContent.isBlank()) {
            return Result.failure(IllegalArgumentException("El comentario no puede estar vacío."))
        }

        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            val authorName = authRepository.getCurrentUser()?.fullName
                ?.takeIf { it.isNotBlank() }
                ?: "Usuario"

            val payloadWithName = buildPayload(
                reportId = reportId,
                userId = currentUserId,
                content = cleanContent,
                authorName = authorName
            )
            val payload = buildPayload(
                reportId = reportId,
                userId = currentUserId,
                content = cleanContent,
                authorName = null
            )

            // `user_name` puede ser una columna generada por un trigger. Si
            // Supabase rechaza el valor, se reintenta sin esa columna.
            val inserted = try {
                supabaseClient
                    .from(COMMENTS_TABLE)
                    .insert(payloadWithName) { select(Columns.raw("*")) }
                    .decodeSingle<CommentDto>()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!isGeneratedColumnError(error)) throw error
                Log.d(TAG, "user_name es generado; se inserta el comentario sin esa columna")
                supabaseClient
                    .from(COMMENTS_TABLE)
                    .insert(payload) { select(Columns.raw("*")) }
                    .decodeSingle<CommentDto>()
            }

            val comment = inserted.toDomain().copy(
                reportId = inserted.reportId ?: reportId,
                userId = inserted.userId ?: currentUserId,
                userName = inserted.userName?.takeIf { it.isNotBlank() } ?: authorName
            )
            Result.success(comment)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo publicar el comentario.")
            )
        }
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> {
        if (commentId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar el comentario."))
        }

        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            // El filtro por usuario es una segunda barrera además de RLS.
            val deleted = supabaseClient
                .from(COMMENTS_TABLE)
                .delete(
                    request = {
                        select(Columns.raw("id"))
                        filter {
                            eq("id", commentId)
                            eq("user_id", currentUserId)
                        }
                    }
                )
                .decodeList<JsonObject>()

            if (deleted.isEmpty()) {
                Result.failure(Exception("No tienes permiso para eliminar este comentario."))
            } else {
                Result.success(Unit)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo eliminar el comentario.")
            )
        }
    }

    private fun buildPayload(
        reportId: String,
        userId: String,
        content: String,
        authorName: String?
    ) = buildJsonObject {
        put("report_id", reportId)
        put("user_id", userId)
        put("content", content)
        if (!authorName.isNullOrBlank()) {
            put("user_name", authorName)
        }
    }
}
