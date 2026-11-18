package com.app.buscandohuellas.data

import com.app.buscandohuellas.domain.model.ReportStatus

/**
 * Radios disponibles para las notificaciones de reportes cercanos.
 *
 * [valueKm] es el valor numérico que se persiste en DataStore; es `null` para
 * la opción "Todas las distancias" (sin límite).
 */
enum class NotificationRadius(
    val valueKm: Double?,
    val displayName: String,
    val subtitle: String
) {
    ONE_KM(
        valueKm = 1.0,
        displayName = "1 kilómetro",
        subtitle = "Muy cerca de ti"
    ),
    THREE_KM(
        valueKm = 3.0,
        displayName = "3 kilómetros",
        subtitle = "Cerca de ti"
    ),
    FIVE_KM(
        valueKm = 5.0,
        displayName = "5 kilómetros",
        subtitle = "A tu alrededor"
    ),
    TEN_KM(
        valueKm = 10.0,
        displayName = "10 kilómetros",
        subtitle = "Zona amplia"
    ),
    ALL(
        valueKm = null,
        displayName = "Todas las distancias",
        subtitle = "Cualquier distancia"
    );

    val isUnlimited: Boolean
        get() = valueKm == null

    companion object {
        /**
         * Convierte el valor guardado en DataStore al enum correspondiente.
         * Cualquier valor negativo o desconocido cae en [ALL].
         */
        fun fromKm(valueKm: Double?): NotificationRadius {
            if (valueKm == null || valueKm < 0.0) return ALL
            return entries.firstOrNull { it.valueKm == valueKm } ?: ALL
        }
    }
}

/**
 * Preferencias de notificaciones de reportes cercanos.
 *
 * @param radius radio seleccionado. [NotificationRadius.ALL] representa "sin
 * límite".
 * @param notifyLost avisar de mascotas perdidas.
 * @param notifySighted avisar de mascotas avistadas.
 * @param notifyFound avisar de mascotas encontradas.
 */
data class NotificationPreferences(
    val enabled: Boolean = false,
    val radius: NotificationRadius = NotificationRadius.ALL,
    val notifyLost: Boolean = true,
    val notifySighted: Boolean = true,
    val notifyFound: Boolean = true
) {

    /** Valor numérico del radio, o `null` si es ilimitado. */
    val radiusKm: Double?
        get() = radius.valueKm

    fun isStatusEnabled(status: ReportStatus): Boolean = when (status) {
        ReportStatus.LOST -> notifyLost
        ReportStatus.SIGHTED -> notifySighted
        ReportStatus.FOUND -> notifyFound
    }
}
