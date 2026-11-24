package com.app.buscandohuellas.ui.screens.chat

import android.net.Uri
import com.app.buscandohuellas.domain.model.Conversation
import com.app.buscandohuellas.domain.model.Message
import com.app.buscandohuellas.domain.model.ReportMatch
import com.app.buscandohuellas.domain.model.ReportStatus

/**
 * Estado único de la pantalla de chat.
 *
 * La lista arranca vacía y se llena desde Supabase. No se inyectan mensajes de
 * bienvenida ni texto por defecto.
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val conversation: Conversation? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentUserId: String = "",
    val currentUserName: String = "",
    val isSending: Boolean = false,
    /** Texto del campo. No forma parte del contrato exigido pero lo usa la UI. */
    val input: String = "",
    /** Nombre del otro participante, mostrado en el TopBar. */
    val otherUserName: String = DEFAULT_OTHER_NAME,
    val otherUserAvatarUrl: String? = null,
    /** Se incrementa al enviar para que la lista haga scroll al final. */
    val scrollEvent: Int = 0,
    /** `true` mientras se sube una imagen a Storage. */
    val isUploadingImage: Boolean = false,
    /** `true` mientras se obtiene/envía la ubicación actual. */
    val isSendingLocation: Boolean = false,
    /** `true` cuando el BottomSheet de adjuntar debe mostrarse. */
    val showAttachmentMenu: Boolean = false,
    /** `true` cuando debe mostrarse el diálogo de opciones de ubicación. */
    val showLocationOptions: Boolean = false,
    /** `true` cuando debe mostrarse el mapa para elegir una ubicación. */
    val showLocationPicker: Boolean = false,
    /** URI de la imagen que se está subiendo (burbuja temporal con spinner). */
    val pendingImageUri: Uri? = null,
    /** Etiqueta legible de la ubicación actual ya resuelta (opcional). */
    val currentLocationLabel: String? = null,
    /** Centro actual del mapa selector de ubicación (solo UI, no se envía). */
    val pickerLatitude: Double? = null,
    val pickerLongitude: Double? = null,
    /** `true` mientras se obtiene la ubicación para centrar el mapa selector. */
    val isLocatingForPicker: Boolean = false,
    /** Dueño real del reporte que identifica el hilo (viene de `reports.user_id`). */
    val reportOwnerId: String = "",
    /** Estado actual de ese reporte, usado para la acción "Ya está en casa". */
    val reportStatus: ReportStatus? = null,
    /** Match del reporte perdido; puede estar ya confirmado/rechazado. */
    val pendingMatch: ReportMatch? = null,
    /** `true` mientras se consulta el match pendiente al entrar al chat. */
    val isLoadingMatch: Boolean = false,
    /** `true` mientras se confirma la mascota encontrada. */
    val isConfirmingMatch: Boolean = false,
    /** `true` mientras se rechaza la mascota encontrada. */
    val isRejectingMatch: Boolean = false,
    /** `true` mientras se cierra el caso desde "Ya está en casa". */
    val isCompletingDelivery: Boolean = false,
    /** Error específico del banner de emparejamiento. */
    val matchError: String? = null
) {

    /** Evita enviar texto vacío o duplicar el envío mientras se está enviando. */
    val canSend: Boolean
        get() = input.isNotBlank() &&
            currentUserId.isNotBlank() &&
            !isSending &&
            !isLoading &&
            !isUploadingImage &&
            !isSendingLocation &&
            !isConfirmingMatch &&
            !isRejectingMatch &&
            !isCompletingDelivery

    /** `true` mientras cualquier operación de envío o match está en curso. */
    val isBusy: Boolean
        get() = isSending || isUploadingImage || isSendingLocation ||
            isConfirmingMatch || isRejectingMatch || isCompletingDelivery

    /** `true` solo para la sesión que creó el reporte perdido. */
    val isReportOwner: Boolean
        get() = currentUserId.isNotBlank() && currentUserId == reportOwnerId

    /**
     * El banner exige las tres condiciones de negocio de forma explícita:
     * dueño del reporte, match existente y aún no confirmado (ni rechazado).
     */
    val showMatchBanner: Boolean
        get() = isReportOwner &&
            pendingMatch != null &&
            pendingMatch.confirmedByOwner == false &&
            pendingMatch.rejectedByOwner == false &&
            !isLoadingMatch &&
            !isLoading

    /** "Ya está en casa" solo aparece para el dueño una vez marcado SIGHTED. */
    val canShowDeliveryButton: Boolean
        get() = isReportOwner &&
            reportStatus == ReportStatus.SIGHTED &&
            !isLoading &&
            !isBusy

    val isEmpty: Boolean
        get() = !isLoading && messages.isEmpty()

    companion object {
        const val DEFAULT_OTHER_NAME = "Dueño del reporte"
        const val MAX_MESSAGE_LENGTH = 1000
        const val MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L
    }
}
