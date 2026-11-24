package com.app.buscandohuellas.domain.repository

import android.net.Uri
import com.app.buscandohuellas.domain.model.ChatReportContext
import com.app.buscandohuellas.domain.model.Conversation
import com.app.buscandohuellas.domain.model.ConversationWithLastMessage
import com.app.buscandohuellas.domain.model.Message
import com.app.buscandohuellas.domain.model.MessageType
import com.app.buscandohuellas.domain.model.ReportMatch
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del chat persistido en Supabase.
 *
 * La UI solo conoce `StateFlow`; el repositorio traduce PostgREST a modelos de
 * dominio y expone los mensajes como [Flow] para reflejar cambios sin recargar
 * la pantalla.
 */
interface ChatRepository {

    /**
     * Busca la conversación de un reporte entre dos usuarios y, si no existe,
     * la crea. [userOneId] debe ser el usuario autenticado; el repositorio lo
     * valida contra la sesión antes de insertar para no violar RLS.
     */
    suspend fun getOrCreateConversation(
        reportId: String,
        userOneId: String,
        userTwoId: String
    ): Result<Conversation>

    /**
     * Inserta un mensaje indicando explícitamente quién lo envía y su nombre.
     * [messageType] define el tipo de contenido; los campos opcionales se usan
     * según el tipo (imageUrl para IMAGE, coordenadas para LOCATION).
     */
    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        content: String,
        messageType: MessageType = MessageType.TEXT,
        imageUrl: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String? = null
    ): Result<Unit>

    /** Sube una imagen al bucket `chat_images` y devuelve su URL pública. */
    suspend fun uploadChatImage(imageUri: Uri, senderId: String): Result<String>

    /** Inserta un mensaje de tipo IMAGE con la URL ya subida. */
    suspend fun sendImageMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        imageUrl: String
    ): Result<Unit>

    /** Inserta un mensaje de tipo LOCATION con coordenadas y etiqueta legible. */
    suspend fun sendLocationMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String
    ): Result<Unit>

    /** Mensajes de una conversación ordenados por `created_at` ascendente. */
    fun getMessagesByConversationId(conversationId: String): Flow<List<Message>>

    /** Carga una conversación ya existente y resuelve el nombre del otro usuario. */
    suspend fun getConversationById(conversationId: String): Result<Conversation>

    /** Conversaciones del usuario ordenadas por actividad más reciente. */
    suspend fun getConversationsForCurrentUser(): Result<List<ConversationWithLastMessage>>

    /**
     * Devuelve el dueño y el estado del reporte que identifica el hilo. La UI
     * usa el dueño para ocultar el banner al contribuyente y el estado para
     * mostrar la acción "Ya está en casa".
     */
    suspend fun getChatReportContext(reportId: String): Result<ChatReportContext?>

    /**
     * Devuelve el match pendiente del reporte perdido [reportId] **solo si la
     * sesión activa es su dueña**. Si no hay match pendiente o el usuario no es
     * el dueño, devuelve `null` (el banner no debe mostrarse).
     */
    suspend fun getPendingMatchForReport(reportId: String): Result<ReportMatch?>

    /**
     * Confirma que la mascota encontrada es suya: marca el match como confirmado,
     * pasa el reporte de LOST a SIGHTED y escribe el mensaje automático.
     */
    suspend fun confirmPetFound(matchId: String, reportId: String): Result<Unit>

    /**
     * Rechaza el match: lo marca como rechazado por el dueño y escribe el
     * mensaje automático en el chat.
     */
    suspend fun rejectPetFound(matchId: String): Result<Unit>

    /**
     * Cierra el caso cuando la mascota ya fue recogida: SIGHTED → RESOLVED y
     * mensaje automático de agradecimiento.
     */
    suspend fun completePetDelivery(reportId: String): Result<Unit>
}
