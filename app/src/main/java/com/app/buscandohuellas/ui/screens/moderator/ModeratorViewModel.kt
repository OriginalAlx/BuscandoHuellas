package com.app.buscandohuellas.ui.screens.moderator

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.repository.AuthRepository
import com.app.buscandohuellas.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import javax.inject.Inject

private const val TAG = "ModeratorViewModel"

@HiltViewModel
class ModeratorViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModeratorUiState())
    val uiState: StateFlow<ModeratorUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(isRefresh: Boolean = false) {
        if (_uiState.value.isActionLoading) return

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    errorMessage = null,
                    userMessage = null,
                    reportsLoadError = null,
                    reportedPostsLoadError = null
                )
            }

            // Si una recarga falla, se conservan los datos ya visibles. En la
            // carga inicial estas listas están vacías.
            val previousState = _uiState.value

            // Reportes y denuncias se piden en paralelo para que el badge de la
            // pestaña tenga valor desde el primer render.
            coroutineScope {
                val userDeferred = async { authRepository.getCurrentUser() }
                val reportsDeferred = async { reportRepository.getAllReports() }
                val reportedPostsDeferred = async { reportRepository.getReportedPosts() }

                val user = userDeferred.await()
                val reportsResult = reportsDeferred.await()
                val reportedPostsResult = reportedPostsDeferred.await()

                val reports = reportsResult
                    .getOrNull()
                    ?.filter { !it.id.isNullOrBlank() }
                    ?: previousState.allReports

                val reportedPosts = reportedPostsResult
                    .getOrNull()
                    ?: previousState.reportedPosts
                val reportsLoadError = reportsResult.exceptionOrNull()?.message
                val reportedPostsLoadError = reportedPostsResult.exceptionOrNull()?.message

                _uiState.update { current ->
                    current.copy(
                        moderatorName = user
                            ?.fullName
                            ?.takeIf { it.isNotBlank() }
                            ?: "Moderador",
                        allReports = reports,
                        reportedPosts = reportedPosts,
                        isLoading = false,
                        isRefreshing = false,
                        reportsLoadError = reportsLoadError,
                        reportedPostsLoadError = reportedPostsLoadError,
                        errorMessage = reportsLoadError ?: reportedPostsLoadError
                    )
                }
            }

            applyFilters()
        }
    }

    fun selectTab(tab: ModeratorTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectFilter(filter: ReportFilterOption) {
        _uiState.update { it.copy(selectedFilter = filter) }
        applyFilters()
    }

    fun requestEdit(report: Report) {
        if (report.id.isNullOrBlank()) return
        _uiState.update { it.copy(reportToEdit = report) }
    }

    fun dismissEdit() {
        if (_uiState.value.isActionLoading) return
        _uiState.update { it.copy(reportToEdit = null) }
    }

    fun saveEdit(description: String, status: ReportStatus, reason: String) {
        val currentState = _uiState.value
        if (currentState.isActionLoading) return

        val report = currentState.reportToEdit ?: return
        val reportId = report.id ?: return
        val cleanDescription = description.trim()
        val cleanReason = reason.trim()

        val hasChanges = cleanDescription != report.description.trim() ||
            status != report.status

        if (cleanDescription.isBlank() || cleanReason.isBlank() || !hasChanges) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isActionLoading = true,
                    errorMessage = null,
                    userMessage = null
                )
            }

            reportRepository.updateReportAsModerator(
                reportId = reportId,
                description = cleanDescription,
                status = status,
                reason = cleanReason
            ).onSuccess {
                _uiState.update { current ->
                    current.copy(
                        allReports = current.allReports.map { item ->
                            if (item.id == reportId) {
                                item.copy(description = cleanDescription, status = status)
                            } else {
                                item
                            }
                        },
                        reportedPosts = current.reportedPosts.filterNot {
                            it.report.id == reportId
                        },
                        reportToEdit = null,
                        isActionLoading = false,
                        userMessage = "Reporte actualizado correctamente."
                    )
                }
                applyFilters()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionLoading = false,
                        errorMessage = error.message
                            ?: "No se pudo actualizar el reporte. Inténtalo de nuevo."
                    )
                }
            }
        }
    }

    fun requestDelete(report: Report) {
        if (report.id.isNullOrBlank()) return
        _uiState.update { it.copy(reportToDelete = report) }
    }

    fun dismissDelete() {
        if (_uiState.value.isActionLoading) return
        _uiState.update { it.copy(reportToDelete = null) }
    }

    fun confirmDelete() {
        val currentState = _uiState.value
        if (currentState.isActionLoading) return

        val report = currentState.reportToDelete ?: return
        val reportId = report.id ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isActionLoading = true,
                    errorMessage = null,
                    userMessage = null
                )
            }

            reportRepository.deleteReport(reportId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            allReports = state.allReports.filterNot { it.id == reportId },
                            reportedPosts = state.reportedPosts.filterNot {
                                it.report.id == reportId
                            },
                            reportToDelete = null,
                            isActionLoading = false,
                            userMessage = "Reporte eliminado correctamente."
                        )
                    }
                    applyFilters()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            errorMessage = error.message
                                ?: "No se pudo eliminar el reporte. Inténtalo de nuevo."
                        )
                    }
                }
        }
    }

    fun dismissFlags(reportId: String) {
        val currentState = _uiState.value
        if (currentState.isActionLoading || reportId.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isActionLoading = true,
                    errorMessage = null,
                    userMessage = null
                )
            }

            reportRepository.dismissReportFlags(reportId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            reportedPosts = state.reportedPosts.filterNot {
                                it.report.id == reportId
                            },
                            isActionLoading = false,
                            userMessage = "Denuncias descartadas correctamente."
                        )
                    }
                    applyFilters()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            errorMessage = error.message
                                ?: "No se pudieron descartar las denuncias. Inténtalo de nuevo."
                        )
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.logout()
                _uiState.update { it.copy(isLoggedOut = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo cerrar sesión", error)
                _uiState.update {
                    it.copy(
                        userMessage = error.message
                            ?.takeIf { message -> message.isNotBlank() }
                            ?: "No se pudo cerrar sesión. Inténtalo de nuevo."
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, userMessage = null) }
    }

    /**
     * Única fuente de verdad para la lista filtrada y las estadísticas.
     *
     * Las estadísticas siempre se calculan sobre [ModeratorUiState.allReports],
     * nunca sobre la lista filtrada.
     */
    private fun applyFilters() {
        _uiState.update { state ->
            val sortedReports = state.allReports.sortedByDescending {
                parseInstant(it.createdAt) ?: Instant.MIN
            }
            val filterStatus = state.selectedFilter.status

            state.copy(
                filteredReports = if (filterStatus == null) {
                    sortedReports
                } else {
                    sortedReports.filter { it.status == filterStatus }
                },
                stats = ModeratorStats(
                    active = state.allReports.count {
                        it.status == ReportStatus.LOST || it.status == ReportStatus.SIGHTED
                    },
                    reported = state.reportedPosts.size,
                    resolved = state.allReports.count { it.status == ReportStatus.FOUND }
                )
            )
        }
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null

        val trimmed = value.trim()
        return try {
            Instant.parse(trimmed)
        } catch (error: DateTimeParseException) {
            try {
                OffsetDateTime.parse(trimmed).toInstant()
            } catch (offsetError: DateTimeParseException) {
                try {
                    LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC)
                } catch (localError: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
