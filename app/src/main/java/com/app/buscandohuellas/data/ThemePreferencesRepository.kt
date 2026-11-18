package com.app.buscandohuellas.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.buscandohuellas.data.model.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ThemePreferences"
private const val DATASTORE_NAME = "app_settings"

private val Context.appSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = DATASTORE_NAME)

/**
 * Persiste el tema elegido por el usuario en DataStore Preferences.
 *
 * Se mantiene separado de las preferencias de notificaciones para no mezclar
 * responsabilidades ni obligar a leer un objeto grande solo para el tema.
 */
@Singleton
class ThemePreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val dataStore = context.appSettingsDataStore

    /**
     * Flujo con el tema actual. Si DataStore falla al leer, se emite el valor
     * por defecto para no romper el arranque de la app.
     */
    val theme: Flow<AppTheme> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.e(TAG, "Error leyendo el tema guardado", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { stored -> AppTheme.fromStorage(stored[Keys.APP_THEME]) }
        .distinctUntilChanged()

    /** Guarda el tema seleccionado. */
    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { stored ->
            stored[Keys.APP_THEME] = theme.name
        }
    }

    private object Keys {
        val APP_THEME = stringPreferencesKey("app_theme")
    }
}
