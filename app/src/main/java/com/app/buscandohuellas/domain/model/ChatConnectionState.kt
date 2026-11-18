package com.app.buscandohuellas.domain.model

/**
 * Estado del canal con el dueño del reporte.
 *
 * La UI lo usa para deshabilitar el envío y mostrar un aviso cuando todavía se
 * está conectando o cuando no hay conexión disponible.
 */
enum class ChatConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}
