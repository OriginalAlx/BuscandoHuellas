package com.app.buscandohuellas.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.buscandohuellas.MainActivity
import com.app.buscandohuellas.R
import com.app.buscandohuellas.ui.theme.BrandColors
import com.app.buscandohuellas.util.FirebaseTokenManager
import com.app.buscandohuellas.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tag único de diagnóstico: filtrar Logcat por "BH_DEBUG". */
private const val TAG = "BH_DEBUG"

/**
 * Servicio de Firebase Cloud Messaging.
 *
 * Recibe los push enviados por la Edge Function / backend y:
 * 1. Guarda el token FCM del dispositivo en Supabase cuando cambia.
 * 2. Construye y muestra una notificación local con el estilo de la app.
 * 3. Abre `MainActivity` con el `report_id` como extra para navegar al detalle.
 *
 * Nota: si el backend envía un payload con bloque `notification`, Android
 * muestra la notificación automáticamente cuando la app está en segundo plano
 * y NO se llama a [onMessageReceived]. Para tener control total del texto,
 * icono y deep link, el backend debe enviar un mensaje **data-only** con las
 * claves `title`, `body` y `report_id`.
 */
@AndroidEntryPoint
class BuscandoHuellasFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var firebaseTokenManager: FirebaseTokenManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    /**
     * Scope propio del servicio: no depende de la UI. FCM puede destruir el
     * servicio después de `onMessageReceived`, por lo que las operaciones
     * cortas usan este scope y se completan con `goAsync`-like semantics.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Se invoca cuando Firebase emite un token nuevo (primer arranque,
     * reinstalación, restauración de backup o rotación del token).
     *
     * En firebase-messaging 26.x este callback está obsoleto en favor de
     * [onRegistered], pero se mantiene porque sigue siendo el que usa la ruta
     * legacy (la habilitada por defecto). El warning de deprecación se
     * silencia de forma intencionada.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        Log.e(TAG, "🔑 onNewToken (API legacy): nuevo token FCM recibido")
        Log.e(TAG, "🔑 Token: $token")
        syncTokenWithServer(token, source = "onNewToken")
    }

    /**
     * Nuevo callback de firebase-messaging 26.x. Solo se invoca cuando la
     * "V1 registration" está habilitada mediante el metadato
     * `firebase_messaging_installation_id_enabled`; se implementa para que la
     * app funcione igual si en el futuro se activa esa ruta.
     */
    fun onRegistered(token: String) {
        Log.e(TAG, "🔑 onRegistered (API v1): token FCM registrado")
        Log.e(TAG, "🔑 Token: $token")
        syncTokenWithServer(token, source = "onRegistered")
    }

    /**
     * Se invoca cuando la "V1 registration" invalida el token del dispositivo.
     * Se elimina del servidor para dejar de enviar push a un token muerto.
     */
    fun onUnregistered(token: String) {
        Log.e(TAG, "🔑 onUnregistered (API v1): token FCM invalidado")
        serviceScope.launch {
            firebaseTokenManager.deleteToken(token)
        }
    }

    /** Sincroniza con Supabase el token recibido por cualquiera de las APIs. */
    private fun syncTokenWithServer(token: String, source: String) {
        serviceScope.launch {
            val result = firebaseTokenManager.saveToken(token)
            if (result.isSuccess) {
                Log.e(TAG, "✅ $source: token sincronizado con Supabase")
            } else {
                Log.e(TAG, "⚠️ $source: no se pudo sincronizar (¿sin sesión?)")
            }
        }
    }

    /**
     * Se invoca cuando llega un mensaje data-only (o en primer plano si el
     * payload trae `notification`).
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.e(TAG, "📩 onMessageReceived: mensaje FCM recibido")
        Log.e(TAG, "📩 from=${remoteMessage.from}")
        Log.e(TAG, "📩 data=${remoteMessage.data}")

        val data = remoteMessage.data
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: "Nuevo reporte cerca de ti"
        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: "Hay un nuevo reporte de mascota. Toca para ver los detalles."
        val reportId = data["report_id"]
            ?: data["reportId"]
            ?: ""

        Log.e(TAG, "📩 Título: $title")
        Log.e(TAG, "📩 Cuerpo: $body")
        Log.e(TAG, "📩 report_id: ${reportId.ifBlank { "(vacío)" }}")

        // El canal debe existir antes de publicar (idempotente).
        notificationHelper.createNotificationChannel()

        val notificationId = if (reportId.isNotBlank()) {
            reportId.hashCode()
        } else {
            System.currentTimeMillis().toInt()
        }

        // PendingIntent que abre MainActivity y entrega el report_id.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (reportId.isNotBlank()) {
                putExtra(NotificationHelper.EXTRA_REPORT_ID, reportId)
            }
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(BrandColors.NotificationPrimary.toArgb())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        if (!hasNotificationPermission()) {
            Log.e(TAG, "❌ Notificación FCM NO mostrada: permiso POST_NOTIFICATIONS denegado")
            return
        }

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
            Log.e(TAG, "🔔 NotificationManager.notify ejecutado para reportId: $reportId")
        } catch (error: SecurityException) {
            Log.e(TAG, "❌ Error al mostrar la notificación FCM", error)
        }
    }

    /** Comprueba el permiso de notificaciones en Android 13+. */
    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}
