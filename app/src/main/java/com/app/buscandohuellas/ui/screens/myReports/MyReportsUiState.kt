package com.app.buscandohuellas.ui.screens.myReports

import com.app.buscandohuellas.domain.model.Report

/**
 * Estado de la pantalla "Mis reportes".
 *
 * [error] solo se usa para fallos de carga (se muestra en pantalla completa).
 * [message] se usa para resultados puntuales de acciones, como eliminar un
 * reporte, y se muestra en un snackbar sin ocultar la lista.
 */
data class MyReportsUiState(
    val reports: List<Report> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val deletingReportId: String? = null,
    val message: String? = null
) {

    /** true cuando la carga terminó sin error y no hay reportes que mostrar. */
    val isEmpty: Boolean
        get() = !isLoading && error == null && reports.isEmpty()
}
