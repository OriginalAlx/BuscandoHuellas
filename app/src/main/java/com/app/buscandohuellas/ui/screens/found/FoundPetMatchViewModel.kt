package com.app.buscandohuellas.ui.screens.found

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.repository.ReportRepository
import com.app.buscandohuellas.util.ReportSimilarity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoundPetMatchViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val foundReportId: String = savedStateHandle.get<String>("reportId").orEmpty()

    private val _uiState = MutableStateFlow(FoundPetMatchUiState())
    val uiState: StateFlow<FoundPetMatchUiState> = _uiState.asStateFlow()

    private var foundReport: Report? = null

    init {
        loadCandidates()
    }

    fun loadCandidates() {
        if (foundReportId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "No se encontró el reporte creado.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val found = reportRepository.getReportById(foundReportId).getOrElse { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "No se pudo cargar el reporte.")
                }
                return@launch
            }
            foundReport = found

            val lat = found.latitude
            val lon = found.longitude
            if (lat == null || lon == null) {
                _uiState.update { it.copy(isLoading = false, candidates = emptyList()) }
                return@launch
            }

            reportRepository.getNearbyLostReports(
                latitude = lat,
                longitude = lon,
                radiusKm = 10.0,
                maxDays = 7
            ).onSuccess { lostReports ->
                val candidates = lostReports
                    .filter { it.id != found.id }
                    .map { lost -> ReportSimilarity.calculate(found, lost) }
                    .sortedByDescending { it.similarityScore }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        candidates = candidates,
                        currentIndex = 0,
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "No se pudieron cargar mascotas cercanas."
                    )
                }
            }
        }
    }

    fun onNotThisOne() {
        _uiState.update { state ->
            val next = (state.currentIndex + 1).coerceAtMost(state.candidates.size)
            state.copy(currentIndex = next)
        }
    }

    fun onConfirmMatch(onOpenChat: (ownerId: String, ownerName: String, ownerAvatarUrl: String?, lostReportId: String) -> Unit) {
        val candidate = _uiState.value.currentCandidate ?: return
        val lostReport = candidate.report
        val lostId = lostReport.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isConfirming = true) }
            reportRepository.createReportMatch(
                foundReportId = foundReportId,
                lostReportId = lostId,
                similarityScore = candidate.similarityScore.toFloat()
            )
            // Abrimos chat aunque falle el registro de match para no bloquear al usuario.
            _uiState.update { it.copy(isConfirming = false, confirmedLostReportId = lostId) }
            onOpenChat(
                lostReport.userId,
                lostReport.userName.ifBlank { "Dueño del reporte" },
                lostReport.userAvatarUrl,
                lostId
            )
        }
    }
}
