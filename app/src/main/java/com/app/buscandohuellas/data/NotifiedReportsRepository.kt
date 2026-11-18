package com.app.buscandohuellas.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotifiedReports"
private const val DATASTORE_NAME = "notified_reports"
private const val ID_SEPARATOR = ","
private const val MAX_NOTIFIED_IDS = 100

private val Context.notifiedReportsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = DATASTORE_NAME)

/**
 * Guarda los IDs de los reportes que ya generaron una notificación local para
 * evitar avisos duplicados. El conjunto se limita a [MAX_NOTIFIED_IDS] con
 * estrategia FIFO.
 */
@Singleton
class NotifiedReportsRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val dataStore = context.notifiedReportsDataStore
    private val mutex = Mutex()

    /** IDs notificados, del más antiguo al más reciente. */
    suspend fun getNotifiedIds(): Set<String> = readIds().toSet()

    /** Indica si el reporte ya fue notificado. */
    suspend fun isNotified(reportId: String): Boolean {
        if (reportId.isBlank()) return false
        return reportId in readIds()
    }

    /** Añade un ID al final y descarta los más antiguos si supera el límite. */
    suspend fun addNotifiedId(reportId: String) {
        if (reportId.isBlank()) return

        mutex.withLock {
            dataStore.edit { stored ->
                val current = decodeIds(stored[Keys.NOTIFIED_IDS])
                val updated = (current + reportId)
                    .distinct()
                    .takeLast(MAX_NOTIFIED_IDS)
                stored[Keys.NOTIFIED_IDS] = encodeIds(updated)
            }
        }
    }

    /** Limpia el historial (útil al cerrar sesión o depurar). */
    suspend fun clear() {
        mutex.withLock {
            dataStore.edit { stored -> stored.remove(Keys.NOTIFIED_IDS) }
        }
    }

    private suspend fun readIds(): List<String> = try {
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    Log.e(TAG, "Error leyendo los reportes notificados", error)
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .first()
            .let { stored -> decodeIds(stored[Keys.NOTIFIED_IDS]) }
    } catch (error: IOException) {
        Log.e(TAG, "No se pudo leer el historial de notificaciones", error)
        emptyList()
    }

    private fun decodeIds(raw: String?): List<String> =
        raw.orEmpty()
            .split(ID_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun encodeIds(ids: List<String>): String = ids.joinToString(ID_SEPARATOR)

    private object Keys {
        val NOTIFIED_IDS = stringPreferencesKey("notified_report_ids")
    }
}
