package com.app.buscandohuellas.ui.screens.createReport

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.repository.ReportRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject


sealed interface CreateReportUiState {
    data object Idle : CreateReportUiState
    data object Loading : CreateReportUiState
    data class Success(val warning: String? = null) : CreateReportUiState
    data class Error(val message: String) : CreateReportUiState
}

@HiltViewModel
class CreateReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow<CreateReportUiState>(CreateReportUiState.Idle)
    val uiState: StateFlow<CreateReportUiState> = _uiState.asStateFlow()

    private val _petName = MutableStateFlow("")
    val petName: StateFlow<String> = _petName.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _selectedStatus = MutableStateFlow(ReportStatus.LOST)
    val selectedStatus: StateFlow<ReportStatus> = _selectedStatus.asStateFlow()

    private val _latitude = MutableStateFlow<Double?>(null)
    val latitude: StateFlow<Double?> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow<Double?>(null)
    val longitude: StateFlow<Double?> = _longitude.asStateFlow()

    private val _isLocationLoading = MutableStateFlow(false)
    val isLocationLoading: StateFlow<Boolean> = _isLocationLoading.asStateFlow()

    
    private val _selectedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImageUris: StateFlow<List<Uri>> = _selectedImageUris.asStateFlow()

    private val _isUploadingImage = MutableStateFlow(false)
    val isUploadingImage: StateFlow<Boolean> = _isUploadingImage.asStateFlow()

    
    private val _isMapPickerOpen = MutableStateFlow(false)
    val isMapPickerOpen: StateFlow<Boolean> = _isMapPickerOpen.asStateFlow()

    fun onPetNameChange(value: String) {
        _petName.value = value
        clearError()
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
    }

    fun onStatusSelected(status: ReportStatus) {
        _selectedStatus.value = status
        clearError()
    }

    
    @SuppressLint("MissingPermission")
    fun getCurrentLocation() {
        _isLocationLoading.value = true
        try {
            val currentLocationRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                .build()

            fusedLocationClient.getCurrentLocation(currentLocationRequest, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        _latitude.value = location.latitude
                        _longitude.value = location.longitude
                        clearError()
                        _isLocationLoading.value = false
                    } else {
                        _uiState.value = CreateReportUiState.Error(
                            "No se pudo obtener la ubicación. Activa el GPS e inténtalo de nuevo."
                        )
                        _isLocationLoading.value = false
                    }
                }
                .addOnFailureListener { error ->
                    _uiState.value = CreateReportUiState.Error(
                        error.message ?: "No se pudo obtener la ubicación."
                    )
                    _isLocationLoading.value = false
                }
        } catch (securityException: SecurityException) {
            _uiState.value = CreateReportUiState.Error(
                "Necesitas conceder el permiso de ubicación para continuar."
            )
            _isLocationLoading.value = false
        }
    }

    
    fun onLocationPermissionDenied() {
        _uiState.value = CreateReportUiState.Error(
            "Necesitas conceder el permiso de ubicación para publicar el reporte."
        )
    }

    
    fun openMapPicker() {
        clearError()
        _isMapPickerOpen.value = true
    }

    
    fun closeMapPicker() {
        _isMapPickerOpen.value = false
    }

    
    fun onMapLocationSelected(latitude: Double, longitude: Double) {
        _latitude.value = latitude
        _longitude.value = longitude
        _isMapPickerOpen.value = false
        clearError()
    }

    
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        clearError()

        val current = _selectedImageUris.value
        val remaining = MAX_PHOTOS - current.size
        if (remaining <= 0) {
            _uiState.value = CreateReportUiState.Error("Máximo 5 fotos alcanzado.")
            return
        }

        val newUris = uris.filter { it !in current }.distinct()
        val accepted = newUris.take(remaining)
        if (accepted.size < newUris.size) {
            _uiState.value = CreateReportUiState.Error(
                "Solo puedes agregar hasta $MAX_PHOTOS fotos. Se añadieron ${accepted.size}."
            )
        }
        _selectedImageUris.value = current + accepted
    }

    fun removePhoto(uri: Uri) {
        _selectedImageUris.value = _selectedImageUris.value.filterNot { it == uri }
        clearError()
    }

    fun clearPhotos() {
        _selectedImageUris.value = emptyList()
        clearError()
    }

    fun onImageCaptured(bitmap: Bitmap) {
        if (_selectedImageUris.value.size >= MAX_PHOTOS) {
            _uiState.value = CreateReportUiState.Error("Máximo 5 fotos alcanzado.")
            return
        }

        viewModelScope.launch {
            try {
                val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                addPhotos(listOf(uri))
            } catch (error: Exception) {
                Log.e(TAG, "Error al guardar imagen de cámara", error)
                _uiState.value = CreateReportUiState.Error("No se pudo guardar la foto.")
            }
        }
    }

    fun createReport() {
        val petName = _petName.value.trim()
        val latitude = _latitude.value
        val longitude = _longitude.value
        val photos = _selectedImageUris.value

        if (petName.isEmpty()) {
            _uiState.value = CreateReportUiState.Error("Ingresa el nombre de la mascota.")
            return
        }
        if (latitude == null || longitude == null) {
            _uiState.value = CreateReportUiState.Error(
                "Obtén tu ubicación actual antes de publicar."
            )
            return
        }

        
        if (_isUploadingImage.value) {
            _uiState.value = CreateReportUiState.Error("Espera a que las fotos terminen de subir.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateReportUiState.Loading
            _isUploadingImage.value = true

            var uploadedUrls: List<String> = emptyList()
            try {
                val uploadResult = reportRepository.uploadReportImages(photos)
                    .getOrElse { error ->
                        _uiState.value = CreateReportUiState.Error(
                            error.message ?: "No se pudieron subir las fotos. Inténtalo de nuevo."
                        )
                        return@launch
                    }

                uploadedUrls = uploadResult.urls

                val report = Report(
                    userId = "",
                    petName = petName,
                    description = _description.value.trim(),
                    status = _selectedStatus.value,
                    imageUrl = uploadedUrls.firstOrNull(),
                    imageUrls = uploadedUrls.takeIf { it.isNotEmpty() },
                    latitude = latitude,
                    longitude = longitude
                )

                reportRepository.createReport(report)
                    .onSuccess {
                        val warning = if (uploadResult.hasFailures) {
                            "El reporte se publicó, pero ${uploadResult.failedCount} " +
                                if (uploadResult.failedCount == 1) {
                                    "foto no se pudo subir."
                                } else {
                                    "fotos no se pudieron subir."
                                }
                        } else {
                            null
                        }
                        _uiState.value = CreateReportUiState.Success(warning)
                    }
                    .onFailure { error ->
                        deleteUploadedPhotos(uploadedUrls)
                        _uiState.value = CreateReportUiState.Error(
                            error.message ?: "No se pudo publicar el reporte. Inténtalo de nuevo."
                        )
                    }
            } finally {
                _isUploadingImage.value = false
            }
        }
    }

    private suspend fun deleteUploadedPhotos(urls: List<String>) {
        urls.forEach { url ->
            runCatching { reportRepository.deleteReportImage(url) }
                .onFailure { Log.w(TAG, "No se pudo limpiar la foto huérfana: $url", it) }
        }
    }

    private fun clearError() {
        if (_uiState.value is CreateReportUiState.Error) {
            _uiState.value = CreateReportUiState.Idle
        }
    }

    companion object {
        const val MAX_PHOTOS = 5
        private const val TAG = "CreateReportViewModel"
    }
}
