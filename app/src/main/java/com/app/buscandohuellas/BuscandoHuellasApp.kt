package com.app.buscandohuellas

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.app.buscandohuellas.data.NotificationPreferencesRepository
import com.app.buscandohuellas.service.AppLifecycleService
import com.app.buscandohuellas.service.ReportRealtimeService
import com.app.buscandohuellas.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application de la app.
 *
 * Importante: NO detenemos [ReportRealtimeService] desde el ciclo de vida de
 * la UI. El servicio es un singleton con su propio scope raíz y debe seguir
 * escuchando aunque la Activity se destruya o la app pase a segundo plano.
 */
@HiltAndroidApp
class BuscandoHuellasApp : Application() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var realtimeService: ReportRealtimeService

    @Inject
    lateinit var preferencesRepository: NotificationPreferencesRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.e("BH_DEBUG", "=== Application onCreate: Hilt inyectó correctamente ===")
        notificationHelper.createNotificationChannel()
        startAppLifecycleService()
        observeNotificationPreferences()
        observeProcessLifecycle()
    }

    /**
     * Arranca el servicio que vigila `onTaskRemoved`. El flag estático evita
     * duplicados si el proceso se recrea dentro de la misma sesión; al morir el
     * proceso el flag vuelve a false y el servicio se inicia de nuevo.
     */
    private fun startAppLifecycleService() {
        if (AppLifecycleService.isRunning) {
            Log.e("BH_DEBUG", "AppLifecycleService ya está corriendo; no se reinicia")
            return
        }

        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AppLifecycleService::class.java)
            )
            Log.e("BH_DEBUG", "=== AppLifecycleService iniciado desde Application ===")
        } catch (error: Exception) {
            Log.e("BH_DEBUG", "No se pudo iniciar AppLifecycleService", error)
        }
    }

    /**
     * Arranca la escucha cuando las notificaciones se activan. Si se
     * desactivan, NO se cancela la suscripción: [ReportRealtimeService]
     * descarta los eventos en `handleInsert` con el motivo
     * "notificaciones desactivadas". Así la escucha sobrevive a cambios de
     * preferencias sin fugas ni reinicios innecesarios.
     */
    private fun observeNotificationPreferences() {
        applicationScope.launch {
            preferencesRepository.preferences
                .map { it.enabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    Log.e("BH_DEBUG", "Preferencia notifications_enabled=$enabled")
                    if (enabled) {
                        realtimeService.startListening()
                    } else {
                        Log.e(
                            "BH_DEBUG",
                            "Notificaciones desactivadas: la escucha continúa y los " +
                                "eventos se descartarán en handleInsert"
                        )
                    }
                }
        }
    }

    /**
     * Asegura la suscripción al volver a primer plano. En segundo plano NO se
     * detiene: el scope del servicio es independiente del ciclo de vida de la
     * UI y debe seguir vivo para recibir notificaciones.
     */
    private fun observeProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    Log.e("BH_DEBUG", "=== ProcessLifecycleOwner onStart: asegurando suscripción ===")
                    realtimeService.startListening()
                }

                override fun onStop(owner: LifecycleOwner) {
                    // Intencionalmente NO se llama a stop(): la suscripción es
                    // persistente e independiente del ciclo de vida de la UI.
                    Log.e(
                        "BH_DEBUG",
                        "=== ProcessLifecycleOwner onStop: la suscripción continúa ==="
                    )
                }
            }
        )
    }
}
