package com.app.buscandohuellas.ui.screens.editReport

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Datos editables del reporte. Las fotos se separan en dos listas porque las
 * existentes ya viven en el Storage (URLs) y las nuevas todavía son locales
 * (URIs) hasta que se guarda el formulario.
 */
data class EditReportForm(
    val petName: String = "",
    val description: String = "",
    val status: ReportStatus = ReportStatus.LOST,
    val imageUrls: List<String> = emptyList(),
    val newPhotoUris: List<Uri> = emptyList()
) {
    val totalPhotoCount: Int
        get() = imageUrls.size + newPhotoUris.size
}

sealed interface EditReportUiState {

    data object Loading : EditReportUiState

    data class Editing(
        val form: EditReportForm,
        val message: String? = null
    ) : EditReportUiState

    data class Saving(val form: EditReportForm) : EditReportUiState

    data class Success(val warning: String? = null) : EditReportUiState

    data class Error(val message: String) : EditReportUiState
}

@HiltViewModel
class EditReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val reportId: String = savedStateHandle.get<String>(REPORT_ID_ARG).orEmpty()

    private val _uiState = MutableStateFlow<EditReportUiState>(EditReportUiState.Loading)
    val uiState: StateFlow<EditReportUiState> = _uiState.asStateFlow()

    private var loadedReport: Report? = null

    init {
        loadReport()
    }

    fun loadReport() {
        if (reportId.isBlank()) {
            _uiState.value = EditReportUiState.Error("No se pudo identificar el reporte.")
            return
        }

        viewModelScope.launch {
            _uiState.value = EditReportUiState.Loading

            reportRepository.getReportById(reportId)
                .onSuccess { report ->
                    loadedReport = report
                    _uiState.value = EditReportUiState.Editing(
                        form = EditReportForm(
                            petName = report.petName,
                            description = report.description,
                            status = report.status,
                            imageUrls = report.allImageUrls.take(MAX_PHOTOS)
                        )
                    )
                }
                .onFailure { error ->
                    Log.e(TAG, "Error al cargar el reporte $reportId", error)
                    _uiState.value = EditReportUiState.Error(
                        error.message ?: "No se pudo cargar el reporte. Inténtalo de nuevo."
                    )
                }
        }
    }

    fun retry() = loadReport()

    fun updatePetName(value: String) = updateForm { it.copy(petName = value) }

    fun updateDescription(value: String) = updateForm { it.copy(description = value) }

    fun updateStatus(status: ReportStatus) = updateForm { it.copy(status = status) }

    fun addPhotos(uris: List<Uri>) {
        val state = editingState() ?: return
        if (uris.isEmpty()) return

        val newUris = uris
            .distinct()
            .filterNot { it in state.form.newPhotoUris }
        if (newUris.isEmpty()) return

        val remaining = MAX_PHOTOS - state.form.totalPhotoCount
        if (remaining <= 0) {
            _uiState.value = state.copy(message = "Máximo $MAX_PHOTOS fotos alcanzado.")
            return
        }

        val accepted = newUris.take(remaining)
        val message = if (accepted.size < newUris.size) {
            "Solo puedes tener hasta $MAX_PHOTOS fotos. Se añadieron ${accepted.size}."
        } else {
            null
        }

        _uiState.value = state.copy(
            form = state.form.copy(newPhotoUris = state.form.newPhotoUris + accepted),
            message = message
        )
    }

    fun removePhoto(imageUrl: String) {
        updateForm { form -> form.copy(imageUrls = form.imageUrls - imageUrl) }
    }

    fun removeNewPhoto(uri: Uri) {
        updateForm { form -> form.copy(newPhotoUris = form.newPhotoUris - uri) }
    }

    fun consumeMessage() {
        val state = editingState() ?: return
        if (state.message != null) {
            _uiState.value = state.copy(message = null)
        }
    }

    fun saveChanges() {
        val state = editingState() ?: return
        val form = state.form

        val petName = form.petName.trim()
        val description = form.description.trim()

        if (petName.isEmpty()) {
            _uiState.value = state.copy(message = "Ingresa el nombre de la mascota.")
            return
        }

        if (description.isEmpty()) {
            _uiState.value = state.copy(message = "Ingresa una descripción del reporte.")
            return
        }

        if (form.totalPhotoCount > MAX_PHOTOS) {
            _uiState.value = state.copy(
                message = "Solo puedes guardar hasta $MAX_PHOTOS fotos."
            )
            return
        }

        val trimmedForm = form.copy(petName = petName, description = description)
        _uiState.value = EditReportUiState.Saving(trimmedForm)

        viewModelScope.launch {
            var uploadedUrls: List<String> = emptyList()
            var failedUploads = 0

            if (trimmedForm.newPhotoUris.isNotEmpty()) {
                val uploadResult = reportRepository.uploadReportImages(trimmedForm.newPhotoUris)
                    .getOrElse { error ->
                        _uiState.value = EditReportUiState.Editing(
                            form = trimmedForm,
                            message = error.message
                                ?: "No se pudieron subir las fotos. Inténtalo de nuevo."
                        )
                        return@launch
                    }

                uploadedUrls = uploadResult.urls
                failedUploads = uploadResult.failedCount

                // Si no había fotos previas y fallaron todas las nuevas, no
                // tiene sentido guardar un reporte sin imágenes.
                if (uploadedUrls.isEmpty() && trimmedForm.imageUrls.isEmpty()) {
                    _uiState.value = EditReportUiState.Editing(
                        form = trimmedForm,
                        message = "No se pudieron subir las fotos. Inténtalo de nuevo."
                    )
                    return@launch
                }
            }

            val finalImageUrls = (trimmedForm.imageUrls + uploadedUrls)
                .distinct()
                .take(MAX_PHOTOS)

            val baseReport = loadedReport ?: Report(
                userId = "",
                petName = petName,
                description = description
            )

            val updatedReport = baseReport.copy(
                petName = petName,
                description = description,
                status = trimmedForm.status,
                imageUrl = finalImageUrls.firstOrNull(),
                imageUrls = finalImageUrls.takeIf { it.isNotEmpty() }
            )

            reportRepository.updateReport(reportId, updatedReport)
                .onSuccess {
                    loadedReport = updatedReport
                    _uiState.value = EditReportUiState.Success(uploadWarning(failedUploads))
                }
                .onFailure { error ->
                    Log.e(TAG, "No se pudo actualizar el reporte $reportId", error)
                    deleteUploadedPhotos(uploadedUrls)
                    _uiState.value = EditReportUiState.Editing(
                        form = trimmedForm,
                        message = error.message
                            ?: "No se pudo actualizar el reporte. Inténtalo de nuevo."
                    )
                }
        }
    }

    private fun editingState(): EditReportUiState.Editing? =
        _uiState.value as? EditReportUiState.Editing

    private fun updateForm(transform: (EditReportForm) -> EditReportForm) {
        val state = editingState() ?: return
        _uiState.value = state.copy(form = transform(state.form), message = null)
    }

    private suspend fun deleteUploadedPhotos(urls: List<String>) {
        urls.forEach { url ->
            reportRepository.deleteReportImage(url)
                .onFailure { Log.w(TAG, "No se pudo limpiar la foto huérfana: $url", it) }
        }
    }

    private fun uploadWarning(failedCount: Int): String? = when {
        failedCount <= 0 -> null
        failedCount == 1 -> "Los cambios se guardaron, pero 1 foto no se pudo subir."
        else -> "Los cambios se guardaron, pero $failedCount fotos no se pudieron subir."
    }

    companion object {
        const val MAX_PHOTOS = 5
        const val REPORT_ID_ARG = "reportId"
        private const val TAG = "EditReportViewModel"
    }
}
