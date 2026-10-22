package com.app.buscandohuellas.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
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
