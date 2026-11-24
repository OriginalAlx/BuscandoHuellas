// INSTRUCCIONES DE PRUEBA:
// 1. Filtrar Logcat con: "BH_DEBUG"
// 2. Abrir la app y observar logs de inicialización
// 3. Ir a pantalla de Notificaciones y tocar "🧪 Enviar notificación de prueba"
// 4. Si la notificación de prueba NO aparece → problema de permisos o canal
// 5. Si la notificación de prueba SÍ aparece → problema de Realtime
// 6. Crear un reporte desde OTRA cuenta y observar logs

package com.app.buscandohuellas

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.app.buscandohuellas.data.ThemePreferencesRepository
import com.app.buscandohuellas.data.model.AppTheme
import com.app.buscandohuellas.navigation.AppNavHost
import com.app.buscandohuellas.navigation.Screen
import com.app.buscandohuellas.service.ReportRealtimeService
import com.app.buscandohuellas.ui.theme.BuscandoHuellasTheme
import com.app.buscandohuellas.util.FirebaseTokenManager
import com.app.buscandohuellas.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var reportRealtimeService: ReportRealtimeService

    @Inject
    lateinit var firebaseTokenManager: FirebaseTokenManager

    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository

    private val pendingReportId = mutableStateOf<String?>(null)

    /** Último usuario cuyo token FCM ya se sincronizó (evita upserts repetidos). */
    private var lastTokenSyncedUserId: String? = null

    /**
     * Token FCM obtenido al iniciar la Activity. Se guarda en memoria para
     * reutilizarlo cuando el usuario inicia sesión, sin volver a llamar a
     * Firebase (el único punto de inicialización es [fetchFcmTokenAndSync]).
     */
    private var fcmToken: String? = null

    /**
     * Tema leído de forma síncrona (con timeout) al arrancar. Evita que la
     * primera composición use el tema del sistema cuando el usuario forzó el
     * modo claro u oscuro, y permite pintar la ventana con el color correcto
     * antes de que Compose dibuje el primer frame.
     */
    private var initialTheme: AppTheme = AppTheme.SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmDroidInitializer.initialize(this)

        // Deep link: se lee el extra o la URI y se limpian del Intent para que
        // una rotación o recreación no vuelva a navegar al detalle.
        pendingReportId.value = consumeReportIdExtra(intent)

        // Se resuelve el tema antes de dibujar para que la ventana y las barras
        // del sistema arranquen ya con el color correcto (sin flash blanco).
        initialTheme = readInitialTheme()
        val initialDarkTheme = resolveDarkTheme(initialTheme)
        applyThemeToWindow(initialDarkTheme)
        enableEdgeToEdge(
            statusBarStyle = systemBarStyle(initialDarkTheme),
            navigationBarStyle = systemBarStyle(initialDarkTheme)
        )

        // Arranque directo y limpio del servicio persistente. El servicio vive
        // en su propio CoroutineScope raíz, así que no depende de esta Activity.
        Log.e("BH_DEBUG", "=== MainActivity onCreate: iniciando ReportRealtimeService ===")
        try {
            reportRealtimeService.startListening()
            Log.e("BH_DEBUG", "startListening() invocado correctamente")
        } catch (error: Exception) {
            Log.e("BH_DEBUG", "ERROR al invocar startListening()", error)
        }

        // FCM: obtención del token INCONDICIONAL al arrancar la Activity.
        // Es el único punto de inicialización del token en toda la app.
        fetchFcmTokenAndSync()

        // Los permisos en runtime los solicita HomeScreen al entrar, para que
        // el usuario vea un único diálogo en el momento en que se necesitan.

        // Si el usuario inicia sesión después, se reutiliza el token cacheado.
        observeSessionForTokenSync()

        setContent {
            // El tema elegido en Configuración se observa desde DataStore y se
            // aplica a toda la app; "Automático" delega en el sistema.
            val appTheme by themePreferencesRepository.theme
                .collectAsStateWithLifecycle(initialValue = initialTheme)
            val darkTheme = when (appTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            // Al cambiar el tema se actualizan el fondo de la ventana y el
            // contraste de las barras sin recrear la Activity ni parpadear.
            LaunchedEffect(darkTheme) {
                applyThemeToWindow(darkTheme)
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(darkTheme),
                    navigationBarStyle = systemBarStyle(darkTheme)
                )
            }

            BuscandoHuellasTheme(darkTheme = darkTheme) {
                AppNavHost(
                    pendingReportId = pendingReportId.value,
                    onPendingReportIdConsumed = { pendingReportId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Al tocar una notificación con la app abierta (singleTop), se extrae
        // el report_id y se limpia del Intent.
        pendingReportId.value = consumeReportIdExtra(intent)
    }

    /**
     * Lee el tema guardado de forma síncrona con un timeout corto.
     *
     * DataStore es la fuente de verdad, pero su primera emisión es asíncrona.
     * Bloquear el arranque como máximo 250 ms permite aplicar el fondo correcto
     * antes del primer frame sin penalizar de forma perceptible el inicio. Si
     * la lectura tarda más, se usa el tema del sistema y el flujo de Compose
     * corrige el tema en cuanto llega el valor real.
     */
    private fun readInitialTheme(): AppTheme = runCatching {
        runBlocking {
            withTimeoutOrNull(250L) { themePreferencesRepository.theme.first() }
        }
    }.getOrNull() ?: AppTheme.SYSTEM

    /** Resuelve si el tema elegido implica UI oscura en este momento. */
    private fun resolveDarkTheme(theme: AppTheme): Boolean = when (theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM ->
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Pinta el fondo de la ventana con el mismo color que usa Compose. Así no
     * se ve un destello blanco durante el arranque ni al cambiar de tema.
     */
    private fun applyThemeToWindow(darkTheme: Boolean) {
        val background = if (darkTheme) 0xFF121218.toInt() else 0xFFF7F6FC.toInt()
        window.setBackgroundDrawable(ColorDrawable(background))
    }

    /**
     * Estilo de barras del sistema acorde al tema resuelto, no al del sistema.
     * En modo claro se piden iconos oscuros; en oscuro, iconos claros.
     */
    private fun systemBarStyle(darkTheme: Boolean): SystemBarStyle =
        if (darkTheme) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                scrim = AndroidColor.TRANSPARENT,
                // Fallback para API 24-26, donde no existen iconos oscuros
                // en la barra de navegación.
                darkScrim = AndroidColor.argb(0x66, 0x00, 0x00, 0x00)
            )
        }

    /**
     * Extrae el ID de reporte del Intent y lo elimina para evitar navegaciones
     * duplicadas al rotar la pantalla o recrear la Activity.
     *
     * Se admiten dos formatos:
     * - Extra `report_id` enviado por las notificaciones push.
     * - Deep link `buscandohuellas://report/{reportId}`.
     *
     * Cuando llega por URI se limpia `intent.data` después de leerlo, porque la
     * navegación la hace [AppNavHost] a partir de `pendingReportId`; así el
     * NavHost no procesa el mismo link dos veces.
     */
    private fun consumeReportIdExtra(intent: Intent?): String? {
        if (intent == null) return null

        val extraId = intent
            .getStringExtra(NotificationHelper.EXTRA_REPORT_ID)
            ?.takeIf { it.isNotBlank() }

        val deepLinkId = intent.data
            ?.takeIf { uri ->
                uri.scheme == Screen.DEEP_LINK_SCHEME &&
                    uri.host == Screen.DEEP_LINK_HOST
            }
            ?.lastPathSegment
            ?.takeIf { it.isNotBlank() }

        intent.removeExtra(NotificationHelper.EXTRA_REPORT_ID)
        if (deepLinkId != null) {
            intent.data = null
        }

        val reportId = extraId ?: deepLinkId
        if (reportId != null) {
            Log.e("BH_DEBUG", "🔗 Deep link recibido: report_id=$reportId")
        }
        return reportId
    }

    /**
     * Obtiene el token FCM del dispositivo de forma incondicional y, si hay
     * sesión, lo envía inmediatamente a Supabase.
     *
     * `getToken()` está obsoleto en firebase-messaging 26.x, pero es la API
     * válida por defecto (la ruta "V1 registration" está deshabilitada). El
     * `try/catch` cubre el caso de que alguien active esa ruta por metadatos:
     * `getToken()` lanzaría `IllegalStateException` y el token llegaría por
     * `onRegistered` en el servicio FCM.
     */
    @Suppress("DEPRECATION")
    private fun fetchFcmTokenAndSync() {
        Log.e("BH_DEBUG", "Iniciando obtención del token FCM...")
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("BH_DEBUG", "❌ Error obteniendo token FCM: ${task.exception}")
                    return@addOnCompleteListener
                }

                val token = task.result
                fcmToken = token
                Log.e("BH_DEBUG", "✅ Token FCM obtenido: $token")

                Log.e("BH_DEBUG", "Enviando token al servidor...")
                lifecycleScope.launch {
                    val result = firebaseTokenManager.saveToken(token)
                    if (result.isSuccess) {
                        // Marca al usuario ya sincronizado para que el observer
                        // de sesión no repita el mismo upsert en este arranque.
                        lastTokenSyncedUserId =
                            SupabaseClient.client.auth.currentUserOrNull()?.id
                        Log.e("BH_DEBUG", "✅ Token FCM sincronizado con Supabase")
                    } else {
                        Log.e("BH_DEBUG", "⚠️ Token FCM no sincronizado (¿sin sesión?)")
                    }
                }
            }
        } catch (error: IllegalStateException) {
            Log.e("BH_DEBUG", "❌ Error obteniendo token FCM: $error")
        }
    }

    /**
     * Escucha los cambios de sesión para enviar a Supabase el token FCM ya
     * obtenido en [fetchFcmTokenAndSync]. No vuelve a llamar a Firebase: si el
     * token aún no está en caché (login antes de que responda Firebase), el
     * envío lo hará el propio callback de [fetchFcmTokenAndSync].
     */
    private fun observeSessionForTokenSync() {
        lifecycleScope.launch {
            SupabaseClient.client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val userId = status.session.user?.id
                        if (userId != null && userId != lastTokenSyncedUserId) {
                            lastTokenSyncedUserId = userId
                            val token = fcmToken
                            if (token.isNullOrBlank()) {
                                Log.e(
                                    "BH_DEBUG",
                                    "⚠️ Sesión autenticada ($userId) sin token FCM en caché todavía"
                                )
                            } else {
                                Log.e("BH_DEBUG", "Enviando token al servidor...")
                                val result = firebaseTokenManager.saveToken(token)
                                if (result.isSuccess) {
                                    Log.e("BH_DEBUG", "✅ Token FCM sincronizado con Supabase")
                                } else {
                                    Log.e("BH_DEBUG", "⚠️ Token FCM no sincronizado")
                                }
                            }
                        }
                    }

                    is SessionStatus.NotAuthenticated -> {
                        lastTokenSyncedUserId = null
                        Log.e("BH_DEBUG", "👤 Sesión no autenticada: token FCM pendiente de sincronizar")
                    }

                    else -> Unit
                }
            }
        }
    }
}
