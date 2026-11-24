package com.app.buscandohuellas.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Conversation
import com.app.buscandohuellas.domain.model.Message
import com.app.buscandohuellas.domain.model.MessageType
import com.app.buscandohuellas.domain.model.ReportMatch
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.repository.AuthRepository
import com.app.buscandohuellas.domain.repository.ChatRepository
import com.app.buscandohuellas.navigation.Screen
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

private const val TAG = "ChatViewModel"
private const val DEBUG_TAG = "BH_DEBUG"

/**
 * ViewModel del chat.
 *
 * Acepta dos formas de entrada:
 * - `conversationId` real (al llegar desde la lista de mensajes).
 * - `conversationId = "new"` junto a `ownerId`/`reportId` (al llegar desde el
 *   detalle de un reporte); en ese caso [initializeChat] crea o recupera el hilo.
 *
 * [sendMessage] también crea la conversación si todavía no existe, de modo que
 * el envío nunca se pierde por una navegación concurrente. Lo mismo aplica para
 * imágenes y ubicaciones.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val conversationIdArg: String? =
        savedStateHandle.get<String>(Screen.ARG_CONVERSATION_ID)?.takeIf { it.isNotBlank() }

    private val ownerIdArg: String =
        savedStateHandle.get<String>(Screen.ARG_OWNER_ID).orEmpty()

    private val ownerNameArg: String =
        savedStateHandle.get<String>(Screen.ARG_OWNER_NAME)?.takeIf { it.isNotBlank() }.orEmpty()

    private val ownerAvatarArg: String? =
        savedStateHandle.get<String>(Screen.ARG_OWNER_AVATAR)?.takeIf { it.isNotBlank() }

    private val reportIdArg: String =
        savedStateHandle.get<String>(Screen.ARG_REPORT_ID)?.takeIf { it.isNotBlank() }.orEmpty()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow(
        ChatUiState(
            otherUserName = ownerNameArg.ifBlank { ChatUiState.DEFAULT_OTHER_NAME },
            otherUserAvatarUrl = ownerAvatarArg
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null
    private var initialized = false

    /** Carga o crea la conversación y empieza a observar sus mensajes. */
    fun initializeChat(force: Boolean = false) {
        if (initialized && !force) return
        initialized = true
        messagesJob?.cancel()

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val currentUserId = supabaseClient.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
            if (currentUserId == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Inicia sesión para usar el chat."
                    )
                }
                return@launch
            }

            val currentUserName = try {
                authRepository.getCurrentUser()?.fullName?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_CURRENT_USER_NAME
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo resolver el nombre del usuario", error)
                DEFAULT_CURRENT_USER_NAME
            }

            _uiState.update { it.copy(currentUserId = currentUserId, currentUserName = currentUserName) }

            val explicitId = conversationIdArg?.takeIf { it != Screen.NEW_CONVERSATION_ID }
            val result = when {
                explicitId != null -> chatRepository.getConversationById(explicitId)
                ownerIdArg.isNotBlank() -> chatRepository.getOrCreateConversation(
                    reportId = reportIdArg,
                    userOneId = currentUserId,
                    userTwoId = ownerIdArg
                )

                else -> Result.failure(
                    IllegalStateException("No se pudo identificar la conversación.")
                )
            }

            val conversation = result.getOrNull()
                ?: if (explicitId != null && ownerIdArg.isNotBlank()) {
                    // La conversación pudo borrarse; se intenta recuperar creándola.
                    chatRepository.getOrCreateConversation(
                        reportId = reportIdArg,
                        userOneId = currentUserId,
                        userTwoId = ownerIdArg
                    ).getOrNull()
                } else {
                    null
                }

            if (conversation == null) {
                val error = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error?.message ?: "No se pudo abrir la conversación."
                    )
                }
                return@launch
            }

            applyConversation(conversation)
            observeMessages(conversation.id)
        }
    }

    private fun applyConversation(conversation: Conversation) {
        _uiState.update { current ->
            current.copy(
                conversation = conversation,
                otherUserName = ownerNameArg.ifBlank {
                    conversation.ownerName.takeIf { it.isNotBlank() }
                        ?: ChatUiState.DEFAULT_OTHER_NAME
                },
                otherUserAvatarUrl = current.otherUserAvatarUrl ?: conversation.ownerAvatarUrl,
                isLoading = false,
                error = null
            )
        }
    }

    private fun observeMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesByConversationId(conversationId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudieron cargar los mensajes", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "No se pudieron cargar los mensajes."
                        )
                    }
                    emit(emptyList())
                }
                .collect { messages ->
                    _uiState.update {
                        it.copy(messages = messages, isLoading = false)
                    }
                }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update {
            it.copy(input = value.take(ChatUiState.MAX_MESSAGE_LENGTH))
        }
    }

    /**
     * Valida y envía el texto recibido. Si el hilo aún no existe se crea antes
     * de insertar el mensaje.
     */
    fun sendMessage(content: String) {
        val cleanText = content.trim()
        if (cleanText.isBlank() || _uiState.value.isBusy) return

        viewModelScope.launch {
            val contextResult = prepareSendContext()
            if (contextResult == null) return@launch
            val (target, senderId, senderName) = contextResult

            _uiState.update { it.copy(isSending = true, error = null) }

            chatRepository.sendMessage(
                conversationId = target.id,
                senderId = senderId,
                senderName = senderName,
                content = cleanText
            ).onSuccess {
                _uiState.update { current ->
                    current.copy(
                        input = "",
                        isSending = false,
                        error = null,
                        scrollEvent = current.scrollEvent + 1,
                        messages = current.messages + Message(
                            id = "local-${System.currentTimeMillis()}",
                            conversationId = target.id,
                            senderId = senderId,
                            text = cleanText,
                            senderName = senderName
                        )
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "No se pudo enviar el mensaje", error)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        input = cleanText,
                        error = error.message ?: "No se pudo enviar el mensaje."
                    )
                }
            }
        }
    }

    /**
     * Valida, sube y envía una imagen. Muestra una burbuja temporal con el
     * spinner mientras se completa la subida.
     */
    fun sendImageFromUri(imageUri: Uri) {
        if (_uiState.value.isBusy) return

        // Validación de tipo MIME.
        val mimeType = context.contentResolver.getType(imageUri)
        if (mimeType == null || mimeType !in setOf("image/jpeg", "image/jpg", "image/png", "image/webp")) {
            _uiState.update {
                it.copy(error = "Formato no compatible. Usa una imagen JPEG, PNG o WebP.")
            }
            return
        }

        // Validación de tamaño (5 MB).
        val fileSize = runCatching {
            context.contentResolver.openFileDescriptor(imageUri, "r")?.use { it.statSize }
        }.getOrNull() ?: 0L
        if (fileSize > ChatUiState.MAX_IMAGE_SIZE_BYTES) {
            _uiState.update { it.copy(error = "La imagen es demasiado grande (máx 5MB)") }
            return
        }

        viewModelScope.launch {
            val contextResult = prepareSendContext()
            if (contextResult == null) {
                _uiState.update { it.copy(isUploadingImage = false, pendingImageUri = null) }
                return@launch
            }
            val (target, senderId, senderName) = contextResult

            _uiState.update {
                it.copy(
                    isUploadingImage = true,
                    pendingImageUri = imageUri,
                    error = null
                )
            }

            chatRepository.uploadChatImage(imageUri, senderId)
                .onSuccess { imageUrl ->
                    chatRepository.sendImageMessage(
                        conversationId = target.id,
                        senderId = senderId,
                        senderName = senderName,
                        imageUrl = imageUrl
                    ).onSuccess {
                        _uiState.update { current ->
                            current.copy(
                                isUploadingImage = false,
                                pendingImageUri = null,
                                error = null,
                                scrollEvent = current.scrollEvent + 1,
                                messages = current.messages + Message(
                                    id = "local-${System.currentTimeMillis()}",
                                    conversationId = target.id,
                                    senderId = senderId,
                                    text = "",
                                    senderName = senderName,
                                    messageType = MessageType.IMAGE,
                                    imageUrl = imageUrl
                                )
                            )
                        }
                    }.onFailure { error -> handleSendFailure(error, restoreInput = null) }
                }
                .onFailure { error -> handleSendFailure(error, restoreInput = null) }
        }
    }

    /** Obtiene la ubicación actual, la resuelve a una etiqueta y la envía. */
    fun sendCurrentLocation() {
        if (_uiState.value.isBusy) return
        if (!hasLocationPermission()) {
            _uiState.update {
                it.copy(error = "Se necesita permiso de ubicación para compartir tu posición.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingLocation = true, error = null) }

            val location = try {
                getCurrentLocation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo obtener la ubicación", error)
                _uiState.update {
                    it.copy(isSendingLocation = false, error = "No se pudo obtener tu ubicación. Verifica el GPS.")
                }
                return@launch
            }

            if (location == null || !isValidCoordinate(location.first, location.second)) {
                _uiState.update {
                    it.copy(isSendingLocation = false, error = "No se pudo obtener tu ubicación. Verifica el GPS.")
                }
                return@launch
            }

            val (lat, lon) = location
            val label = reverseGeocode(lat, lon) ?: "Ubicación actual ($lat, $lon)"

            val contextResult = prepareSendContext()
            if (contextResult == null) {
                _uiState.update { it.copy(isSendingLocation = false) }
                return@launch
            }
            val (target, senderId, senderName) = contextResult

            chatRepository.sendLocationMessage(
                conversationId = target.id,
                senderId = senderId,
                senderName = senderName,
                latitude = lat,
                longitude = lon,
                locationLabel = label
            ).onSuccess {
                _uiState.update { current ->
                    current.copy(
                        isSendingLocation = false,
                        error = null,
                        currentLocationLabel = label,
                        scrollEvent = current.scrollEvent + 1,
                        messages = current.messages + Message(
                            id = "local-${System.currentTimeMillis()}",
                            conversationId = target.id,
                            senderId = senderId,
                            text = "",
                            senderName = senderName,
                            messageType = MessageType.LOCATION,
                            latitude = lat,
                            longitude = lon,
                            locationLabel = label
                        )
                    )
                }
            }.onFailure { error -> handleSendFailure(error, restoreInput = null) }
        }
    }

    /** Envía una ubicación elegida en el mapa. */
    fun sendSelectedLocation(latitude: Double, longitude: Double, label: String) {
        if (_uiState.value.isBusy) return
        if (!isValidCoordinate(latitude, longitude)) {
            _uiState.update { it.copy(error = "La ubicación seleccionada no es válida.") }
            return
        }

        viewModelScope.launch {
            val contextResult = prepareSendContext()
            if (contextResult == null) return@launch
            val (target, senderId, senderName) = contextResult

            _uiState.update { it.copy(isSendingLocation = true, error = null) }

            val resolvedLabel = label.takeIf { it.isNotBlank() }
                ?: reverseGeocode(latitude, longitude)
                ?: "Ubicación compartida ($latitude, $longitude)"

            chatRepository.sendLocationMessage(
                conversationId = target.id,
                senderId = senderId,
                senderName = senderName,
                latitude = latitude,
                longitude = longitude,
                locationLabel = resolvedLabel
            ).onSuccess {
                _uiState.update { current ->
                    current.copy(
                        isSendingLocation = false,
                        showLocationPicker = false,
                        showLocationOptions = false,
                        error = null,
                        scrollEvent = current.scrollEvent + 1,
                        messages = current.messages + Message(
                            id = "local-${System.currentTimeMillis()}",
                            conversationId = target.id,
                            senderId = senderId,
                            text = "",
                            senderName = senderName,
                            messageType = MessageType.LOCATION,
                            latitude = latitude,
                            longitude = longitude,
                            locationLabel = resolvedLabel
                        )
                    )
                }
            }.onFailure { error -> handleSendFailure(error, restoreInput = null) }
        }
    }

    // --- Confirmación de la mascota encontrada ------------------------------

    /**
     * Carga el contexto del reporte y su match. El dueño se lee de
     * `reports.user_id`, nunca se asume que `ownerId` de navegación es el dueño:
     * al abrir el chat desde un reporte FOUND ese parámetro apunta al
     * contribuyente y el banner no debe aparecer.
     */
    fun loadPendingMatch() {
        val reportId = resolveReportId()
        val currentUserId = _uiState.value.currentUserId
        Log.d(
            DEBUG_TAG,
            "Chat.loadPendingMatch IN: reportId=$reportId currentUserId=$currentUserId"
        )

        if (reportId.isBlank() || currentUserId.isBlank() || _uiState.value.isLoadingMatch) {
            Log.d(DEBUG_TAG, "Chat.loadPendingMatch SKIP: faltan datos o ya está cargando")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMatch = true, matchError = null) }

            val contextResult = chatRepository.getChatReportContext(reportId)
            val context = contextResult.getOrNull()
            if (context == null) {
                val error = contextResult.exceptionOrNull()
                Log.w(DEBUG_TAG, "Chat.loadPendingMatch: no se pudo cargar el reporte", error)
                _uiState.update {
                    it.copy(
                        reportOwnerId = "",
                        reportStatus = null,
                        pendingMatch = null,
                        isLoadingMatch = false,
                        matchError = error?.message ?: "No se pudo consultar el reporte del chat."
                    )
                }
                return@launch
            }

            val isOwner = context.ownerId == currentUserId
            _uiState.update {
                it.copy(reportOwnerId = context.ownerId, reportStatus = context.status)
            }
            Log.d(
                DEBUG_TAG,
                "Chat.loadPendingMatch CONTEXT: ownerId=${context.ownerId} " +
                    "isOwner=$isOwner status=${context.status.value}"
            )

            if (!isOwner) {
                _uiState.update {
                    it.copy(pendingMatch = null, isLoadingMatch = false, matchError = null)
                }
                Log.d(DEBUG_TAG, "Chat.loadPendingMatch: oculto para el contribuyente")
                return@launch
            }

            chatRepository.getPendingMatchForReport(reportId)
                .onSuccess { match ->
                    _uiState.update {
                        it.copy(
                            // Un match confirmado/rechazado se conserva con sus
                            // flags, pero showMatchBanner lo filtra explícitamente.
                            pendingMatch = match,
                            isLoadingMatch = false,
                            matchError = null
                        )
                    }
                    Log.d(
                        DEBUG_TAG,
                        "Chat.loadPendingMatch MATCH: id=${match?.id} " +
                            "confirmed=${match?.confirmedByOwner} rejected=${match?.rejectedByOwner} " +
                            "showBanner=${_uiState.value.showMatchBanner}"
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "No se pudo cargar el match pendiente", error)
                    _uiState.update {
                        it.copy(
                            pendingMatch = null,
                            isLoadingMatch = false,
                            matchError = error.message
                                ?: "No se pudo consultar el emparejamiento."
                        )
                    }
                }
        }
    }

    /**
     * Confirmación optimista: el banner desaparece en la misma recomposición en
     * que se toca el botón. Si Supabase falla se revierte el estado local y el
     * banner vuelve a hacerse visible junto al error.
     */
    fun confirmPetFound(matchId: String, reportId: String) {
        val state = _uiState.value
        if (matchId.isBlank() || reportId.isBlank()) return
        if (state.isBusy) return

        val previousMatch = state.pendingMatch
        val previousStatus = state.reportStatus

        _uiState.update { current ->
            current.copy(
                pendingMatch = current.pendingMatch?.copy(confirmedByOwner = true),
                reportStatus = ReportStatus.SIGHTED,
                isConfirmingMatch = true,
                matchError = null
            )
        }
        Log.d(
            DEBUG_TAG,
            "Chat.confirmPetFound OPTIMISTIC: confirmed=${_uiState.value.pendingMatch?.confirmedByOwner} " +
                "status=${_uiState.value.reportStatus?.value} showBanner=${_uiState.value.showMatchBanner}"
        )

        viewModelScope.launch {
            chatRepository.confirmPetFound(matchId, reportId)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            pendingMatch = current.pendingMatch?.copy(confirmedByOwner = true),
                            reportStatus = ReportStatus.SIGHTED,
                            isConfirmingMatch = false,
                            matchError = null,
                            scrollEvent = current.scrollEvent + 1,
                            messages = current.messages + systemMessage(
                                current = current,
                                text = ReportMatch.CONFIRM_MESSAGE
                            )
                        )
                    }
                    Log.d(DEBUG_TAG, "Chat.confirmPetFound OK: banner=${_uiState.value.showMatchBanner}")
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudo confirmar la mascota", error)
                    _uiState.update { current ->
                        current.copy(
                            pendingMatch = previousMatch,
                            reportStatus = previousStatus,
                            isConfirmingMatch = false,
                            matchError = error.message ?: "No se pudo confirmar la mascota."
                        )
                    }
                }
        }
    }

    /** Rechazo optimista con reversión si RLS o la red fallan. */
    fun rejectPetFound(matchId: String) {
        val state = _uiState.value
        if (matchId.isBlank() || state.isBusy) return

        val previousMatch = state.pendingMatch
        _uiState.update { current ->
            current.copy(
                pendingMatch = current.pendingMatch?.copy(rejectedByOwner = true),
                isRejectingMatch = true,
                matchError = null
            )
        }

        viewModelScope.launch {
            chatRepository.rejectPetFound(matchId)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            pendingMatch = current.pendingMatch?.copy(rejectedByOwner = true),
                            isRejectingMatch = false,
                            matchError = null,
                            scrollEvent = current.scrollEvent + 1,
                            messages = current.messages + systemMessage(
                                current = current,
                                text = ReportMatch.REJECT_MESSAGE
                            )
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudo rechazar el emparejamiento", error)
                    _uiState.update { current ->
                        current.copy(
                            pendingMatch = previousMatch,
                            isRejectingMatch = false,
                            matchError = error.message ?: "No se pudo rechazar el emparejamiento."
                        )
                    }
                }
        }
    }

    /** Cierra el caso cuando la mascota ya está físicamente con su dueño. */
    fun markPetHome() {
        val state = _uiState.value
        if (!state.isReportOwner || state.reportStatus != ReportStatus.SIGHTED || state.isBusy) return
        val reportId = resolveReportId()
        if (reportId.isBlank()) return

        val previousStatus = state.reportStatus
        _uiState.update {
            it.copy(
                reportStatus = ReportStatus.RESOLVED,
                isCompletingDelivery = true,
                matchError = null
            )
        }
        Log.d(DEBUG_TAG, "Chat.markPetHome OPTIMISTIC: status=${_uiState.value.reportStatus?.value}")

        viewModelScope.launch {
            chatRepository.completePetDelivery(reportId)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            reportStatus = ReportStatus.RESOLVED,
                            isCompletingDelivery = false,
                            matchError = null,
                            scrollEvent = current.scrollEvent + 1,
                            messages = current.messages + systemMessage(
                                current = current,
                                text = ReportMatch.HOME_MESSAGE
                            )
                        )
                    }
                    Log.d(DEBUG_TAG, "Chat.markPetHome OK")
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudo cerrar el caso", error)
                    _uiState.update { current ->
                        current.copy(
                            reportStatus = previousStatus,
                            isCompletingDelivery = false,
                            matchError = error.message ?: "No se pudo cerrar el caso."
                        )
                    }
                }
        }
    }

    private fun resolveReportId(): String =
        reportIdArg.ifBlank { _uiState.value.conversation?.reportId.orEmpty() }

    private fun systemMessage(current: ChatUiState, text: String): Message = Message(
        id = "local-match-${System.currentTimeMillis()}",
        conversationId = current.conversation?.id.orEmpty(),
        senderId = current.currentUserId,
        text = text,
        senderName = "Sistema"
    )

    fun consumeMatchError() {
        _uiState.update { it.copy(matchError = null) }
    }

    // --- Menús / diálogos ----------------------------------------------------

    fun toggleAttachmentMenu() {
        _uiState.update { it.copy(showAttachmentMenu = !it.showAttachmentMenu) }
    }

    fun dismissAttachmentMenu() {
        _uiState.update { it.copy(showAttachmentMenu = false) }
    }

    fun toggleLocationOptions() {
        _uiState.update { it.copy(showLocationOptions = !it.showLocationOptions) }
    }

    fun dismissLocationOptions() {
        _uiState.update { it.copy(showLocationOptions = false) }
    }

    fun showLocationPicker() {
        _uiState.update {
            it.copy(showLocationOptions = false, showLocationPicker = true)
        }
    }

    fun dismissLocationPicker() {
        _uiState.update {
            it.copy(
                showLocationPicker = false,
                pickerLatitude = null,
                pickerLongitude = null,
                isLocatingForPicker = false
            )
        }
    }

    /**
     * Obtiene la ubicación actual solo para centrar el mapa selector.
     * MapPickerScreen invoca este callback automáticamente al abrirse, por lo
     * que NO debe enviar ningún mensaje.
     */
    fun updatePickerLocation() {
        if (!hasLocationPermission() || _uiState.value.isLocatingForPicker) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLocatingForPicker = true) }
            val location = try {
                getCurrentLocation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo centrar el mapa en la ubicación actual", error)
                null
            }
            if (location != null && isValidCoordinate(location.first, location.second)) {
                _uiState.update {
                    it.copy(
                        pickerLatitude = location.first,
                        pickerLongitude = location.second,
                        isLocatingForPicker = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLocatingForPicker = false) }
            }
        }
    }

    fun retry() {
        initializeChat(force = true)
        loadPendingMatch()
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Nombre con el sufijo "(Tú)" cuando el remitente es el usuario actual. */
    fun displayName(name: String, senderId: String): String {
        val cleanName = name.takeIf { it.isNotBlank() } ?: "Usuario"
        return if (isMine(senderId)) "$cleanName (Tú)" else cleanName
    }

    fun isMine(senderId: String): Boolean =
        senderId.isNotBlank() && senderId == _uiState.value.currentUserId

    // --- Helpers -------------------------------------------------------------

    /** Resuelve el hilo y los datos del remitente, creando la conversación si hace falta. */
    private suspend fun prepareSendContext(): Triple<Conversation, String, String>? {
        val senderId = supabaseClient.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
            ?: _uiState.value.currentUserId.takeIf { it.isNotBlank() }
            ?: run {
                _uiState.update {
                    it.copy(isSending = false, isUploadingImage = false, isSendingLocation = false,
                        pendingImageUri = null, error = "Inicia sesión para enviar mensajes.")
                }
                return null
            }
        val senderName = _uiState.value.currentUserName.takeIf { it.isNotBlank() }
            ?: DEFAULT_CURRENT_USER_NAME

        var conversation = _uiState.value.conversation
        if (conversation == null) {
            if (ownerIdArg.isBlank()) {
                _uiState.update {
                    it.copy(isSending = false, isUploadingImage = false, isSendingLocation = false,
                        pendingImageUri = null, error = "No se pudo identificar la conversación.")
                }
                return null
            }

            _uiState.update { it.copy(isSending = true, error = null) }
            val created = chatRepository.getOrCreateConversation(
                reportId = reportIdArg,
                userOneId = senderId,
                userTwoId = ownerIdArg
            )

            conversation = created.getOrNull()
            if (conversation == null) {
                _uiState.update {
                    it.copy(isSending = false, isUploadingImage = false, isSendingLocation = false,
                        pendingImageUri = null,
                        error = created.exceptionOrNull()?.message ?: "No se pudo abrir la conversación.")
                }
                return null
            }
            applyConversation(conversation)
            observeMessages(conversation.id)
        }

        return Triple(conversation, senderId, senderName)
    }

    private fun handleSendFailure(error: Throwable, restoreInput: String?) {
        if (error is CancellationException) throw error
        Log.e(TAG, "No se pudo enviar el contenido", error)
        _uiState.update {
            it.copy(
                isSending = false,
                isUploadingImage = false,
                isSendingLocation = false,
                pendingImageUri = null,
                input = restoreInput ?: it.input,
                error = error.message ?: "No se pudo enviar el mensaje."
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocation(): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location ->
                if (cont.isActive) {
                    cont.resume(
                        if (location != null) location.latitude to location.longitude else null
                    )
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Fallo al obtener ubicación", error)
                if (cont.isActive) cont.resume(null)
            }
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull() ?: return@withContext null
                listOfNotNull(
                    address.locality,
                    address.subAdminArea,
                    address.adminArea,
                    address.countryName
                ).distinct().joinToString(", ").takeIf { it.isNotBlank() }
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo resolver la dirección", error)
                null
            }
        }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude != 0.0 && longitude != 0.0 &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    private companion object {
        const val DEFAULT_CURRENT_USER_NAME = "Tú"
    }
}
