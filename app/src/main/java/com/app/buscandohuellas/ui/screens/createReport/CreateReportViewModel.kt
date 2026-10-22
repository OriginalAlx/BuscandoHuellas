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
import com.app.buscandohuellas.domain.repository.ReportRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Estado de la pantalla de creación de reporte.
 * Se usa `sealed interface` siguiendo la convención del proyecto (RegisterStatus).
 */
sealed interface CreateReportUiState {
    data object Idle : CreateReportUiState
    data object Loading : CreateReportUiState
    data object Success : CreateReportUiState
    data class Error(val message: String) : CreateReportUiState
}

@HiltViewModel
class CreateReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Cliente de ubicación creado internamente con el contexto de aplicación.
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow<CreateReportUiState>(CreateReportUiState.Idle)
    val uiState: StateFlow<CreateReportUiState> = _uiState.asStateFlow()

    private val _petName = MutableStateFlow("")
    val petName: StateFlow<String> = _petName.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _latitude = MutableStateFlow<Double?>(null)
    val latitude: StateFlow<Double?> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow<Double?>(null)
    val longitude: StateFlow<Double?> = _longitude.asStateFlow()

    private val _isLocationLoading = MutableStateFlow(false)
    val isLocationLoading: StateFlow<Boolean> = _isLocationLoading.asStateFlow()

    // --- Imagen ---
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _isUploadingImage = MutableStateFlow(false)
    val isUploadingImage: StateFlow<Boolean> = _isUploadingImage.asStateFlow()

    private val _uploadedImageUrl = MutableStateFlow<String?>(null)
    val uploadedImageUrl: StateFlow<String?> = _uploadedImageUrl.asStateFlow()

    // --- Selector de ubicación en mapa ---
    private val _isMapPickerOpen = MutableStateFlow(false)
    val isMapPickerOpen: StateFlow<Boolean> = _isMapPickerOpen.asStateFlow()

    fun onPetNameChange(value: String) {
        _petName.value = value
        clearError()
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
    }

    /**
     * Obtiene la última ubicación conocida. Requiere que el permiso
     * ACCESS_FINE_LOCATION ya esté concedido (lo gestiona la UI).
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocation() {
        _isLocationLoading.value = true
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        _latitude.value = location.latitude
                        _longitude.value = location.longitude
                        clearError()
                    } else {
                        _uiState.value = CreateReportUiState.Error(
                            "No se pudo obtener la ubicación. Activa el GPS e inténtalo de nuevo."
                        )
                    }
                    _isLocationLoading.value = false
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

    /** La UI avisa cuando el usuario rechaza el permiso de ubicación. */
    fun onLocationPermissionDenied() {
        _uiState.value = CreateReportUiState.Error(
            "Necesitas conceder el permiso de ubicación para publicar el reporte."
        )
    }

    // ------------------------------------------------------------------
    // Selector de ubicación en mapa (MapPickerScreen)
    // ------------------------------------------------------------------

    /** Abre la pantalla de selección de ubicación en el mapa. */
    fun openMapPicker() {
        clearError()
        _isMapPickerOpen.value = true
    }

    /** Cierra la pantalla del mapa sin modificar las coordenadas. */
    fun closeMapPicker() {
        _isMapPickerOpen.value = false
    }

    /**
     * Llamado por MapPickerScreen cuando el usuario confirma una ubicación.
     * Actualiza los StateFlows de latitud/longitud y cierra el selector.
     */
    fun onMapLocationSelected(latitude: Double, longitude: Double) {
        _latitude.value = latitude
        _longitude.value = longitude
        _isMapPickerOpen.value = false
        clearError()
    }

    /**
     * Preparado para el launcher de galería. La UI invoca el ActivityResultLauncher.
     * Este método queda para mantener la API solicitada (no sube aún).
     */
    fun selectImageFromGallery() {
        // La selección se gestiona en la UI con rememberLauncherForActivityResult
        clearError()
    }

    /**
     * Llamado desde la UI al obtener un Uri de galería o tras guardar la foto de cámara.
     * Inicia la subida a Supabase Storage.
     */
    fun onImageSelected(uri: Uri) {
        _selectedImageUri.value = uri
        _uploadedImageUrl.value = null
        uploadImageToStorage(uri)
    }

    /**
     * Llamado desde la UI al obtener un Bitmap de TakePicturePreview.
     * Guarda el bitmap en caché y lo sube a Supabase Storage.
     */
    fun onImageCaptured(bitmap: Bitmap) {
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
                onImageSelected(uri)
            } catch (error: Exception) {
                Log.e(TAG, "Error al guardar imagen de cámara", error)
                _uiState.value = CreateReportUiState.Error("No se pudo guardar la foto.")
            }
        }
    }

    /**
     * Sube la imagen a Supabase Storage (bucket: "pet-reports").
     * Genera un nombre único con timestamp y UUID.
     * Valida tamaño máximo 5MB. Actualiza selectedImageUri con la URL pública.
     */
    fun uploadImageToStorage(uri: Uri) {
        viewModelScope.launch {
            _isUploadingImage.value = true
            clearError()

            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("No se pudo leer la imagen seleccionada")

                val bytes = inputStream.use { it.readBytes() }

                // Validar tamaño máximo 5MB
                val maxSizeBytes = 5 * 1024 * 1024
                if (bytes.size > maxSizeBytes) {
                    _isUploadingImage.value = false
                    _selectedImageUri.value = null
                    _uiState.value = CreateReportUiState.Error(
                        "La imagen supera el tamaño máximo de 5MB."
                    )
                    return@launch
                }

                // Obtener extensión a partir del MIME type
                val mimeType = contentResolver.getType(uri)
                val extension = when (mimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    "image/bmp" -> "bmp"
                    "image/jpeg", "image/jpg" -> "jpg"
                    else -> {
                        // Intentar obtener extensión del URI
                        val path = uri.lastPathSegment.orEmpty()
                        if (path.contains(".")) {
                            path.substringAfterLast(".").lowercase().ifBlank { "jpg" }
                        } else {
                            "jpg"
                        }
                    }
                }

                reportRepository.uploadReportImage(bytes, extension)
                    .onSuccess { publicUrl ->
                        _uploadedImageUrl.value = publicUrl
                        // Actualiza selectedImageUri con la URL obtenida (según especificación)
                        _selectedImageUri.value = Uri.parse(publicUrl)
                        _isUploadingImage.value = false
                    }
                    .onFailure { error ->
                        _isUploadingImage.value = false
                        _selectedImageUri.value = null
                        _uiState.value = CreateReportUiState.Error(
                            error.message ?: "No se pudo subir la imagen."
                        )
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Error al leer/subir imagen", error)
                _isUploadingImage.value = false
                _selectedImageUri.value = null
                _uiState.value = CreateReportUiState.Error(
                    "No se pudo procesar la imagen."
                )
            }
        }
    }

    /** Elimina la imagen seleccionada (local) y borra del storage si ya fue subida. */
    fun clearImage() {
        val urlToDelete = _uploadedImageUrl.value
        _selectedImageUri.value = null
        _uploadedImageUrl.value = null
        _isUploadingImage.value = false
        if (uiState.value is CreateReportUiState.Error) {
            clearError()
        }

        // Operación best-effort: eliminar del storage en segundo plano
        if (urlToDelete != null) {
            viewModelScope.launch {
                reportRepository.deleteReportImage(urlToDelete)
            }
        }
    }

    fun createReport() {
        val petName = _petName.value.trim()
        val latitude = _latitude.value
        val longitude = _longitude.value

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

        // Si hay una imagen seleccionada pero aún se está subiendo, esperar
        if (_isUploadingImage.value) {
            _uiState.value = CreateReportUiState.Error("Espera a que la imagen termine de subir.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateReportUiState.Loading

            // El repositorio sobrescribe userId con el de la sesión autenticada.
            val report = Report(
                userId = "",
                petName = petName,
                description = _description.value.trim(),
                status = "LOST",
                imageUrl = _uploadedImageUrl.value,
                latitude = latitude,
                longitude = longitude
            )

            reportRepository.createReport(report)
                .onSuccess {
                    _uiState.value = CreateReportUiState.Success
                }
                .onFailure { error ->
                    _uiState.value = CreateReportUiState.Error(
                        error.message ?: "No se pudo publicar el reporte. Inténtalo de nuevo."
                    )
                }
        }
    }

    private fun clearError() {
        if (_uiState.value is CreateReportUiState.Error) {
            _uiState.value = CreateReportUiState.Idle
        }
    }

    companion object {
        private const val TAG = "CreateReportViewModel"
    }
}
