package com.app.buscandohuellas.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotificationPrefs"
private const val DATASTORE_NAME = "notification_preferences"

/**
 * Valor centinela que representa "Todas las distancias" en DataStore, ya que
 * las preferencias no admiten valores nulos.
 */
internal const val UNLIMITED_RADIUS_SENTINEL = -1.0

private val Context.notificationPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = DATASTORE_NAME)

/**
 * Persiste las preferencias de notificaciones con DataStore Preferences.
 */
@Singleton
class NotificationPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val dataStore = context.notificationPreferencesDataStore

    /**
     * Flujo con las preferencias actuales. Si DataStore falla al leer, se
     * emiten los valores por defecto para no romper la UI.
     */
    val preferences: Flow<NotificationPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.e(TAG, "Error leyendo las preferencias de notificaciones", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { stored -> stored.toNotificationPreferences() }
        .distinctUntilChanged()

    /** Guarda el conjunto completo de preferencias. */
    suspend fun updatePreferences(preferences: NotificationPreferences) {
        dataStore.edit { stored ->
            stored[Keys.ENABLED] = preferences.enabled
            stored[Keys.RADIUS] = preferences.radiusKm ?: UNLIMITED_RADIUS_SENTINEL
            stored[Keys.NOTIFY_LOST] = preferences.notifyLost
            stored[Keys.NOTIFY_SIGHTED] = preferences.notifySighted
            stored[Keys.NOTIFY_FOUND] = preferences.notifyFound
        }
    }

    /** Activa o desactiva las notificaciones conservando el resto de valores. */
    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { stored -> stored[Keys.ENABLED] = enabled }
    }

    /** Borra todas las preferencias guardadas (se usa al eliminar la cuenta). */
    suspend fun clear() {
        dataStore.edit { stored -> stored.clear() }
    }

    private fun Preferences.toNotificationPreferences(): NotificationPreferences {
        val radiusKm = this[Keys.RADIUS]
        return NotificationPreferences(
            enabled = this[Keys.ENABLED] ?: false,
            radius = NotificationRadius.fromKm(radiusKm),
            notifyLost = this[Keys.NOTIFY_LOST] ?: true,
            notifySighted = this[Keys.NOTIFY_SIGHTED] ?: true,
            notifyFound = this[Keys.NOTIFY_FOUND] ?: true
        )
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("notifications_enabled")
        val RADIUS = doublePreferencesKey("notification_radius_km")
        val NOTIFY_LOST = booleanPreferencesKey("notify_lost")
        val NOTIFY_SIGHTED = booleanPreferencesKey("notify_sighted")
        val NOTIFY_FOUND = booleanPreferencesKey("notify_found")
    }
}
