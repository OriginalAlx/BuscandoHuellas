package com.app.buscandohuellas.domain.model

/**
 * Datos del reporte que identifica un hilo de chat.
 *
 * El chat necesita saber quién es el dueño real del reporte para no mostrar el
 * banner de confirmación al contribuyente, y cuál es su estado para saber cuándo
 * ofrecer la acción "Ya está en casa".
 */
data class ChatReportContext(
    val reportId: String,
    val ownerId: String,
    val status: ReportStatus
)
