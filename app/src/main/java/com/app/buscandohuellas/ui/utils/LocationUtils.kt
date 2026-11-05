package com.app.buscandohuellas.ui.utils

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt


object LocationUtils {

    private const val EARTH_RADIUS_KM = 6371.0

    fun calculateDistance(
        userLat: Double,
        userLon: Double,
        reportLat: Double,
        reportLon: Double
    ): Double {
        val deltaLat = Math.toRadians(reportLat - userLat)
        val deltaLon = Math.toRadians(reportLon - userLon)
        val userLatRad = Math.toRadians(userLat)
        val reportLatRad = Math.toRadians(reportLat)

        val a = sin(deltaLat / 2).pow(2) +
                cos(userLatRad) * cos(reportLatRad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).roundToInt()} m"
        } else {
            String.format(Locale.US, "%.1f km", distanceKm)
        }
    }
}
