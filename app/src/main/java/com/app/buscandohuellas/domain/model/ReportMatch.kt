package com.app.buscandohuellas.domain.model

/**
 * Coincidencia persistida entre un reporte FOUND y un reporte LOST.
 *
 * Se usa en el chat para que el dueño del reporte perdido pueda confirmar o
 * rechazar que la mascota encontrada es la suya.
 */
data class ReportMatch(
    val id: String,
    val lostReportId: String,
    val foundReportId: String?,
    val matchedBy: String,
    val confirmedByOwner: Boolean = false,
    val rejectedByOwner: Boolean = false,
    val similarityScore: Float? = null,
    val createdAt: Long? = null
) {
    /** `true` mientras el dueño todavía no ha tomado una decisión. */
    val isPending: Boolean
        get() = !confirmedByOwner && !rejectedByOwner

    companion object {
        const val CONFIRM_MESSAGE =
            "✅ El dueño ha confirmado que esta es su mascota. Coordinando entrega."
        const val REJECT_MESSAGE =
            "❌ El dueño indica que esta no es su mascota."
        const val HOME_MESSAGE =
            "🎉 ¡La mascota ya está en casa! Gracias a todos por su ayuda."
    }
}
