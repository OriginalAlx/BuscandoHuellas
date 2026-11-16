package com.app.buscandohuellas.domain.model

/**
 * Agrupación de las denuncias PENDING de una misma publicación.
 *
 * Una instancia representa una tarjeta del panel de moderación, no una
 * denuncia individual.
 */
data class ReportedPost(
    val report: Report,
    /** Cantidad de denuncias PENDING asociadas al reporte. */
    val reporterCount: Int,
    /** Motivo legible de la denuncia más reciente. */
    val latestReason: String,
    /** Fecha ISO-8601 de la denuncia más reciente. */
    val latestReportedAt: String?,
    /** Nombre del denunciante más reciente, si el perfil es público. */
    val latestReporterName: String?
)
