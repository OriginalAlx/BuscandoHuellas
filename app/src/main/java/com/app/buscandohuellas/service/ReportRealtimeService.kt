// ============================================================================
// ⚠️ RECORDATORIO CRÍTICO DE RLS (Row Level Security) — LEER ANTES DE DEPURAR
// ============================================================================
// Para que Supabase Realtime entregue los eventos INSERT de `public.reports`
// al cliente que escucha, ese usuario DEBE tener una política RLS de SELECT
// sobre la tabla. Supabase evalúa RLS también para Realtime: si el rol
// `authenticated` no puede leer la fila, el servidor **descarta el evento en
// silencio** y el cliente nunca recibe nada (no hay error ni log).
//
// Ejecuta una vez en el Editor SQL de Supabase:
//
//   alter table public.reports enable row level security;
//   drop policy if exists "reports_select_authenticated" on public.reports;
//   create policy "reports_select_authenticated"
//   on public.reports for select to authenticated using (true);
//
//   -- La tabla también debe estar en la publicación de Realtime:
//   alter publication supabase_realtime add table public.reports;
//
// Comprobación:
//   select * from pg_publication_tables where pubname = 'supabase_realtime';
// ============================================================================

package com.app.buscandohuellas.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.app.buscandohuellas.data.NotificationPreferencesRepository
import com.app.buscandohuellas.data.NotifiedReportsRepository
import com.app.buscandohuellas.data.dto.ReportDto
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.ui.utils.LocationUtils
import com.app.buscandohuellas.util.NotificationHelper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Tag único de diagnóstico: filtrar Logcat por "BH_DEBUG". */
private const val TAG = "BH_DEBUG"
private const val SCHEMA = "public"
private const val REPORTS_TABLE = "reports"
private const val CHANNEL_BASE_NAME = "reports_public_changes"
private const val RETRY_DELAY_MS = 10_000L
private const val SUBSCRIBE_TIMEOUT_MS = 15_000L
private const val LOCATION_TIMEOUT_MS = 8_000L

/**
 * Servicio singleton que escucha inserciones en `public.reports` con Supabase
 * Realtime y publica una notificación local cuando el reporte coincide con las
 * preferencias del usuario.
 *
 * La suscripción vive en un [CoroutineScope] raíz creado dentro del singleton,
 * por lo que es totalmente independiente del ciclo de vida de Activities y
 * ViewModels. Solo se cancela si el proceso muere o si se llama a [stop] /
 * [restart].
 *
 * API usada (supabase-kt 3.8.0, la versión declarada en este proyecto):
 * - Creación del canal: `supabaseClient.realtime.channel(id)`.
 * - Registro del filtro: `channel.postgresChangeFlow<PostgresAction.Insert>`
 *   ANTES de suscribirse (equivale a eventType = INSERT, schema = "public",
 *   table = "reports"). Debe llamarse antes de `subscribe()`: la librería
 *   lanza `IllegalStateException` si el canal ya está suscrito.
 * - Suscripción explícita: `channel.subscribe(blockUntilSubscribed = true)`.
 * - Recolección: `collect` sobre el flujo, dentro de este scope.
 */
