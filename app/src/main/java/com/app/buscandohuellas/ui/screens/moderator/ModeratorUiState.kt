package com.app.buscandohuellas.ui.screens.moderator

import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.model.ReportedPost

enum class ModeratorTab(val title: String) {
    ALL_REPORTS("Todos los reportes"),
    COMMUNITY_REPORTS("Reportados por comunidad")
}

/**
 * Único enum de filtros del panel. Evita duplicar `ReportFilter`.
 */
enum class ReportFilterOption(
    val label: String,
    val status: ReportStatus?
) {
    ALL("Todos", null),
    LOST("Perdidas", ReportStatus.LOST),
    SIGHTED("Avistadas", ReportStatus.SIGHTED),
    FOUND("Encontradas", ReportStatus.FOUND)
}

data class ModeratorStats(
    val active: Int = 0,
    val reported: Int = 0,
    val resolved: Int = 0
)

data class ModeratorUiState(
    val selectedTab: ModeratorTab = ModeratorTab.ALL_REPORTS,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isActionLoading: Boolean = false,
    val allReports: List<Report> = emptyList(),
    val filteredReports: List<Report> = emptyList(),
    val reportedPosts: List<ReportedPost> = emptyList(),
    val selectedFilter: ReportFilterOption = ReportFilterOption.ALL,
    val stats: ModeratorStats = ModeratorStats(),
    val reportToEdit: Report? = null,
    val reportToDelete: Report? = null,
    val moderatorName: String = "Moderador",
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val isLoggedOut: Boolean = false,
    /**
     * Persisten los fallos de carga para mostrar el estado con "Reintentar".
     * [errorMessage] se limpia al mostrar el snackbar; estos campos no.
     */
    val reportsLoadError: String? = null,
    val reportedPostsLoadError: String? = null
)
