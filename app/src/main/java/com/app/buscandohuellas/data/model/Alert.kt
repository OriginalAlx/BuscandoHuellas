package com.app.buscandohuellas.data.model

/**
 * Alerta persistente generada por la Edge Function `generate-alerts` cuando
 * aparece un reporte que coincide con las preferencias del usuario.
 *
 * @param createdAt marca de tiempo en milisegundos (epoch), ya normalizada
 * desde el `timestamptz` de Supabase para poder formatearla en la UI.
 */
data class Alert(
    val id: String,
    val userId: String,
    val reportId: String,
    val title: String,
    val message: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