@Singleton
class ReportRealtimeService @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val preferencesRepository: NotificationPreferencesRepository,
    private val notifiedReportsRepository: NotifiedReportsRepository,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context
) {

    /**
     * Scope raíz e independiente.
     *
     * [SupervisorJob] se crea SIN padre, así que no hereda la cancelación de
     * ninguna Activity, Fragment ni ViewModel. Un fallo en una corrutina hija
     * tampoco cancela al resto. [Dispatchers.IO] mantiene la escucha fuera del
     * hilo principal.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Job de la suscripción actual. */
    private var listeningJob: Job? = null

    /** Canal activo, usado para limpiarlo de forma explícita. */
    @Volatile
    private var activeChannel: RealtimeChannel? = null

    /**
     * Inicia la escucha de Realtime si no hay una suscripción activa.
     *
     * Es idempotente: si ya existe un job vivo, no crea otro, para evitar
     * canales duplicados cuando Application, MainActivity y
     * ProcessLifecycleOwner llaman a este método. La cancelación explícita de
     * una suscripción anterior se hace en [restart] con `cancelAndJoin()`.
     */
    fun startListening() {
        Log.e(TAG, "=== startListening() EJECUTADO ===")
        Log.e(TAG, "Scope independiente activo=${serviceScope.isActive}")
        Log.e(TAG, "Job actual activo=${listeningJob?.isActive}")
        Log.e(TAG, "Realtime status=${supabaseClient.realtime.status.value}")

        if (listeningJob?.isActive == true) {
            Log.e(TAG, "Ya hay una suscripción activa; se reutiliza (sin duplicar)")
            return
        }

        // La corrutina se lanza en el scope raíz independiente del servicio.
        listeningJob = serviceScope.launch {
            Log.e(TAG, "Corrutina de escucha lanzada en scope independiente")
            while (isActive) {
                try {
                    listenForReports()
                    // El flujo terminó sin excepción: reintentamos tras una pausa.
                    Log.e(
                        TAG,
                        "El flujo de Realtime terminó; reintentando en $RETRY_DELAY_MS ms"
                    )
                    delay(RETRY_DELAY_MS)
                } catch (error: CancellationException) {
                    Log.e(TAG, "Suscripción cancelada explícitamente")
                    throw error
                } catch (error: Exception) {
                    Log.e(
                        TAG,
                        "ERROR en Realtime; reintentando en $RETRY_DELAY_MS ms",
                        error
                    )
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    /** Detiene la suscripción; el canal se libera en el `finally` de la escucha. */
    fun stop() {
        Log.e(TAG, "=== stop() solicitado: deteniendo suscripción ===")
        listeningJob?.cancel()
        listeningJob = null
    }

    /**
     * Cancela la suscripción anterior (si existe), espera a que termine y
     * arranca una nueva. Se usa al cambiar las preferencias o los permisos.
     *
     * `cancelAndJoin()` garantiza que no queden escuchas duplicadas ni fugas:
     * el `finally` de [listenForReports] elimina el canal anterior.
     */
    suspend fun restart() {
        Log.e(TAG, "=== restart(): cancelando suscripción anterior ===")
        listeningJob?.cancelAndJoin()
        listeningJob = null

        withContext(NonCancellable) {
            activeChannel?.let { channel ->
                runCatching { supabaseClient.realtime.removeChannel(channel) }
                    .onFailure { error ->
                        Log.e(TAG, "ERROR eliminando el canal anterior", error)
                    }
            }
            activeChannel = null
        }

        startListening()
    }

    /**
     * Crea el canal, registra el filtro de INSERT, se suscribe explícitamente
     * y recolecta los cambios.
     *
     * Orden obligatorio:
     * 1. `channel.postgresChangeFlow<PostgresAction.Insert>` (registra el
     *    `postgres_changes` que viaja en el join del canal).
     * 2. `channel.subscribe(blockUntilSubscribed = true)`.
     * 3. `collect`.
     */
    private suspend fun listenForReports() {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
        Log.e(TAG, "currentUserId obtenido: ${currentUserId ?: "NULL (sin sesión activa)"}")
        if (currentUserId == null) {
            Log.e(
                TAG,
                "❌ currentUserId es null: no hay sesión; se reintenta en $RETRY_DELAY_MS ms"
            )
            return
        }

        Log.e(TAG, "Usuario autenticado para Realtime: $currentUserId")

        // Nombre único por intento: evita reutilizar un canal obsoleto que
        // quedó registrado en Realtime.subscriptions con otro join payload.
        val channelId = "$CHANNEL_BASE_NAME-${System.currentTimeMillis()}"
        Log.e(TAG, "📡 Creando canal de Realtime... (id=$channelId)")
        val channel = supabaseClient.realtime.channel(channelId)
        activeChannel = channel

        try {
            // 1) Filtro estricto: SOLO INSERT en public.reports.
            //    Debe registrarse ANTES de subscribe().
            val changes = channel.postgresChangeFlow<PostgresAction.Insert>(
                schema = SCHEMA
            ) {
                table = REPORTS_TABLE
            }
            Log.e(
                TAG,
                "Filtro registrado: eventType=INSERT, schema=$SCHEMA, table=$REPORTS_TABLE"
            )

            // 2) Suscripción explícita, con timeout para no quedarnos colgados
            //    si el servidor rechaza el join (RLS, tabla fuera de la
            //    publicación, token inválido...). Sin timeout, la corrutina
            //    esperaría SUBSCRIBED para siempre y no habría ni logs ni
            //    reintentos.
            Log.e(TAG, "Llamando a channel.subscribe()...")
            val subscribed = withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
                channel.subscribe(blockUntilSubscribed = true)
                channel.status.value == RealtimeChannel.Status.SUBSCRIBED
            } ?: false

            if (!subscribed) {
                Log.e(
                    TAG,
                    "❌ No se pudo suscribir el canal. Estado=${channel.status.value}. " +
                        "Revisa RLS de SELECT y la publicación supabase_realtime."
                )
                error("No se alcanzó SUBSCRIBED en $SUBSCRIBE_TIMEOUT_MS ms")
            }

            Log.e(TAG, "✅ Canal suscrito exitosamente (estado=${channel.status.value})")
            Log.e(TAG, "Iniciando recolección del flujo de INSERT...")

            // 3) Recolección dentro del scope independiente del servicio.
            changes.collect { action ->
                Log.e(
                    TAG,
                    "📨 EVENTO CRUDO RECIBIDO de Supabase " +
                        "(tipo=INSERT, schema=$SCHEMA, tabla=$REPORTS_TABLE)"
                )
                Log.e(TAG, "📨 Payload crudo: ${action.record}")
                try {
                    // Un reporte malformado no debe romper toda la suscripción.
                    handleInsert(action, currentUserId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(
                        TAG,
                        "ERROR procesando un reporte; se ignora y se continúa escuchando",
                        error
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "ERROR en Realtime", error)
            throw error
        } finally {
            // Limpieza del canal incluso si la corrutina se cancela.
            withContext(NonCancellable) {
                runCatching { supabaseClient.realtime.removeChannel(channel) }
                    .onFailure { error ->
                        Log.e(TAG, "ERROR eliminando el canal de Realtime", error)
                    }
                if (activeChannel === channel) activeChannel = null
                Log.e(TAG, "Canal de Realtime liberado (id=$channelId)")
            }
        }
    }

    /**
     * Diagnóstico desglosado: registra la decodificación y cada filtro en
     * orden estricto antes de decidir si se notifica.
     */
    private suspend fun handleInsert(
        action: PostgresAction.Insert,
        currentUserId: String
    ) {
        // 1) Intento de decodificación.
        Log.e(TAG, "📦 Intentando decodificar a ReportDto...")
        val report: Report = try {
            action.decodeRecord<ReportDto>().toDomain()
        } catch (error: Exception) {
            Log.e(TAG, "❌ Error decodificando", error)
            return
        }

        // 2) Decodificación exitosa.
        Log.e(
            TAG,
            "✅ Decodificación exitosa. ID: ${report.id}, User: ${report.userId}"
        )

        val reportId = report.id?.takeIf { it.isNotBlank() } ?: run {
            Log.e(TAG, "❌ Descartado: el reporte decodificado no tiene id")
            return
        }

        // Pre-check: preferencias generales.
        val preferences = try {
            preferencesRepository.preferences.first()
        } catch (error: Exception) {
            Log.e(TAG, "❌ Error leyendo las preferencias de notificaciones", error)
            return
        }
        Log.e(TAG, "🔍 Pre-check: notificaciones activadas=${preferences.enabled}")
        if (!preferences.enabled) {
            Log.e(TAG, "❌ Descartado: notificaciones desactivadas en preferencias")
            return
        }

        // 3) Filtro 1: mismo usuario.
        Log.e(
            TAG,
            "🔍 Filtro 1: ¿Es el mismo usuario? " +
                "(Report: ${report.userId} vs Local: $currentUserId)"
        )
        if (report.userId == currentUserId) {
            Log.e(TAG, "❌ Filtro 1 RECHAZADO: el reporte es del propio usuario")
            return
        }
        Log.e(TAG, "✅ Filtro 1 APROBADO: el reporte es de otro usuario")

        // 4) Filtro 2: estado permitido por preferencias.
        Log.e(
            TAG,
            "🔍 Filtro 2: ¿Estado permitido por preferencias? " +
                "(estado=${report.status.value}, perdidas=${preferences.notifyLost}, " +
                "avistadas=${preferences.notifySighted}, " +
                "encontradas=${preferences.notifyFound})"
        )
        if (!preferences.isStatusEnabled(report.status)) {
            Log.e(
                TAG,
                "❌ Filtro 2 RECHAZADO: estado ${report.status.value} desactivado"
            )
            return
        }
        Log.e(TAG, "✅ Filtro 2 APROBADO: estado ${report.status.value} permitido")

        // 5) Filtro 3: rango de distancia.
        val radiusKm = preferences.radiusKm
        Log.e(
            TAG,
            "🔍 Filtro 3: ¿Dentro del rango de distancia? " +
                "(radio=${radiusKm?.let { "$it km" } ?: "Todas las distancias"})"
        )
        val distanceKm: Double? = if (radiusKm == null) {
            // Radio "Todas las distancias": se aprueba sin calcular distancia.
            Log.e(
                TAG,
                "✅ Filtro 3 APROBADO: radio ilimitado, se omite el cálculo de distancia"
            )
            null
        } else {
            val calculatedDistance = resolveDistanceWithinRadius(report, radiusKm)
            if (calculatedDistance == null) {
                Log.e(
                    TAG,
                    "❌ Filtro 3 RECHAZADO: fuera de rango o sin datos de ubicación"
                )
                return
            }
            Log.e(
                TAG,
                "✅ Filtro 3 APROBADO: distancia=${calculatedDistance} km " +
                    "<= radio=$radiusKm km"
            )
            calculatedDistance
        }

        // 6) Filtro 4: ya notificado.
        Log.e(TAG, "🔍 Filtro 4: ¿Ya fue notificado previamente? (reportId=$reportId)")
        if (notifiedReportsRepository.isNotified(reportId)) {
            Log.e(TAG, "❌ Filtro 4 RECHAZADO: el reporte $reportId ya fue notificado")
            return
        }
        Log.e(TAG, "✅ Filtro 4 APROBADO: el reporte no había sido notificado")

        // 7) Todos los filtros aprobados.
        Log.e(TAG, "🚀 ¡TODOS LOS FILTROS APROBADOS! Generando notificación...")
        notificationHelper.showNearbyReportNotification(report, distanceKm)
        notifiedReportsRepository.addNotifiedId(reportId)
        Log.e(TAG, "✅ Reporte $reportId marcado como notificado")
    }

    /**
     * Calcula y loguea la distancia al reporte. Devuelve `null` si debe
     * descartarse (sin coordenadas, sin ubicación del usuario o fuera de rango).
     */
    private suspend fun resolveDistanceWithinRadius(
        report: Report,
        radiusKm: Double
    ): Double? {
        val reportLatitude = report.latitude ?: run {
            Log.e(TAG, "❌ Sin latitud en el reporte; no se puede calcular la distancia")
            return null
        }
        val reportLongitude = report.longitude ?: run {
            Log.e(TAG, "❌ Sin longitud en el reporte; no se puede calcular la distancia")
            return null
        }

        val userLocation = currentLocationOrNull() ?: run {
            Log.e(TAG, "❌ No se pudo obtener la ubicación del usuario")
            return null
        }

        val distanceKm = LocationUtils.calculateDistance(
            userLat = userLocation.latitude,
            userLon = userLocation.longitude,
            reportLat = reportLatitude,
            reportLon = reportLongitude
        )

        Log.e(
            TAG,
            "📏 Distancia calculada: $distanceKm km " +
                "(radio configurado: $radiusKm km)"
        )

        return distanceKm.takeIf { it <= radiusKm } ?: run {
            Log.e(TAG, "❌ Distancia $distanceKm km supera el radio de $radiusKm km")
            null
        }
    }

    /** Obtiene la ubicación actual del usuario con timeout. */
    private suspend fun currentLocationOrNull(): Location? {
        if (!hasLocationPermission()) {
            Log.e(TAG, "❌ Sin permiso de ubicación para calcular la distancia")
            return null
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()

        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

                try {
                    client.getCurrentLocation(request, cancellationTokenSource.token)
                        .addOnSuccessListener { location -> continuation.resume(location) }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "ERROR obteniendo la ubicación actual", error)
                            continuation.resume(null)
                        }
                } catch (error: SecurityException) {
                    Log.e(TAG, "ERROR de permiso de ubicación", error)
                    continuation.resume(null)
                }
            }
        }
    }

    /** Comprueba si hay permiso de ubicación (fina o aproximada). */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
}
