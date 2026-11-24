package com.app.buscandohuellas.domain.repository

import com.app.buscandohuellas.data.model.Alert
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a las alertas persistentes del usuario.
 *
 * Los flujos combinan la consulta inicial con los cambios de Supabase
 * Realtime, de modo que la lista y el badge del BottomBar se actualizan sin
 * recargar manualmente la pantalla.
 */
interface AlertRepository {

    /** Alertas del usuario, la más reciente primero. */
    fun getAlertsByUserId(userId: String): Flow<List<Alert>>

    /** Número de alertas no leídas (badge del BottomBar). */
    fun getUnreadAlertsCount(userId: String): Flow<Int>

    suspend fun markAlertAsRead(alertId: String): Result<Unit>

    suspend fun markAllAlertsAsRead(userId: String): Result<Unit>

    suspend fun deleteAlert(alertId: String): Result<Unit>

    suspend fun deleteAllAlerts(userId: String): Result<Unit>

    /** Elimina alertas más antiguas que [daysToKeep] días (mantenimiento). */
    suspend fun deleteOldAlerts(userId: String, daysToKeep: Int = 7): Result<Unit>
}
