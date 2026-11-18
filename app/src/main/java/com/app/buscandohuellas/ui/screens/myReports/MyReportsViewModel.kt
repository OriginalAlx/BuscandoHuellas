package com.app.buscandohuellas.ui.screens.myReports

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de "Mis reportes".
 *
 * Resuelve el usuario autenticado desde Supabase (o usa el `userId` recibido
 * por navegación) y expone la lista de reportes junto con los estados de
 * carga, error y eliminación.
 */
@HiltViewModel
class MyReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyReportsUiState())
    val uiState: StateFlow<MyReportsUiState> = _uiState.asStateFlow()

    /** Último usuario cargado, para reintentar sin volver a resolverlo. */
    private var currentUserId: String? = null

    /**
     * Carga los reportes del usuario indicado. Si [userId] llega vacío (por
     * ejemplo, porque el perfil aún no terminó de cargar), se resuelve el
     * usuario autenticado directamente desde la sesión de Supabase.
     */
    fun loadReports(userId: String) {
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isLoading = true, error = null) }

            val resolvedUserId = userId.takeIf { it.isNotBlank() }
                ?: supabaseClient.auth.currentUserOrNull()?.id

            if (resolvedUserId.isNullOrBlank()) {
                Log.e(TAG, "No hay usuario autenticado para cargar sus reportes")
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = "No se pudo identificar al usuario autenticado."
                    )
                }
                return@launch
            }

            currentUserId = resolvedUserId

            try {
                reportRepository.getReportsByUser(resolvedUserId).collect { reports ->
                    Log.d(TAG, "Reportes cargados para $resolvedUserId: ${reports.size}")
                    _uiState.update { current ->
                        current.copy(
                            reports = reports,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Error al cargar los reportes de $resolvedUserId", error)
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = error.message
                            ?: "No se pudieron cargar tus reportes. Inténtalo de nuevo."
                    )
                }
            }
        }
    }

    /** Reintenta la última carga (botón "Reintentar"). */
    fun retry() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            loadReports("")
        } else {
            loadReports(userId)
        }
    }

    /**
     * Elimina un reporte tras la confirmación del usuario. Actualiza la lista
     * de forma optimista solo cuando el servidor confirma el borrado.
     */
    fun deleteReport(reportId: String) {
        if (reportId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { current -> current.copy(deletingReportId = reportId) }

            reportRepository.deleteReport(reportId)
                .onSuccess {
                    Log.d(TAG, "Reporte $reportId eliminado")
                    _uiState.update { current ->
                        current.copy(
                            reports = current.reports.filterNot { it.id == reportId },
                            deletingReportId = null,
                            message = "Reporte eliminado correctamente."
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "No se pudo eliminar el reporte $reportId", error)
                    _uiState.update { current ->
                        current.copy(
                            deletingReportId = null,
                            message = error.message
                                ?: "No se pudo eliminar el reporte. Inténtalo de nuevo."
                        )
                    }
                }
        }
    }

    /** Limpia el mensaje después de mostrarlo en el snackbar. */
    fun consumeMessage() {
        _uiState.update { current -> current.copy(message = null) }
    }

    private companion object {
        const val TAG = "MyReportsViewModel"
    }
}
