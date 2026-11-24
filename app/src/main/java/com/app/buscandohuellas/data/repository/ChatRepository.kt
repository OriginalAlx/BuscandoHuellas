package com.app.buscandohuellas.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.app.buscandohuellas.data.dto.ConversationDto
import com.app.buscandohuellas.data.dto.MessageDto
import com.app.buscandohuellas.data.dto.ReportContextDto
import com.app.buscandohuellas.data.dto.ReportMatchDto
import com.app.buscandohuellas.data.dto.parseSocialTimestamp
import com.app.buscandohuellas.data.network.NetworkMonitor
import com.app.buscandohuellas.domain.model.ChatReportContext
import com.app.buscandohuellas.domain.model.Conversation
import com.app.buscandohuellas.domain.model.ConversationWithLastMessage
import com.app.buscandohuellas.domain.model.Message
import com.app.buscandohuellas.domain.model.MessageType
import com.app.buscandohuellas.domain.model.Profile
import com.app.buscandohuellas.domain.model.ReportMatch
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.repository.AuthRepository
import com.app.buscandohuellas.domain.repository.ChatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatRepository"
private const val CONVERSATIONS_TABLE = "conversations"
private const val MESSAGES_TABLE = "messages"
private const val PROFILES_TABLE = "profiles"
private const val REPORTS_TABLE = "reports"
private const val REPORT_MATCHES_TABLE = "report_matches"
private const val CHAT_IMAGES_BUCKET = "chat_images"
private const val DEFAULT_SENDER_NAME = "Usuario"
private const val SYSTEM_SENDER_NAME = "Sistema"
private const val MAX_CHAT_IMAGE_BYTES = 5L * 1024L * 1024L

