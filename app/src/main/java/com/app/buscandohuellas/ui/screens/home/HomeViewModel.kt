package com.app.buscandohuellas.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.repository.AuthRepository
import com.app.buscandohuellas.domain.repository.ReportRepository
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _mapError = MutableStateFlow<String?>(null)
    val mapError: StateFlow<String?> = _mapError.asStateFlow()

    private val _shouldShowMap = MutableStateFlow(false)
    val shouldShowMap: StateFlow<Boolean> = _shouldShowMap.asStateFlow()

    private val _userLocation = MutableStateFlow<GeoPoint?>(null)
    val userLocation: StateFlow<GeoPoint?> = _userLocation.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _showPermissionDeniedDialog = MutableStateFlow(false)
    val showPermissionDeniedDialog: StateFlow<Boolean> = _showPermissionDeniedDialog.asStateFlow()

    val reportsForMap: StateFlow<List<Report>> get() = _reports
    val isLoadingMap: StateFlow<Boolean> get() = _isLoading

    init {
        Log.d("HomeViewModel", "Inicializando HomeViewModel")
        loadCurrentUser()
        loadReports()
    }

    fun loadReports() {
        viewModelScope.launch {
            Log.d("HomeViewModel", "🔄 PASO A: Iniciando loadReports()")
            _isLoading.value = true
            _mapError.value = null

            Log.d("HomeViewModel", "🔄 PASO B: Llamando a reportRepository.getAllReports()")

            reportRepository.getAllReports()
                .onSuccess { reportsList ->
                    Log.d("HomeViewModel", "✅ PASO C: Reportes recibidos: ${reportsList.size}")
                    reportsList.forEach { r ->
                        Log.d("HomeViewModel", "✅ PASO C.1: ${r.petName} | lat=${r.latitude} | lon=${r.longitude}")
                    }
                    _reports.value = reportsList
                    updateRecentReports()
                }
                .onFailure { error ->
                    Log.e("HomeViewModel", "❌ PASO D: Error: ${error.message}", error)
                    _mapError.value = error.message ?: "Error desconocido"
                    _reports.value = emptyList()
                    _uiState.update { current -> current.copy(recentReports = emptyList()) }
                }

            _isLoading.value = false
            Log.d("HomeViewModel", "🏁 PASO E: loadReports() completado")
        }
    }

    fun syncLocationPermission(hasPermission: Boolean) {
        _hasLocationPermission.value = hasPermission
    }

    fun onLocationPermissionResult(granted: Boolean, context: Context) {
        _hasLocationPermission.value = granted

        if (granted) {
            _showPermissionDeniedDialog.value = false
            _mapError.value = null
            getCurrentLocation(context)
            return
        }

        _shouldShowMap.value = false
        _userLocation.value = null
        _mapError.value = "Se necesita el permiso de ubicación para mostrar el mapa"

        val activity = context.findActivity()
        val permanentlyDenied = activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        _showPermissionDeniedDialog.value = permanentlyDenied
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        _isLoading.value = true

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onLocationObtained(location)
                    } else {
                        requestFreshLocation(fusedLocationClient)
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("HomeViewModel", "Error al obtener la última ubicación", error)
                    _isLoading.value = false
                    _mapError.value =
                        error.message ?: "No se pudo obtener tu ubicación. Activa el GPS e intenta de nuevo."
                }
        } catch (securityException: SecurityException) {
            Log.e("HomeViewModel", "Permiso de ubicación no concedido", securityException)
            _isLoading.value = false
            _hasLocationPermission.value = false
            _mapError.value = "Se necesita el permiso de ubicación para mostrar el mapa"
        }
    }

    fun dismissPermissionDialog() {
        _showPermissionDeniedDialog.value = false
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(fusedLocationClient: FusedLocationProviderClient) {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        try {
            fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onLocationObtained(location)
                    } else {
                        _isLoading.value = false
                        _mapError.value =
                            "No se pudo obtener tu ubicación. Verifica que el GPS esté activado."
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("HomeViewModel", "Error al obtener la ubicación actual", error)
                    _isLoading.value = false
                    _mapError.value = error.message ?: "No se pudo obtener tu ubicación."
                }
        } catch (securityException: SecurityException) {
            Log.e("HomeViewModel", "Permiso de ubicación no concedido", securityException)
            _isLoading.value = false
            _hasLocationPermission.value = false
            _mapError.value = "Se necesita el permiso de ubicación para mostrar el mapa"
        }
    }

    private fun onLocationObtained(location: Location) {
        Log.d(
            "HomeViewModel",
            "Ubicación del usuario: ${location.latitude}, ${location.longitude}"
        )
        _userLocation.value = GeoPoint(location.latitude, location.longitude)
        _shouldShowMap.value = true
        _hasLocationPermission.value = true
        _showPermissionDeniedDialog.value = false
        _mapError.value = null
        _isLoading.value = false

        updateRecentReports()

        if (_reports.value.isEmpty()) loadReports()
    }

    private fun updateRecentReports() {
        val mapped = _reports.value.map { report -> report.toRecentReport() }
        _uiState.update { current -> current.copy(recentReports = mapped) }
    }

    private fun Report.toRecentReport(): RecentReport {
        return RecentReport(
            id = id.orEmpty(),
            petName = petName,
            breed = description.ifBlank { "Sin descripción" },
            status = when (status.uppercase()) {
                "FOUND" -> PetReportStatus.FOUND
                "SIGHTED" -> PetReportStatus.FOUND
                else -> PetReportStatus.LOST
            },
            imageUrl = imageUrl,
            latitude = latitude,
            longitude = longitude
        )
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

    fun refreshReports() {
        loadReports()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
