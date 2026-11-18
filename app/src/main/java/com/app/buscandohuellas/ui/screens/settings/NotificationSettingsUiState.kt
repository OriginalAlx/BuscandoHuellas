package com.app.buscandohuellas.ui.screens.settings

import com.app.buscandohuellas.data.NotificationRadius

/**
 * Estado de la pantalla de configuración de notificaciones.
 */
data class NotificationSettingsUiState(
    val isLoading: Boolean = true,
    val enabled: Boolean = false,
    val radius: NotificationRadius = NotificationRadius.ALL,
    val notifyLost: Boolean = true,
    val notifySighted: Boolean = true,
    val notifyFound: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
) {
    /** El banner de permisos solo se muestra si falta alguno. */
    val showPermissionBanner: Boolean
        get() = !hasLocationPermission || !hasNotificationPermission

    /** Al menos un tipo de reporte debe estar activo para guardar. */
    val hasAnyTypeEnabled: Boolean
        get() = notifyLost || notifySighted || notifyFound
}
