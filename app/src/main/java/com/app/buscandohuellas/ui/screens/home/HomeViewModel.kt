package com.app.buscandohuellas.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.repository.AuthRepository
import com.app.buscandohuellas.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _reportsForMap = MutableStateFlow<List<Report>>(emptyList())
    val reportsForMap: StateFlow<List<Report>> = _reportsForMap.asStateFlow()

    private val _isLoadingMap = MutableStateFlow(false)
    val isLoadingMap: StateFlow<Boolean> = _isLoadingMap.asStateFlow()

    private val _mapError = MutableStateFlow<String?>(null)
    val mapError: StateFlow<String?> = _mapError.asStateFlow()

    init {
        loadCurrentUser()
        loadReports()
    }

    fun loadReports() {
        viewModelScope.launch {
            _isLoadingMap.value = true
            _mapError.value = null

            reportRepository.getAllReports()
                .onSuccess { reports ->
                    _reportsForMap.value = reports
                    _isLoadingMap.value = false
                }
                .onFailure { error ->
                    _isLoadingMap.value = false
                    _mapError.value = error.message ?: "No se pudieron cargar los reportes."
                }
        }
    }

    
    private fun loadCurrentUser() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.update { current ->
                current.copy(
                    profile = current.profile.copy(
                        fullName = user?.fullName.orEmpty(),
                        email = user?.email.orEmpty()
                    )
                )
            }
        }
    }

    
    fun onTabSelected(tab: HomeTab) {
        _uiState.update { current -> current.copy(selectedTab = tab) }
    }

    
    fun onAlertsFilterSelected(filter: AlertFilter) {
        _uiState.update { current -> current.copy(alertsFilter = filter) }
    }

    
    fun markAlertsAsRead() {
        _uiState.update { current ->
            current.copy(alerts = current.alerts.map { it.copy(isUnread = false) })
        }
    }

    
    fun onAlertClicked(alertId: String) {
        _uiState.update { current ->
            current.copy(
                alerts = current.alerts.map {
                    if (it.id == alertId) it.copy(isUnread = false) else it
                }
            )
        }
    }

    
    fun onCreateReportClicked() = Unit
}
