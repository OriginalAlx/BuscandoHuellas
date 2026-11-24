package com.app.buscandohuellas.util

import android.util.Log
import com.app.buscandohuellas.data.NotificationPreferencesRepository
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Tag único de diagnóstico: filtrar Logcat por "BH_DEBUG". */
private const val TAG = "BH_DEBUG"

/** Tabla de Supabase donde se asocian usuarios y tokens de dispositivo. */
private const val TOKENS_TABLE = "user_notification_tokens"

/** Función RPC atómica que registra el token para el usuario autenticado. */
private const val REGISTER_TOKEN_FUNCTION = "register_notification_token"

/**
 * Gestiona el ciclo de vida del token FCM del dispositivo contra Supabase.
 *
 * Diseño de la tabla (ver `supabase/alerts.sql`):
 * - `user_id`  → UUID del usuario autenticado (`auth.users.id`).
 * - `fcm_token`→ token único del dispositivo (constraint UNIQUE).
 *
 * Además del token, la fila guarda las preferencias de notificaciones y la
 * última ubicación del usuario, que la Edge Function `generate-alerts` usa para
 * decidir a quién avisar. Al guardar el token se sincronizan las preferencias
 * locales para que el servidor nunca envíe avisos que el usuario desactivó.
 *
 * El registro se hace mediante la función RPC `register_notification_token`,
 * que es `SECURITY DEFINER` y hace un UPSERT sobre `user_id`. Así, si el
 * mismo dispositivo cambia de cuenta (por ejemplo, porque el logout anterior
 * se hizo sin conexión y la fila sigue asociada al usuario anterior), la fila
 * se reasigna al usuario actual sin violar las políticas RLS.
 *
 * El fallback directo usa `on_conflict=user_id`, por lo que la tabla debe
 * tener un UNIQUE(user_id) (ver `supabase/alerts.sql`).
 *
 * Al cerrar sesión se elimina la fila por token (RLS solo permite borrar las
 * filas propias, que es justo lo que necesitamos).
 */
