package com.app.buscandohuellas.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.buscandohuellas.MainActivity
import com.app.buscandohuellas.R
import com.app.buscandohuellas.util.SessionLogoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BH_DEBUG"
private const val CHANNEL_ID = "session_guard"
private const val CHANNEL_NAME = "Sesión segura"
private const val NOTIFICATION_ID = 9001

/**
 * Servicio persistente que detecta cuándo el usuario elimina la app de
 * recientes (`onTaskRemoved`) y cierra la sesión antes de que el proceso muera.
 *
 * Es foreground service porque en Android 8+ un servicio que debe sobrevivir a
 * la task necesita una notificación; por eso el manifiesto declara el tipo
 * `dataSync`. La notificación es de prioridad mínima y no hace ruido.
 *
 * Limitación del SO: el "forzar detención" desde Ajustes mata el proceso sin
 * llamar a `onTaskRemoved()`. En ese caso la sesión queda en Storage, pero
 * Supabase la invalida por expiración/refresh y la app sigue pidiendo login al
 * arrancar si no hay sesión válida.
 */
@AndroidEntryPoint
class AppLifecycleService : Service() {

    @Inject
    lateinit var sessionLogoutManager: SessionLogoutManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== AppLifecycleService onCreate ===")

        try {
            createChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            isRunning = true
            Log.d(TAG, "AppLifecycleService en primer plano (stopWithTask=false)")
        } catch (error: Exception) {
            // Si el sistema rechaza el foreground service, no dejamos el flag
            // activo: el próximo arranque debe poder intentarlo de nuevo.
            isRunning = false
            Log.e(TAG, "No se pudo poner AppLifecycleService en primer plano", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AppLifecycleService onStartCommand (flags=$flags, startId=$startId)")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Se llama al hacer swipe desde recientes o al terminar la task. Con
     * `android:stopWithTask="false"` el servicio sigue vivo el tiempo justo para
     * ejecutar el logout.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "=== AppLifecycleService onTaskRemoved: cerrando sesión ===")

        serviceScope.launch {
            try {
                sessionLogoutManager.logout(reason = "task_removed")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "AppLifecycleService: error inesperado en logout", error)
            } finally {
                Log.d(TAG, "AppLifecycleService: stopSelf()")
                stopSelf()
            }
        }

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        Log.d(TAG, "=== AppLifecycleService onDestroy ===")
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Mantiene la sesión segura cuando sales de la app"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Buscando Huellas")
            .setContentText("Sesión protegida al salir de la app")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        /** Evita iniciar dos veces el mismo servicio durante el arranque. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
