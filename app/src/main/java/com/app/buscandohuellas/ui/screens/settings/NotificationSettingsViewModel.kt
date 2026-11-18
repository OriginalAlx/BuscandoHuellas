package com.app.buscandohuellas.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.data.NotificationPreferences
import com.app.buscandohuellas.data.NotificationPreferencesRepository
import com.app.buscandohuellas.data.NotificationRadius
import com.app.buscandohuellas.service.ReportRealtimeService
import com.app.buscandohuellas.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BH_DEBUG"

/**
 * Gestiona el estado de la pantalla de configuración de notificaciones y
 * persiste los cambios en DataStore.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val preferencesRepository: NotificationPreferencesRepository,
    private val realtimeService: ReportRealtimeService,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    /** Carga las preferencias persistidas. */
    fun loadPreferences() {
        viewModelScope.launch {
            val preferences = preferencesRepository.preferences.first()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    enabled = preferences.enabled,
                    radius = preferences.radius,
                    notifyLost = preferences.notifyLost,
                    notifySighted = preferences.notifySighted,
                    notifyFound = preferences.notifyFound
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled) }
    }

    fun setRadius(radius: NotificationRadius) {
        _uiState.update { it.copy(radius = radius) }
    }

    fun setNotifyLost(enabled: Boolean) {
        _uiState.update { it.copy(notifyLost = enabled) }
    }

    fun setNotifySighted(enabled: Boolean) {
        _uiState.update { it.copy(notifySighted = enabled) }
    }

    fun setNotifyFound(enabled: Boolean) {
        _uiState.update { it.copy(notifyFound = enabled) }
    }

    /**
     * Sincroniza el estado de los permisos con la UI. Si se acaba de conceder
     * alguno y las notificaciones están activas, reinicia la suscripción.
     */
    fun updatePermissions(
        hasLocationPermission: Boolean,
        hasNotificationPermission: Boolean
    ) {
        val previous = _uiState.value
        val justGranted =
            (!previous.hasLocationPermission && hasLocationPermission) ||
                (!previous.hasNotificationPermission && hasNotificationPermission)

        _uiState.update {
            it.copy(
                hasLocationPermission = hasLocationPermission,
                hasNotificationPermission = hasNotificationPermission
            )
        }

        if (justGranted && previous.enabled) {
            viewModelScope.launch { realtimeService.restart() }
        }
    }

    /**
     * Verifica y loguea el estado actual de los permisos de ubicación y
     * notificaciones, y sincroniza la UI.
     */
    fun checkPermissions() {
        val hasLocation = hasLocationPermission()
        val hasNotifications = notificationHelper.hasNotificationPermission()

        Log.e(TAG, "📍 Permiso de ubicación: ${if (hasLocation) "granted" else "denied"}")
        Log.e(
            TAG,
            "🔔 Permiso de notificaciones: ${if (hasNotifications) "granted" else "denied"}"
        )

        updatePermissions(hasLocation, hasNotifications)
    }

    /**
     * Envía una notificación local de prueba, independiente de Supabase
     * Realtime, para validar permisos y canal.
     */
    fun sendTestNotification() {
        Log.e(TAG, "🧪 Enviando notificación de prueba desde el ViewModel")
        notificationHelper.sendTestNotification(context)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    /** Persiste las preferencias y reinicia o detiene el servicio Realtime. */
    fun saveChanges() {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            val state = _uiState.value
            val preferences = NotificationPreferences(
                enabled = state.enabled,
                radius = state.radius,
                notifyLost = state.notifyLost,
                notifySighted = state.notifySighted,
                notifyFound = state.notifyFound
            )

            try {
                preferencesRepository.updatePreferences(preferences)

                if (preferences.enabled) {
                    realtimeService.restart()
                } else {
                    realtimeService.stop()
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = "Tus preferencias de notificaciones se guardaron."
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "No se pudieron guardar las preferencias", error)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "No se pudieron guardar los cambios. Inténtalo de nuevo."
                    )
                }
            }
        }
    }

    /** Consume el mensaje para que la UI no lo repita. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null, errorMessage = null) }
    }
}
