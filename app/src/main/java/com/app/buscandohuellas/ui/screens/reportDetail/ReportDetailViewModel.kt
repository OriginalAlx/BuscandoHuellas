package com.app.buscandohuellas.ui.screens.reportDetail

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.repository.AuthRepository
import com.app.buscandohuellas.domain.repository.ReportRepository
import com.app.buscandohuellas.ui.utils.LocationUtils
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import javax.inject.Inject

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reportRepository: ReportRepository,
    private val authRepository: AuthRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val reportId: String = savedStateHandle.get<String>(REPORT_ID_ARG).orEmpty()

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _sightingInput = MutableStateFlow("")
    val sightingInput: StateFlow<String> = _sightingInput.asStateFlow()

    private var loadedReport: Report? = null
    private var userLocation: GeoPoint? = null
    private var currentUserName: String? = null
    private var reporterName: String = ""
    private var reporterAvatarUrl: String? = null
    private var isOwner: Boolean = false

    private val sightings = mutableListOf<SightingUi>()
    private var sightingSequence = 0L

    init {
        loadReport()
    }

    fun loadReport() {
        if (reportId.isBlank()) {
            _uiState.value = DetailUiState.Error("No se pudo identificar el reporte.")
            return
        }

        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading

            reportRepository.getReportById(reportId)
                .onSuccess { report ->
                    loadedReport = report
                    isOwner = supabaseClient.auth.currentUserOrNull()?.id == report.userId
                    resolveReporter(report)
                    publishSuccess(report)
                }
                .onFailure { error ->
                    Log.e(TAG, "Error al cargar el reporte $reportId", error)
                    _uiState.value = DetailUiState.Error(
                        error.message ?: "No se pudo cargar el reporte. Inténtalo de nuevo."
                    )
                }
        }
    }

    fun retry() = loadReport()

    private suspend fun resolveReporter(report: Report) {
        val currentUser = runCatching { authRepository.getCurrentUser() }.getOrNull()
        currentUserName = currentUser?.fullName?.takeIf { it.isNotBlank() }

        val profileName = report.userName
            .takeIf { it.isNotBlank() && it != Report.DEFAULT_USER_NAME }
        val ownReportName = if (currentUser?.id == report.userId) currentUserName else null

        reporterName = profileName
            ?: ownReportName
            ?: Report.DEFAULT_USER_NAME

        reporterAvatarUrl = report.userAvatarUrl?.takeIf { it.isNotBlank() }
    }

    private fun publishSuccess(report: Report) {
        _uiState.value = DetailUiState.Success(
            report = report,
            formattedDistance = buildFormattedDistance(report),
            isOwner = isOwner,
            relativeTime = formatRelativeTime(report.createdAt),
            userName = reporterName.ifBlank { Report.DEFAULT_USER_NAME },
            userAvatarUrl = reporterAvatarUrl,
            sightings = sightings.toList()
        )
    }

    fun onSightingInputChange(value: String) {
        _sightingInput.value = value.take(MAX_SIGHTING_LENGTH)
    }

    fun addSighting() {
        val message = _sightingInput.value.trim()
        if (message.isEmpty()) return

        val report = loadedReport ?: return
        sightingSequence += 1
        sightings += SightingUi(
            id = sightingSequence,
            authorName = currentUserName?.takeIf { it.isNotBlank() } ?: "Tú",
            message = message
        )
        _sightingInput.value = ""
        publishSuccess(report)
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

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
                    Log.w(TAG, "No se pudo obtener la última ubicación", error)
                }
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Permiso de ubicación no concedido", securityException)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(fusedLocationClient: FusedLocationProviderClient) {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()

        try {
            fusedLocationClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) onLocationObtained(location)
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "No se pudo obtener la ubicación actual", error)
                }
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Permiso de ubicación no concedido", securityException)
        }
    }

    private fun onLocationObtained(location: Location) {
        userLocation = GeoPoint(location.latitude, location.longitude)
        loadedReport?.let { publishSuccess(it) }
    }

    private fun buildFormattedDistance(report: Report): String {
        val reportLatitude = report.latitude ?: return NO_LOCATION_LABEL
        val reportLongitude = report.longitude ?: return NO_LOCATION_LABEL
        val currentLocation = userLocation ?: return NO_LOCATION_LABEL

        val distanceKm = LocationUtils.calculateDistance(
            currentLocation.latitude,
            currentLocation.longitude,
            reportLatitude,
            reportLongitude
        )
        return formatDistanceLabel(distanceKm, report.locationReference())
    }

    companion object {
        private const val TAG = "ReportDetailViewModel"
        private const val MAX_SIGHTING_LENGTH = 280
        internal const val NO_LOCATION_LABEL = "Ubicación no disponible"

        const val REPORT_ID_ARG = "reportId"
    }
}

/**
 * Convierte una fecha ISO-8601 de Supabase en un texto relativo en español.
 */
internal fun formatRelativeTime(createdAt: String?, now: Instant = Instant.now()): String {
    val instant = parseInstant(createdAt) ?: return ""
    val elapsed = Duration.between(instant, now)
    if (elapsed.isNegative) return "Hace un momento"

    val minutes = elapsed.toMinutes()
    val hours = elapsed.toHours()
    val days = elapsed.toDays()

    return when {
        minutes < 1 -> "Hace un momento"
        minutes < 60 -> "Hace $minutes ${plural(minutes, "minuto", "minutos")}"
        hours < 24 -> "Hace $hours ${plural(hours, "hora", "horas")}"
        days < 7 -> "Hace $days ${plural(days, "día", "días")}"
        days < 30 -> {
            val weeks = days / 7
            "Hace $weeks ${plural(weeks, "semana", "semanas")}"
        }
        days < 365 -> {
            val months = days / 30
            "Hace $months ${plural(months, "mes", "meses")}"
        }
        else -> {
            val years = days / 365
            "Hace $years ${plural(years, "año", "años")}"
        }
    }
}

private fun plural(value: Long, singular: String, plural: String): String =
    if (value == 1L) singular else plural

private fun parseInstant(createdAt: String?): Instant? {
    if (createdAt.isNullOrBlank()) return null

    val value = createdAt.trim()
    return try {
        Instant.parse(value)
    } catch (error: DateTimeParseException) {
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (offsetError: DateTimeParseException) {
            try {
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
            } catch (localError: DateTimeParseException) {
                null
            }
        }
    }
}

internal fun formatDistanceLabel(distanceKm: Double, reference: String?): String {
    val distanceText = "A ${LocationUtils.formatDistance(distanceKm)} de ti"
    val cleanReference = reference?.trim()?.takeIf { it.isNotBlank() }
    return if (cleanReference != null) "$distanceText · $cleanReference" else distanceText
}

/**
 * Devuelve el nombre o referencia legible de la ubicación. Nunca devuelve
 * coordenadas crudas ni el valor WKT `POINT(...)` de la base de datos.
 */
internal fun Report.locationReference(): String? {
    return listOfNotNull(locationName, reference, location)
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.isRawCoordinate() }
}

private fun String.isRawCoordinate(): Boolean {
    if (contains("POINT", ignoreCase = true)) return true
    if (matches(EWKB_REGEX)) return true
    return matches(COORDINATES_REGEX)
}

private val COORDINATES_REGEX =
    Regex("""^-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?$""")

/** EWKB hexadecimal que devuelve PostgREST para columnas `geometry`. */
private val EWKB_REGEX = Regex("""^[0-9a-fA-F]{42,}$""")
