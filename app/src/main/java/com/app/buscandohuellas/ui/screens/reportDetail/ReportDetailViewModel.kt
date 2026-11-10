package com.app.buscandohuellas.ui.screens.reportDetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


sealed interface ReportDetailUiState {
    data object Loading : ReportDetailUiState
    data class Success(val report: Report) : ReportDetailUiState
    data class Error(val message: String) : ReportDetailUiState
}

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val reportId: String = savedStateHandle.get<String>(REPORT_ID_ARG).orEmpty()

    private val _uiState = MutableStateFlow<ReportDetailUiState>(ReportDetailUiState.Loading)
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    fun loadReport() {
        if (reportId.isBlank()) {
            _uiState.value = ReportDetailUiState.Error("No se pudo identificar el reporte.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ReportDetailUiState.Loading

            reportRepository.getReportById(reportId)
                .onSuccess { report ->
                    _uiState.value = ReportDetailUiState.Success(report)
                }
                .onFailure { error ->
                    Log.e(TAG, "Error al cargar el reporte $reportId", error)
                    _uiState.value = ReportDetailUiState.Error(
                        error.message ?: "No se pudo cargar el reporte. Inténtalo de nuevo."
                    )
                }
        }
    }

    fun retry() = loadReport()

    companion object {
        private const val TAG = "ReportDetailViewModel"

        const val REPORT_ID_ARG = "reportId"
    }
}
