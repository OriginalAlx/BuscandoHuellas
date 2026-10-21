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

/**
 * ViewModel de la pantalla principal.
 *
 * Carga el perfil del usuario que ha iniciado sesión (nombre y correo reales de
 * Supabase Auth). Mientras la base de datos no esté cargada, los reportes y las
 * alertas se mantienen vacíos, de modo que la UI muestra su estado vacío.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
    }

    /** Obtiene el usuario de la sesión y vuelca su nombre/correo en el perfil. */
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

    /** Cambia la pestaña activa del BottomNavigation. */
    fun onTabSelected(tab: HomeTab) {
        _uiState.update { current -> current.copy(selectedTab = tab) }
    }

    /** Cambia el filtro de la pantalla de Alertas. */
    fun onAlertsFilterSelected(filter: AlertFilter) {
        _uiState.update { current -> current.copy(alertsFilter = filter) }
    }

    /** Marca todas las alertas como leídas. */
    fun markAlertsAsRead() {
        _uiState.update { current ->
            current.copy(alerts = current.alerts.map { it.copy(isUnread = false) })
        }
    }

    /** Marca una alerta concreta como leída al abrirla. */
    fun onAlertClicked(alertId: String) {
        _uiState.update { current ->
            current.copy(
                alerts = current.alerts.map {
                    if (it.id == alertId) it.copy(isUnread = false) else it
                }
            )
        }
    }

    /**
     * Callback del FAB "Reportar". La navegación vive en la UI, aquí solo
     * queda el punto de enganche para añadir analítica/estado si hace falta.
     */
    fun onCreateReportClicked() = Unit
}
