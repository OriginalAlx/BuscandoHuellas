package com.app.buscandohuellas.ui.utils

/**
 * Controla qué permisos ya fueron solicitados durante el proceso actual.
 *
 * `HomeScreen` y `NotificationSettingsScreen` pueden pedir permisos en
 * momentos distintos. Sin este registro, un cambio de pestaña o un reingreso
 * a la pantalla volvería a mostrar el diálogo nativo después de que el usuario
 * ya decidió. La decisión real la sigue teniendo Android; este objeto solo
 * evita preguntas repetidas e intrusivas.
 */
object RuntimePermissionRequestTracker {

    @Volatile
    var locationRequested: Boolean = false
        private set

    @Volatile
    var notificationsRequested: Boolean = false
        private set

    fun markLocationRequested() {
        locationRequested = true
    }

    fun markNotificationsRequested() {
        notificationsRequested = true
    }

    /**
     * Se llama al cerrar sesión. Sin esto, un logout manual seguido de un login
     * dentro del mismo proceso reutilizaría los flags y no volvería a comprobar
     * permisos aunque el usuario haya desactivado la ubicación mientras tanto.
     */
    fun reset() {
        locationRequested = false
        notificationsRequested = false
    }
}
