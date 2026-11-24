package com.app.buscandohuellas.util

import android.util.Log
import com.app.buscandohuellas.data.NotificationPreferencesRepository
import com.app.buscandohuellas.data.NotifiedReportsRepository
import com.app.buscandohuellas.data.ThemePreferencesRepository
import com.app.buscandohuellas.ui.utils.RuntimePermissionRequestTracker
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BH_DEBUG"
private const val TOKEN_DELETE_TIMEOUT_MS = 8_000L

/**
 * Único punto de cierre de sesión de la app.
 *
 * Lo usan tanto el logout manual (Perfil/Home) como [AppLifecycleService]
 * cuando el usuario elimina la app de recientes. El orden es importante:
 * el token FCM se borra del servidor **antes** del `signOut()`, porque esa
 * operación necesita el JWT del usuario para pasar RLS.
 *
 * Después del `signOut()` se limpia todo DataStore para que la próxima apertura
 * arranque limpia y `AppNavHost` envíe al usuario a `login`.
 */
@Singleton
class SessionLogoutManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val firebaseTokenManager: FirebaseTokenManager,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val notifiedReportsRepository: NotifiedReportsRepository
) {

    suspend fun logout(reason: String) {
        Log.d(TAG, "=== LOGOUT START reason=$reason ===")

        val hadSession = supabaseClient.auth.currentUserOrNull() != null
        Log.d(TAG, "Logout: sesión activa antes de cerrar=$hadSession")

        // 1) Token FCM del servidor (best-effort, con timeout).
        try {
            val result = withTimeoutOrNull(TOKEN_DELETE_TIMEOUT_MS) {
                firebaseTokenManager.deleteCurrentToken()
            }
            when {
                result == null -> Log.w(TAG, "Logout: timeout eliminando el token FCM")
                result.isSuccess -> Log.d(TAG, "Logout: token FCM eliminado del servidor")
                else -> Log.w(TAG, "Logout: no se pudo eliminar el token FCM", result.exceptionOrNull())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Logout: error eliminando el token FCM", error)
        }

        // 2) Sesión de Supabase. signOut() invalida el JWT y borra la sesión
        // persistida por supabase-kt (DataStore/SharedPreferences interno).
        try {
            supabaseClient.auth.signOut()
            Log.d(TAG, "Logout: sesión de Supabase cerrada")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Logout: signOut() falló; se limpia el resto igualmente", error)
        }

        // 3) Todas las preferencias locales.
        clearLocalData()

        // 4) Caché en memoria del token (ubicación pendiente de sincronizar).
        firebaseTokenManager.clearLocalState()

        // 5) Permisos: al iniciar sesión de nuevo se deben reevaluar (el GPS
        // podría haberse apagado mientras la app estaba cerrada).
        RuntimePermissionRequestTracker.reset()
        Log.d(TAG, "Logout: flags de permisos reiniciados")

        Log.d(TAG, "=== LOGOUT END reason=$reason ===")
    }

    private suspend fun clearLocalData() {
        val steps: List<Pair<String, suspend () -> Unit>> = listOf(
            "notificaciones" to { notificationPreferencesRepository.clear() },
            "tema" to { themePreferencesRepository.clear() },
            "reportes notificados" to { notifiedReportsRepository.clear() }
        )

        steps.forEach { (name, clear) ->
            try {
                clear()
                Log.d(TAG, "Logout: DataStore limpiado ($name)")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Logout: no se pudo limpiar DataStore ($name)", error)
            }
        }
    }
}
