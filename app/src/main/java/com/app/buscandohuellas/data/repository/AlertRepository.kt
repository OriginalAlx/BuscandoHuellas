package com.app.buscandohuellas.data.repository

import android.util.Log
import com.app.buscandohuellas.data.dto.AlertDto
import com.app.buscandohuellas.data.model.Alert
import com.app.buscandohuellas.domain.repository.AlertRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlertRepository"
private const val SCHEMA = "public"
private const val ALERTS_TABLE = "alerts"
private const val CHANNEL_BASE_NAME = "alerts_user_changes"

/**
 * Implementación PostgREST + Realtime de [AlertRepository].
 *
 * Cada colección de [getAlertsByUserId] abre un canal de Realtime filtrado por
 * RLS (el usuario solo recibe sus propias filas) y vuelve a consultar la tabla
 * cuando llega cualquier INSERT/UPDATE/DELETE. El canal se elimina en
 * `awaitClose`, así que no queda escuchando cuando la pantalla se destruye.
 */
@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : AlertRepository {

    /** Scope para liberar canales cuando el flujo se cancela. */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getAlertsByUserId(userId: String): Flow<List<Alert>> {
        if (userId.isBlank()) return flowOf(emptyList())

        return callbackFlow {
            val channelId = "$CHANNEL_BASE_NAME-$userId-${UUID.randomUUID()}"
            val channel = supabaseClient.realtime.channel(channelId)

            val initial = runCatching { fetchAlerts(userId) }
            val initialError = initial.exceptionOrNull()
            if (initialError != null) {
                close(initialError)
                awaitClose { }
                return@callbackFlow
            }
            trySend(initial.getOrDefault(emptyList()))

            // El filtro debe registrarse ANTES de subscribe(). No se filtra por
            // user_id en el servidor porque RLS ya limita los eventos a las
            // filas del usuario autenticado.
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = SCHEMA) {
                table = ALERTS_TABLE
            }

            val realtimeJob = try {
                channel.subscribe()
                changes
                    .onEach {
                        runCatching { fetchAlerts(userId) }
                            .onSuccess { alerts -> trySend(alerts) }
                            .onFailure { error ->
                                Log.w(TAG, "No se pudieron refrescar las alertas", error)
                            }
                    }
                    .catch { error ->
                        Log.w(TAG, "El flujo de Realtime de alertas terminó", error)
                    }
                    .launchIn(this)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Sin Realtime la pantalla sigue funcionando con la carga
                // inicial; solo se pierde la actualización automática.
                Log.w(TAG, "No se pudo suscribir Realtime de alertas", error)
                null
            }

            awaitClose {
                realtimeJob?.cancel()
                cleanupScope.launch {
                    runCatching { supabaseClient.realtime.removeChannel(channel) }
                        .onFailure { error ->
                            Log.w(TAG, "No se pudo eliminar el canal de alertas", error)
                        }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getUnreadAlertsCount(userId: String): Flow<Int> =
        getAlertsByUserId(userId).map { alerts -> alerts.count { !it.isRead } }

    override suspend fun markAlertAsRead(alertId: String): Result<Unit> = try {
        if (alertId.isBlank()) throw IllegalArgumentException("Alerta inválida")

        val updated = supabaseClient
            .from(ALERTS_TABLE)
            .update(
                update = { set("is_read", true) },
                request = {
                    select(Columns.raw("id"))
                    filter { eq("id", alertId) }
                }
            )
            .decodeList<JsonObject>()

        if (updated.isEmpty()) {
            Result.failure(IllegalStateException("No se pudo marcar la alerta como leída."))
        } else {
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "Error marcando la alerta como leída", error)
        Result.failure(
            Exception(error.message ?: "No se pudo marcar la alerta como leída.")
        )
    }

    override suspend fun markAllAlertsAsRead(userId: String): Result<Unit> = try {
        if (userId.isBlank()) throw IllegalArgumentException("Usuario inválido")

        supabaseClient
            .from(ALERTS_TABLE)
            .update(
                update = { set("is_read", true) },
                request = {
                    select(Columns.raw("id"))
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }
            )
            .decodeList<JsonObject>()

        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "Error marcando todas las alertas como leídas", error)
        Result.failure(
            Exception(error.message ?: "No se pudieron marcar las alertas como leídas.")
        )
    }

    override suspend fun deleteAlert(alertId: String): Result<Unit> = try {
        if (alertId.isBlank()) throw IllegalArgumentException("Alerta inválida")

        supabaseClient.from(ALERTS_TABLE).delete {
            filter { eq("id", alertId) }
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "Error eliminando la alerta", error)
        Result.failure(Exception(error.message ?: "No se pudo eliminar la alerta."))
    }

    override suspend fun deleteAllAlerts(userId: String): Result<Unit> = try {
        if (userId.isBlank()) throw IllegalArgumentException("Usuario inválido")

        supabaseClient.from(ALERTS_TABLE).delete {
            filter { eq("user_id", userId) }
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "Error eliminando todas las alertas", error)
        Result.failure(Exception(error.message ?: "No se pudieron eliminar las alertas."))
    }

    override suspend fun deleteOldAlerts(
        userId: String,
        daysToKeep: Int
    ): Result<Unit> = try {
        if (userId.isBlank()) throw IllegalArgumentException("Usuario inválido")

        val cutoff = Instant.now()
            .minus(daysToKeep.coerceAtLeast(1).toLong(), ChronoUnit.DAYS)
            .toString()

        supabaseClient.from(ALERTS_TABLE).delete {
            filter {
                eq("user_id", userId)
                lt("created_at", cutoff)
            }
        }
        Log.d(TAG, "Limpieza de alertas anteriores a $cutoff completada")
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Es mantenimiento: un fallo no debe impedir ver las alertas.
        Log.w(TAG, "No se pudieron eliminar las alertas antiguas", error)
        Result.failure(Exception(error.message ?: "No se pudieron limpiar las alertas antiguas."))
    }

    private suspend fun fetchAlerts(userId: String): List<Alert> =
        supabaseClient
            .from(ALERTS_TABLE)
            .select(Columns.raw("*")) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<AlertDto>()
            .map { dto -> dto.toDomain() }
            .filter { alert -> alert.id.isNotBlank() }
}
