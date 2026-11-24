package com.app.buscandohuellas.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.buscandohuellas.MainActivity
import com.app.buscandohuellas.R
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.ui.utils.LocationUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BH_DEBUG"

/**
 * Crea el canal de notificaciones y muestra avisos locales de reportes
 * cercanos.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Crea el canal "Reportes cercanos" con importancia alta (idempotente). */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)

        Log.e(TAG, "🔔 Canal de notificación creado: $CHANNEL_ID")
    }

    /**
     * Muestra la notificación de un reporte nuevo.
     *
     * @param distanceKm distancia al usuario, o `null` cuando el radio es
     * ilimitado o no se pudo calcular.
     */
    fun showNearbyReportNotification(report: Report, distanceKm: Double?) {
        val reportId = report.id?.takeIf { it.isNotBlank() } ?: return
        createNotificationChannel()

        val title = when (report.status) {
            ReportStatus.LOST -> "Mascota perdida cerca de ti"
            ReportStatus.SIGHTED -> "Mascota avistada cerca de ti"
            ReportStatus.FOUND -> "Mascota encontrada cerca de ti"
            ReportStatus.RESOLVED -> "Caso resuelto cerca de ti"
        }

        val contentText = buildString {
            append(report.petName)
            if (distanceKm != null) {
                append(" · ")
                append(LocationUtils.formatDistance(distanceKm))
            }
        }

        val description = report.description.trim()
            .ifBlank { "Toca para ver los detalles del reporte." }

        val notificationId = reportId.hashCode()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REPORT_ID, reportId)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n$description")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        Log.e(TAG, "📱 Mostrando notificación: id=$notificationId, title=$title")

        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            Log.e(TAG, "❌ Notificación NO mostrada: permiso POST_NOTIFICATIONS denegado")
            return
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.e(TAG, "🔔 NotificationManager.notify ejecutado para reportId: $reportId")
        } catch (error: SecurityException) {
            Log.e(TAG, "ERROR al mostrar la notificación", error)
        }
    }

    /**
     * Notificación de prueba independiente de Realtime. Sirve para validar
     * permisos, canal y sistema de notificaciones.
     */
    fun sendTestNotification(context: Context) {
        createNotificationChannel()

        val notificationId = TEST_NOTIFICATION_ID
        val title = "Buscando Huellas 🧪"
        val message =
            "🧪 Notificación de prueba - Si ves esto, el sistema de notificaciones funciona correctamente"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        Log.e(TAG, "📱 Mostrando notificación: id=$notificationId, title=$title")

        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            Log.e(TAG, "❌ Notificación de prueba NO mostrada: permiso denegado")
            return
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.e(TAG, "📱 NotificationManager.notify() ejecutado (prueba)")
        } catch (error: SecurityException) {
            Log.e(TAG, "ERROR al mostrar la notificación de prueba", error)
        }
    }

    /** Indica si la app puede publicar notificaciones en este dispositivo. */
    fun hasNotificationPermission(): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Lanza la solicitud del permiso de notificaciones en Android 13+ si aún
     * no fue concedido.
     */
    fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission()) return
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val CHANNEL_ID = "reportes_cercanos"
        const val CHANNEL_NAME = "Reportes cercanos"
        const val CHANNEL_DESCRIPTION =
            "Avisos de mascotas perdidas, avistadas o encontradas cerca de ti."
        const val EXTRA_REPORT_ID = "report_id"
        private const val TEST_NOTIFICATION_ID = 990_001
    }
}