/**
 * Chat persistido en Supabase PostgREST.
 *
 * La búsqueda de conversaciones comprueba ambos sentidos de la pareja porque
 * RLS permite que cualquiera de los dos participantes cree el hilo. El nombre
 * del remitente se guarda en `messages.sender_name`: si esa columna existe en
 * el esquema (y suele ser NOT NULL) un INSERT sin ella es justamente el error
 * "No se pudo enviar el mensaje.".
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context
) : ChatRepository {

    /** Emite el id de un hilo cuando cambia, para recargar el [Flow] de mensajes. */
    private val messageChanges = MutableSharedFlow<String>(extraBufferCapacity = 16)

    override suspend fun getOrCreateConversation(
        reportId: String,
        userOneId: String,
        userTwoId: String
    ): Result<Conversation> {
        if (reportId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar el reporte."))
        }
        if (userTwoId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar al otro usuario."))
        }

        val currentUserId = currentUserIdOrNull()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            ensureOnline()

            // `user_one_id` debe ser la sesión activa: la política RLS de INSERT
            // suele exigirlo. Si el llamador envía otro valor, se corrige aquí.
            val one = userOneId.takeIf { it == currentUserId } ?: currentUserId
            val two = userTwoId.takeIf { it != currentUserId }
                ?: return Result.failure(IllegalArgumentException("No puedes chatear contigo mismo."))

            findConversation(currentUserId, two, reportId)?.let { existing ->
                return Result.success(existing.toDomainFromDto(currentUserId, null, emptyMap()))
            }

            val payload = buildJsonObject {
                put("report_id", reportId)
                put("user_one_id", one)
                put("user_two_id", two)
            }

            val created = try {
                supabaseClient
                    .from(CONVERSATIONS_TABLE)
                    .insert(payload) { select(Columns.raw("*")) }
                    .decodeSingle<ConversationDto>()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!isDuplicateKeyError(error)) throw error

                // Otro dispositivo creó el hilo mientras tanto.
                findConversation(currentUserId, two, reportId) ?: throw error
            }

            Result.success(created.toDomainFromDto(currentUserId, null, emptyMap()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo abrir la conversación.")
            )
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        content: String,
        messageType: MessageType,
        imageUrl: String?,
        latitude: Double?,
        longitude: Double?,
        locationLabel: String?
    ): Result<Unit> {
        val cleanContent = content.trim()
        if (conversationId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar la conversación."))
        }
        if (messageType == MessageType.TEXT && cleanContent.isBlank()) {
            return Result.failure(IllegalArgumentException("El mensaje no puede estar vacío."))
        }
        if (messageType == MessageType.IMAGE && imageUrl.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Falta la URL de la imagen."))
        }
        if (messageType == MessageType.LOCATION && (latitude == null || longitude == null)) {
            return Result.failure(IllegalArgumentException("Faltan las coordenadas de la ubicación."))
        }

        val currentUserId = currentUserIdOrNull()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            ensureOnline()

            // El remitente siempre es la sesión activa; un parámetro distinto
            // no puede suplantar a otro usuario.
            val resolvedName = senderName.trim().ifBlank { null }
                ?: authRepository.getCurrentUser()?.fullName?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SENDER_NAME

            val typeValue = when (messageType) {
                MessageType.TEXT -> "text"
                MessageType.IMAGE -> "image"
                MessageType.LOCATION -> "location"
            }

            // Se prueban variantes del payload para no bloquear el chat aunque
            // el esquema esté a medio migrar: columnas multimedia ausentes o
            // `sender_name` generado por la base de datos.
            val payloads = buildList {
                add(
                    buildMessagePayload(
                        conversationId, currentUserId, resolvedName, cleanContent, typeValue,
                        imageUrl, latitude, longitude, locationLabel
                    )
                )
                add(
                    buildMessagePayload(
                        conversationId, currentUserId, null, cleanContent, typeValue,
                        imageUrl, latitude, longitude, locationLabel
                    )
                )
                if (messageType == MessageType.TEXT) {
                    // Sin las columnas nuevas de chat_media.sql: el texto sigue
                    // pudiendo enviarse con el esquema anterior.
                    add(
                        buildMessagePayload(
                            conversationId, currentUserId, resolvedName, cleanContent, typeValue,
                            null, null, null, null, includeMediaColumns = false
                        )
                    )
                    add(
                        buildMessagePayload(
                            conversationId, currentUserId, null, cleanContent, typeValue,
                            null, null, null, null, includeMediaColumns = false
                        )
                    )
                }
            }

            insertMessagePayloads(payloads)
            messageChanges.tryEmit(conversationId)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo enviar el mensaje.")
            )
        }
    }

    private fun buildMessagePayload(
        conversationId: String,
        senderId: String,
        senderName: String?,
        content: String,
        typeValue: String,
        imageUrl: String?,
        latitude: Double?,
        longitude: Double?,
        locationLabel: String?,
        includeMediaColumns: Boolean = true
    ): JsonObject = buildJsonObject {
        put("conversation_id", conversationId)
        put("sender_id", senderId)
        if (senderName != null) put("sender_name", senderName)
        put("content", content)
        if (includeMediaColumns) {
            put("message_type", typeValue)
            if (imageUrl != null) put("image_url", imageUrl)
            if (latitude != null) put("latitude", latitude)
            if (longitude != null) put("longitude", longitude)
            if (locationLabel != null) put("location_label", locationLabel)
        }
    }

    /** Inserta el primer payload que el esquema actual acepte. */
    private suspend fun insertMessagePayloads(payloads: List<JsonObject>): MessageDto {
        var lastError: Throwable? = null

        payloads.forEachIndexed { index, payload ->
            try {
                return supabaseClient
                    .from(MESSAGES_TABLE)
                    .insert(payload) { select(Columns.raw("*")) }
                    .decodeSingle<MessageDto>()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                lastError = error
                when {
                    isGeneratedColumnError(error) -> Log.d(
                        TAG,
                        "sender_name es generado; se reintenta sin esa columna (payload $index)"
                    )

                    isMissingSchemaColumnError(error) -> Log.w(
                        TAG,
                        "El esquema no reconoce alguna columna; se reintenta con un payload compatible (payload $index)",
                        error
                    )

                    else -> throw error
                }
            }
        }

        throw lastError ?: IllegalStateException("No se pudo insertar el mensaje.")
    }

    override suspend fun uploadChatImage(imageUri: Uri, senderId: String): Result<String> = try {
        val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo leer la imagen seleccionada.")
        if (bytes.isEmpty()) {
            throw IllegalStateException("La imagen seleccionada está vacía.")
        }
        if (bytes.size > MAX_CHAT_IMAGE_BYTES) {
            throw IllegalStateException("La imagen es demasiado grande (máx 5MB)")
        }

        val extension = resolveChatImageExtension(imageUri)
        // Convención de ruta exigida por las políticas: chat_images/{sender_id}/{timestamp}.jpg
        val fileName = "$senderId/${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"

        supabaseClient.storage.from(CHAT_IMAGES_BUCKET).upload(fileName, bytes) {
            upsert = false
            contentType = when (extension) {
                "png" -> ContentType.Image.PNG
                "webp" -> ContentType.Image.Any
                else -> ContentType.Image.JPEG
            }
        }

        val publicUrl = supabaseClient.storage.from(CHAT_IMAGES_BUCKET).publicUrl(fileName)
        Result.success(publicUrl)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "No se pudo subir la imagen del chat", error)
        Result.failure(
            SocialErrorMapper.map(error, TAG, "No se pudo subir la imagen. Inténtalo de nuevo.")
        )
    }

    private fun resolveChatImageExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> {
                val path = uri.lastPathSegment.orEmpty()
                if (path.contains(".")) {
                    path.substringAfterLast(".").lowercase().ifBlank { "jpg" }
                } else {
                    "jpg"
                }
            }
        }
    }

    override suspend fun sendImageMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        imageUrl: String
    ): Result<Unit> = sendMessage(
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        content = "",
        messageType = MessageType.IMAGE,
        imageUrl = imageUrl
    )

    override suspend fun sendLocationMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String
    ): Result<Unit> = sendMessage(
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        content = "",
        messageType = MessageType.LOCATION,
        latitude = latitude,
        longitude = longitude,
        locationLabel = locationLabel
    )

    // --- Confirmación de mascotas encontradas --------------------------------

    override suspend fun getChatReportContext(reportId: String): Result<ChatReportContext?> {
        if (reportId.isBlank()) return Result.success(null)

        return try {
            ensureOnline()

            val context = supabaseClient
                .from(REPORTS_TABLE)
                .select(Columns.raw("id, user_id, status")) {
                    filter { eq("id", reportId) }
                    limit(1)
                }
                .decodeList<ReportContextDto>()
                .firstOrNull()
                ?.toDomain()

            Result.success(context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo consultar el reporte del chat.")
            )
        }
    }

    override suspend fun getPendingMatchForReport(reportId: String): Result<ReportMatch?> {
        if (reportId.isBlank()) return Result.success(null)

        val currentUserId = currentUserIdOrNull()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            ensureOnline()

            // El banner solo existe para el dueño del reporte perdido.
            val isOwner = supabaseClient
                .from(REPORTS_TABLE)
                .select(Columns.raw("id")) {
                    filter {
                        eq("id", reportId)
                        eq("user_id", currentUserId)
                    }
                    limit(1)
                }
                .decodeList<JsonObject>()
                .isNotEmpty()

            if (!isOwner) return Result.success(null)

            val pending = selectPendingMatch(reportId)
            Result.success(pending?.toDomain()?.takeIf { it.isPending })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo consultar el emparejamiento.")
            )
        }
    }

    /**
     * Consulta el match pendiente. Si la migración que añade `rejected_by_owner`
     * todavía no se aplicó, reintenta sin ese filtro y descarta en memoria los
     * rechazados, para no romper el chat en instalaciones a medio migrar.
     */
    private suspend fun selectPendingMatch(reportId: String): ReportMatchDto? {
        val selectMatching: suspend (Boolean) -> ReportMatchDto? = { includeRejected ->
            supabaseClient
                .from(REPORT_MATCHES_TABLE)
                .select(Columns.raw("*")) {
                    filter {
                        eq("lost_report_id", reportId)
                        eq("confirmed_by_owner", false)
                        if (includeRejected) eq("rejected_by_owner", false)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<ReportMatchDto>()
                .firstOrNull()
        }

        return try {
            selectMatching(true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "No se pudo filtrar por rejected_by_owner; se reintenta sin esa columna", error)
            selectMatching(false)?.takeIf { it.rejectedByOwner != true }
        }
    }

    override suspend fun confirmPetFound(matchId: String, reportId: String): Result<Unit> {
        if (matchId.isBlank() || reportId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar el emparejamiento."))
        }

        val currentUserId = currentUserIdOrNull()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            ensureOnline()

            // Primero el reporte: si RLS no permite el UPDATE, no se confirma el
            // match y el banner permanece visible para que el dueño pueda reintentar.
            val updatedReports = supabaseClient
                .from(REPORTS_TABLE)
                .update(
                    update = { set("status", ReportStatus.SIGHTED.value) },
                    request = {
                        select(Columns.raw("id"))
                        filter { eq("id", reportId) }
                    }
                )
                .decodeList<JsonObject>()

            if (updatedReports.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No tienes permiso para actualizar el estado del reporte.")
                )
            }

            val updatedMatches = supabaseClient
                .from(REPORT_MATCHES_TABLE)
                .update(
                    update = { set("confirmed_by_owner", true) },
                    request = {
                        select(Columns.raw("id"))
                        filter { eq("id", matchId) }
                    }
                )
                .decodeList<JsonObject>()

            if (updatedMatches.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No tienes permiso para confirmar este emparejamiento.")
                )
            }

            insertSystemMessage(reportId, ReportMatch.CONFIRM_MESSAGE, currentUserId)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo confirmar la mascota.")
            )
        }
    }

    override suspend fun rejectPetFound(matchId: String): Result<Unit> {
        if (matchId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar el emparejamiento."))
        }

        val currentUserId = currentUserIdOrNull()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            ensureOnline()

            val match = supabaseClient
                .from(REPORT_MATCHES_TABLE)
                .select(Columns.raw("lost_report_id")) {
                    filter { eq("id", matchId) }
                    limit(1)
                }
                .decodeList<ReportMatchDto>()
                .firstOrNull()

            val updatedMatches = supabaseClient
                .from(REPORT_MATCHES_TABLE)
                .update(
                    update = { set("rejected_by_owner", true) },
                    request = {
                        select(Columns.raw("id"))
                        filter { eq("id", matchId) }
                    }
                )
                .decodeList<JsonObject>()

            if (updatedMatches.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No tienes permiso para rechazar este emparejamiento.")
                )
            }

            match?.lostReportId?.let { lostReportId ->
                insertSystemMessage(lostReportId, ReportMatch.REJECT_MESSAGE, currentUserId)
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo rechazar el emparejamiento.")
            )
        }
    }

    override suspend fun completePetDelivery(reportId: String): Result<Unit> {
        if (reportId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar el reporte."))
        }

        val currentUserId = currentUserIdOrNull()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            ensureOnline()

            val updatedReports = supabaseClient
                .from(REPORTS_TABLE)
                .update(
                    update = { set("status", ReportStatus.RESOLVED.value) },
                    request = {
                        select(Columns.raw("id"))
                        filter {
                            eq("id", reportId)
                            eq("user_id", currentUserId)
                        }
                    }
                )
                .decodeList<JsonObject>()

            if (updatedReports.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No tienes permiso para cerrar este reporte.")
                )
            }

            insertSystemMessage(reportId, ReportMatch.HOME_MESSAGE, currentUserId)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo cerrar el caso.")
            )
        }
    }

    /**
     * Escribe el mensaje automático con `sender_id` igual a la sesión activa:
     * `messages.sender_id` referencia `auth.users` y RLS exige
     * `auth.uid() = sender_id`, así que no se puede suplantar al otro usuario.
     */
    private suspend fun insertSystemMessage(
        reportId: String,
        content: String,
        senderId: String
    ) {
        val conversationId = supabaseClient
            .from(CONVERSATIONS_TABLE)
            .select(Columns.raw("id")) {
                filter { eq("report_id", reportId) }
                limit(1)
            }
            .decodeList<ConversationDto>()
            .firstOrNull()
            ?.id

        if (conversationId.isNullOrBlank()) {
            Log.w(TAG, "No hay conversación para $reportId; no se inserta el mensaje automático")
            return
        }

        sendMessage(
            conversationId = conversationId,
            senderId = senderId,
            senderName = SYSTEM_SENDER_NAME,
            content = content
        ).onFailure { error ->
            Log.w(TAG, "No se pudo insertar el mensaje automático del chat", error)
        }
    }

    override fun getMessagesByConversationId(conversationId: String): Flow<List<Message>> =
        messageChanges
            .filter { it == conversationId }
            .onStart { emit(conversationId) }
            .map { fetchMessages(conversationId) }
            .flowOn(Dispatchers.IO)

    override suspend fun getConversationById(conversationId: String): Result<Conversation> {
        if (conversationId.isBlank()) {
            return Result.failure(IllegalArgumentException("No se pudo identificar la conversación."))
        }

        val currentUserId = currentUserIdOrNull()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            val dto = supabaseClient
                .from(CONVERSATIONS_TABLE)
                .select(Columns.raw("*")) {
                    filter { eq("id", conversationId) }
                    limit(1)
                }
                .decodeList<ConversationDto>()
                .firstOrNull()
                ?: throw IllegalStateException("La conversación ya no existe.")

            Result.success(dto.toDomainFromDto(currentUserId, null, emptyMap()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudo abrir la conversación.")
            )
        }
    }

    override suspend fun getConversationsForCurrentUser(): Result<List<ConversationWithLastMessage>> {
        val currentUserId = currentUserIdOrNull()
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))

        return try {
            val asOne = selectConversations("user_one_id", currentUserId)
            val asTwo = selectConversations("user_two_id", currentUserId)
            val conversations = (asOne + asTwo).distinctBy { it.id }
            if (conversations.isEmpty()) return Result.success(emptyList())

            val otherIds = conversations.mapNotNull { dto ->
                listOf(dto.userOneId, dto.userTwoId).firstOrNull { it != currentUserId }
            }.toSet()

            val lastMessages = conversations.associate { dto ->
                dto.id.orEmpty() to fetchLastMessage(dto.id.orEmpty())
            }

            val profileIds = otherIds + lastMessages.values.mapNotNull { it?.senderId }
            val profiles = loadProfiles(profileIds.toList())

            val result = conversations.mapNotNull { dto ->
                val id = dto.id ?: return@mapNotNull null
                val otherId = listOf(dto.userOneId, dto.userTwoId)
                    .firstOrNull { it != currentUserId }
                    ?: return@mapNotNull null
                val otherProfile = profiles[otherId]
                val last = lastMessages[id]
                val senderProfile = last?.senderId?.let { profiles[it] }
                val message = last?.toDomain(
                    senderName = senderProfile?.fullName,
                    senderAvatarUrl = senderProfile?.avatarUrl
                )

                ConversationWithLastMessage(
                    conversationId = id,
                    reportId = dto.reportId,
                    otherUserId = otherId,
                    otherUserName = otherProfile?.fullName?.takeIf { it.isNotBlank() }
                        ?: last?.senderName?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_SENDER_NAME,
                    otherUserAvatarUrl = otherProfile?.avatarUrl,
                    lastMessage = message,
                    lastMessageText = message?.previewText.orEmpty(),
                    lastMessageAt = message?.sentAt
                        ?: parseSocialTimestamp(dto.createdAt)
                )
            }.sortedByDescending { it.lastMessageAt }

            Result.success(result)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                SocialErrorMapper.map(error, TAG, "No se pudieron cargar las conversaciones.")
            )
        }
    }

    private suspend fun fetchMessages(conversationId: String): List<Message> {
        val dtos = supabaseClient
            .from(MESSAGES_TABLE)
            .select(Columns.raw("*")) {
                filter { eq("conversation_id", conversationId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<MessageDto>()

        val profiles = loadProfiles(dtos.mapNotNull { it.senderId }.toSet())
        return dtos.sortedBy { parseSocialTimestamp(it.createdAt) }.map { dto ->
            val profile = dto.senderId?.let { profiles[it] }
            dto.toDomain(
                senderName = profile?.fullName,
                senderAvatarUrl = profile?.avatarUrl
            )
        }
    }

    private suspend fun fetchLastMessage(conversationId: String): MessageDto? =
        supabaseClient
            .from(MESSAGES_TABLE)
            .select(Columns.raw("*")) {
                filter { eq("conversation_id", conversationId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<MessageDto>()
            .firstOrNull()

    private suspend fun selectConversations(column: String, value: String): List<ConversationDto> =
        supabaseClient
            .from(CONVERSATIONS_TABLE)
            .select(Columns.raw("*")) {
                filter { eq(column, value) }
            }
            .decodeList<ConversationDto>()

    private suspend fun findConversation(
        currentUserId: String,
        otherUserId: String,
        reportId: String
    ): ConversationDto? = selectConversation(currentUserId, otherUserId, reportId)
        ?: selectConversation(otherUserId, currentUserId, reportId)

    private suspend fun selectConversation(
        userOneId: String,
        userTwoId: String,
        reportId: String
    ): ConversationDto? = supabaseClient
        .from(CONVERSATIONS_TABLE)
        .select(Columns.raw("*")) {
            filter {
                eq("report_id", reportId)
                eq("user_one_id", userOneId)
                eq("user_two_id", userTwoId)
            }
            limit(1)
        }
        .decodeList<ConversationDto>()
        .firstOrNull()

    /**
     * Resuelve al otro participante a partir de la sesión. El nombre viaja en
     * los argumentos de navegación cuando se abre desde un reporte; si no está,
     * se consulta `profiles`.
     */
    private suspend fun ConversationDto.toDomainFromDto(
        currentUserId: String,
        fallbackName: String?,
        loadedProfiles: Map<String, Profile>
    ): Conversation {
        val otherId = listOf(userOneId, userTwoId)
            .firstOrNull { it != null && it != currentUserId }
            .orEmpty()
        val cachedProfile = loadedProfiles[otherId]
        val profile = if (cachedProfile?.fullName.isNullOrBlank() && otherId.isNotBlank()) {
            loadProfiles(setOf(otherId))[otherId]
        } else {
            cachedProfile
        }
        val name = fallbackName?.takeIf { it.isNotBlank() }
            ?: profile?.fullName?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SENDER_NAME

        return Conversation(
            id = id.orEmpty(),
            ownerId = otherId,
            ownerName = name,
            ownerAvatarUrl = profile?.avatarUrl,
            reportId = reportId
        )
    }

    private suspend fun loadProfiles(userIds: Collection<String>): Map<String, Profile> {
        val ids = userIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyMap()

        return try {
            supabaseClient
                .from(PROFILES_TABLE)
                .select(Columns.raw("id, full_name, avatar_url")) {
                    filter { isIn("id", ids) }
                }
                .decodeList<Profile>()
                .associateBy { it.id }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "No se pudieron resolver los nombres de los participantes", error)
            emptyMap()
        }
    }

    private fun currentUserIdOrNull(): String? =
        supabaseClient.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }

    private suspend fun ensureOnline() {
        if (!networkMonitor.isOnline.first()) {
            throw IllegalStateException(SocialErrorMapper.NO_CONNECTION)
        }
    }
}
