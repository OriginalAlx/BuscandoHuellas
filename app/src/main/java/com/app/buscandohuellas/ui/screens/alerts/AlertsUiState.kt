package com.app.buscandohuellas.ui.screens.alerts

import com.app.buscandohuellas.data.model.Alert

/** Filtros disponibles en la pantalla de alertas. */
enum class AlertFilter(val label: String) {
    ALL("Todas"),
    UNREAD("Sin leer")
}

/**
 * Estado de la pantalla de Alertas.
 *
 * [unreadCount] y [visibleAlerts] se derivan de [alerts] para que nunca queden
 * desincronizados tras marcar o eliminar una alerta.
 */
data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val error: String? = null,
    val filter: AlertFilter = AlertFilter.ALL
) {

    val visibleAlerts: List<Alert>
        get() = if (filter == AlertFilter.UNREAD) alerts.filter { !it.isRead } else alerts

    val unreadCount: Int
        get() = alerts.count { !it.isRead }
}
