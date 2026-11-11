package com.app.buscandohuellas.ui.screens.reportDetail

import com.app.buscandohuellas.domain.model.Report

/**
 * Estados de la pantalla de detalle de un reporte.
 */
sealed interface DetailUiState {

    data object Loading : DetailUiState

    data class Success(
        val report: Report,
        val formattedDistance: String = "Ubicación no disponible",
        val isOwner: Boolean = false,
        val relativeTime: String = "",
        val userName: String = Report.DEFAULT_USER_NAME,
        val userAvatarUrl: String? = null,
        val sightings: List<SightingUi> = emptyList()
    ) : DetailUiState

    data class Error(val message: String) : DetailUiState
}

/**
 * Avistamiento mostrado en la sección de comentarios. Se mantiene en memoria
 * porque el backend todavía no expone una tabla de comentarios.
 */
data class SightingUi(
    val id: Long,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val message: String
)
