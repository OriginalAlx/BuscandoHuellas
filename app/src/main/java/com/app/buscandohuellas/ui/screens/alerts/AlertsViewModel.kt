package com.app.buscandohuellas.ui.screens.alerts

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.repository.AlertRepository
import com.app.buscandohuellas.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AlertsViewModel"

/**
 * ViewModel de la pantalla de Alertas.
 *
 * El `userId` se toma de [SavedStateHandle] si la ruta lo aporta; en caso
 * contrario se resuelve desde la sesión activa de Supabase. La lista se
 * mantiene en vivo con el Flow de [AlertRepository], que combina PostgREST y
 * Realtime.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val userIdArg: String =
        savedStateHandle.get<String>(Screen.ARG_USER_ID).orEmpty()

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    /** Emite el reporte al que se debe navegar tras tocar una alerta. */
    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    private var alertsJob: Job? = null
    private var resolvedUserId: String? = null

    /** Evita repetir la limpieza en cada recomposición o reingreso. */
    private var cleanedUpUserId: String? = null

    /** Carga (o recarga) las alertas y empieza a escuchar cambios. */
    fun loadAlerts() {
        alertsJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }

        alertsJob = viewModelScope.launch {
            val userId = resolveUserId()
            if (userId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Inicia sesión para ver tus alertas."
                    )
                }
                return@launch
            }
            resolvedUserId = userId

            alertRepository.getAlertsByUserId(userId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "No se pudieron cargar las alertas", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "No se pudieron cargar las alertas."
                        )
                    }
                }
                .collect { alerts ->
                    _uiState.update {
                        it.copy(alerts = alerts, isLoading = false, error = null)
                    }
                }
        }
    }

    /**
     * Limpieza de alertas con más de 7 días, una sola vez por sesión y usuario.
     * Se dispara al entrar a la pantalla; un fallo no interrumpe la carga.
     */
    fun cleanOldAlertsIfNeeded() {
        val userId = resolveUserId() ?: return
        if (cleanedUpUserId == userId) return
        cleanedUpUserId = userId

        viewModelScope.launch {
            alertRepository.deleteOldAlerts(userId, daysToKeep = 7)
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "No se pudo completar la limpieza de alertas", error)
                }
        }
    }

    fun setFilter(filter: AlertFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    /**
     * Marca la alerta como leída en Supabase y solo entonces emite la
     * navegación al reporte, para que el cambio no se pierda al salir de la
     * pantalla. La UI se actualiza de forma optimista y se revierte si falla.
     */
    fun markAsRead(alertId: String, reportId: String? = null) {
        if (alertId.isBlank()) return

        val previousAlerts = _uiState.value.alerts
        _uiState.update { state ->
            state.copy(
                isUpdating = true,
                alerts = state.alerts.map { alert ->
                    if (alert.id == alertId) alert.copy(isRead = true) else alert
                }
            )
        }

        viewModelScope.launch {
            val result = alertRepository.markAlertAsRead(alertId)
            _uiState.update { it.copy(isUpdating = false) }

            result.onFailure { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "No se pudo marcar la alerta como leída", error)
                _uiState.update {
                    it.copy(alerts = previousAlerts, error = error.message)
                }
            }

            // El usuario debe poder ver el reporte aunque el marcado falle.
            if (!reportId.isNullOrBlank()) _navigationEvent.emit(reportId)
        }
    }

    fun markAllAsRead() {
        val userId = resolvedUserId ?: return

        val previousAlerts = _uiState.value.alerts
        _uiState.update { state ->
            state.copy(
                isUpdating = true,
                alerts = state.alerts.map { it.copy(isRead = true) }
            )
        }

        viewModelScope.launch {
            val result = alertRepository.markAllAlertsAsRead(userId)
            _uiState.update { it.copy(isUpdating = false) }

            result.onFailure { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "No se pudieron marcar todas las alertas como leídas", error)
                _uiState.update {
                    it.copy(alerts = previousAlerts, error = error.message)
                }
            }
        }
    }

    fun deleteAlert(alertId: String) {
        if (alertId.isBlank()) return

        _uiState.update { state ->
            state.copy(alerts = state.alerts.filterNot { it.id == alertId })
        }

        viewModelScope.launch {
            alertRepository.deleteAlert(alertId).onFailure { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "No se pudo eliminar la alerta", error)
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    /** Solicita a la UI navegar al detalle del reporte asociado. */
    fun navigateToReport(reportId: String) {
        if (reportId.isNotBlank()) _navigationEvent.tryEmit(reportId)
    }

    fun retry() = loadAlerts()

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun resolveUserId(): String? =
        userIdArg.takeIf { it.isNotBlank() }
            ?: supabaseClient.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
}
