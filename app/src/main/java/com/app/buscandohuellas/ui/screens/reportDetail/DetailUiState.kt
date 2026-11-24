package com.app.buscandohuellas.ui.screens.reportDetail

import com.app.buscandohuellas.domain.model.Comment
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
        /** Últimos comentarios persistidos, usados como vista previa. */
        val comments: List<Comment> = emptyList()
    ) : DetailUiState

    data class Error(val message: String) : DetailUiState
}