@Singleton
class FirebaseTokenManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val preferencesRepository: NotificationPreferencesRepository
) {

    /**
     * Última ubicación conocida del dispositivo. Se guarda en memoria para
     * aplicarla a la fila del token cuando termine de crearse, incluso si la
     * ubicación se obtuvo antes que el token.
     */
    @Volatile
    private var lastLocation: Pair<Double, Double>? = null

    /**
     * Guarda (inserta o reasigna) el token FCM del usuario autenticado.
     *
     * @return [Result.success] si se guardó; [Result.failure] si no hay sesión
     * o si Supabase devolvió un error.
     */
    suspend fun saveToken(token: String): Result<Unit> = try {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        when {
            userId.isNullOrBlank() -> {
                Log.e(TAG, "❌ No se puede guardar el token FCM: no hay usuario autenticado")
                Result.failure(IllegalStateException("No hay usuario autenticado"))
            }

            token.isBlank() -> {
                Log.e(TAG, "⚠️ saveToken() ignorado: token vacío")
                Result.success(Unit)
            }

            else -> {
                try {
                    // RPC atómica: inserta o reasigna el token al usuario actual.
                    // Se usa una función SECURITY DEFINER para poder actualizar
                    // una fila que pertenezca a otra cuenta sin abrir la RLS.
                    supabaseClient.postgrest.rpc(
                        function = REGISTER_TOKEN_FUNCTION,
                        parameters = buildJsonObject {
                            put("p_fcm_token", token)
                        }
                    )
                    Log.e(TAG, "✅ Token FCM guardado en Supabase vía RPC para user_id=$userId")
                } catch (rpcError: RestException) {
                    if (!isMissingRpcFunction(rpcError)) throw rpcError

                    // Compatibilidad: si aún no se ha creado la función en el
                    // proyecto, se mantiene el upsert directo (requiere que la
                    // fila del token no pertenezca a otro usuario).
                    Log.e(
                        TAG,
                        "⚠️ RPC $REGISTER_TOKEN_FUNCTION no disponible; usando upsert directo"
                    )
                    supabaseClient.from(TOKENS_TABLE).upsert(
                        NotificationTokenRow(userId = userId, fcmToken = token)
                    ) {
                        onConflict = "user_id"
                    }
                    Log.e(TAG, "✅ Token FCM guardado en Supabase vía upsert para user_id=$userId")
                }

                // Si la ubicación ya se conoce, se aplica a la fila recién
                // creada/actualizada para que la Edge Function pueda calcular
                // distancias.
                lastLocation?.let { (latitude, longitude) ->
                    runCatching { updateLocationRow(userId, latitude, longitude) }
                        .onFailure { error ->
                            Log.w(TAG, "No se pudo aplicar la ubicación al token", error)
                        }
                }

                // El servidor debe conocer las preferencias locales (la app
                // arranca con las notificaciones desactivadas) para no enviar
                // avisos que el usuario no quiere.
                syncPreferencesFromLocal()

                Result.success(Unit)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "❌ Error guardando el token FCM en Supabase", error)
        Result.failure(error)
    }

    /**
     * Elimina del servidor el token indicado. Se usa al cerrar sesión para que
     * el usuario no siga recibiendo push en este dispositivo.
     */
    suspend fun deleteToken(token: String): Result<Unit> = try {
        if (token.isBlank()) {
            Log.e(TAG, "⚠️ deleteToken() ignorado: token vacío")
            Result.success(Unit)
        } else {
            supabaseClient.from(TOKENS_TABLE).delete {
                filter {
                    eq("fcm_token", token)
                }
            }
            Log.e(TAG, "✅ Token FCM eliminado de Supabase")
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "❌ Error eliminando el token FCM de Supabase", error)
        Result.failure(error)
    }

    /**
     * Obtiene el token FCM actual del dispositivo de forma suspendida, sin
     * depender de `kotlinx-coroutines-play-services`.
     *
     * `getToken()` está marcado como obsoleto en firebase-messaging 26.x, pero
     * sigue siendo la única API que devuelve el token directamente cuando la
     * "V1 registration" no está habilitada (que es el caso por defecto). Se
     * silencia el warning a propósito; la alternativa (`register()` + callback
     * `onRegistered`) se cubre también en el servicio FCM.
     */
    @Suppress("DEPRECATION")
    suspend fun currentTokenOrNull(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener

                if (task.isSuccessful) {
                    val token = task.result
                    Log.e(TAG, "🔑 Token FCM actual: $token")
                    continuation.resume(token)
                } else {
                    Log.e(TAG, "❌ No se pudo obtener el token FCM", task.exception)
                    continuation.resume(null)
                }
            }
    }

    /**
     * Atajo para el cierre de sesión: obtiene el token del dispositivo y lo
     * elimina de Supabase. Es best-effort: si falla, se loguea y se continúa
     * con el logout para no bloquear al usuario.
     */
    suspend fun deleteCurrentToken(): Result<Unit> {
        val token = currentTokenOrNull()
        if (token.isNullOrBlank()) {
            Log.e(TAG, "⚠️ No hay token FCM para eliminar al cerrar sesión")
            return Result.success(Unit)
        }
        return deleteToken(token)
    }

    /** Limpia el estado en memoria asociado a la sesión anterior. */
    fun clearLocalState() {
        lastLocation = null
        Log.e(TAG, "🧹 Caché local del token FCM limpiada")
    }

    /**
     * Guarda la última ubicación del usuario en su fila de
     * `user_notification_tokens`.
     *
     * La Edge Function `generate-alerts` la usa para calcular la distancia al
     * reporte y aplicar `radius_km`. Si aún no hay sesión o la fila del token
     * no existe, la ubicación se conserva en memoria y se aplica la próxima
     * vez que se guarde el token.
     */
    suspend fun updateLocation(latitude: Double, longitude: Double): Result<Unit> = try {
        lastLocation = latitude to longitude

        val userId = currentUserIdOrNull()
        if (userId == null) {
            Log.e(TAG, "⚠️ Ubicación guardada en memoria: aún no hay sesión")
            Result.success(Unit)
        } else {
            updateLocationRow(userId, latitude, longitude)
            Log.e(TAG, "✅ Ubicación del usuario sincronizada con Supabase")
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "❌ Error guardando la ubicación del usuario", error)
        Result.failure(error)
    }

    /**
     * Sincroniza en el servidor las preferencias de notificaciones que la app
     * guarda en DataStore. La Edge Function las necesita para filtrar por
     * radio y tipo de reporte.
     */
    suspend fun syncNotificationPreferences(
        enabled: Boolean,
        radiusKm: Double?,
        notifyLost: Boolean,
        notifySighted: Boolean,
        notifyFound: Boolean
    ): Result<Unit> = try {
        val userId = currentUserIdOrNull()
        if (userId == null) {
            Result.success(Unit)
        } else {
            supabaseClient
                .from(TOKENS_TABLE)
                .update(
                    update = {
                        set("notifications_enabled", enabled)
                        set("radius_km", radiusKm)
                        set("notify_lost", notifyLost)
                        set("notify_sighted", notifySighted)
                        set("notify_found", notifyFound)
                    },
                    request = {
                        filter { eq("user_id", userId) }
                    }
                )
                .decodeList<JsonObject>()

            Log.e(TAG, "✅ Preferencias de notificación sincronizadas con Supabase")
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "❌ Error sincronizando las preferencias de notificación", error)
        Result.failure(error)
    }

    /**
     * Lee las preferencias de DataStore y las refleja en la fila del token.
     * Best-effort: si DataStore o la red fallan, el token ya quedó guardado y
     * la app sigue funcionando con los valores locales.
     */
    private suspend fun syncPreferencesFromLocal() {
        try {
            val preferences = preferencesRepository.preferences.first()
            syncNotificationPreferences(
                enabled = preferences.enabled,
                radiusKm = preferences.radiusKm,
                notifyLost = preferences.notifyLost,
                notifySighted = preferences.notifySighted,
                notifyFound = preferences.notifyFound
            ).onFailure { error ->
                Log.w(TAG, "No se pudieron sincronizar las preferencias del token", error)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "No se pudieron leer las preferencias locales", error)
        }
    }

    private suspend fun updateLocationRow(
        userId: String,
        latitude: Double,
        longitude: Double
    ) {
        supabaseClient
            .from(TOKENS_TABLE)
            .update(
                update = {
                    set("latitude", latitude)
                    set("longitude", longitude)
                },
                request = {
                    filter { eq("user_id", userId) }
                }
            )
            .decodeList<JsonObject>()
    }

    private fun currentUserIdOrNull(): String? =
        supabaseClient.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }

    /** Detecta el 404 de PostgREST cuando la función RPC aún no existe. */
    private fun isMissingRpcFunction(error: RestException): Boolean {
        val message = error.message.orEmpty()
        return error.statusCode == 404 ||
            message.contains("PGRST202", ignoreCase = true) ||
            message.contains("Could not find the function", ignoreCase = true)
    }
}

/** Fila de la tabla `user_notification_tokens`. */
@Serializable
private data class NotificationTokenRow(
    @SerialName("user_id") val userId: String,
    @SerialName("fcm_token") val fcmToken: String
)
