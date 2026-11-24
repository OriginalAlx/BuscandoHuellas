package com.app.buscandohuellas.ui.screens.found

import com.app.buscandohuellas.util.ReportMatchCandidate

data class FoundPetMatchUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val candidates: List<ReportMatchCandidate> = emptyList(),
    val currentIndex: Int = 0,
    val isConfirming: Boolean = false,
    val confirmedLostReportId: String? = null
) {
    val currentCandidate: ReportMatchCandidate?
        get() = candidates.getOrNull(currentIndex)

    val hasMore: Boolean
        get() = currentIndex < candidates.lastIndex

    val isEmpty: Boolean
        get() = !isLoading && error == null && candidates.isEmpty()
}
