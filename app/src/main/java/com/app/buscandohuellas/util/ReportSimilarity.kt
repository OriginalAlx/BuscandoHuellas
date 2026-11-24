package com.app.buscandohuellas.util

import com.app.buscandohuellas.domain.model.Report
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ReportMatchCandidate(
    val report: Report,
    val distanceKm: Double?,
    val similarityScore: Int
)

object ReportSimilarity {

    fun calculate(
        found: Report,
        lost: Report
    ): ReportMatchCandidate {
        var score = 0

        val foundName = found.petName.trim().lowercase(Locale.getDefault())
        val lostName = lost.petName.trim().lowercase(Locale.getDefault())

        if (foundName.isNotBlank() && lostName.isNotBlank()) {
            if (foundName == lostName) {
                score += 40
            } else if (foundName.contains(lostName) || lostName.contains(foundName)) {
                score += 25
            }
        }

        val foundTokens = keywords(found)
        val lostTokens = keywords(lost)
        if (foundTokens.isNotEmpty() && lostTokens.isNotEmpty()) {
            val common = foundTokens.intersect(lostTokens)
            if (common.isNotEmpty()) score += 20
        }

        val distanceKm = distanceKm(found, lost)
        if (distanceKm != null) {
            when {
                distanceKm < 1.0 -> score += 10
                distanceKm < 3.0 -> score += 5
            }
        }

        val ageHours = ageHours(lost.createdAt)
        if (ageHours != null) {
            when {
                ageHours < 24 -> score += 10
                ageHours < 24 * 7 -> score += 5
            }
        }

        return ReportMatchCandidate(
            report = lost,
            distanceKm = distanceKm,
            similarityScore = score.coerceIn(0, 100)
        )
    }

    fun distanceKm(a: Report, b: Report): Double? {
        val lat1 = a.latitude ?: return null
        val lon1 = a.longitude ?: return null
        val lat2 = b.latitude ?: return null
        val lon2 = b.longitude ?: return null
        return haversineKm(lat1, lon1, lat2, lon2)
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun keywords(report: Report): Set<String> {
        val text = "${report.petName} ${report.description}"
            .lowercase(Locale.getDefault())
        return text.split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .toSet()
    }

    private fun ageHours(createdAt: String?): Long? {
        val instant = parseCreatedAt(createdAt) ?: return null
        val now = Instant.now()
        val diffMs = now.toEpochMilli() - instant.toEpochMilli()
        return if (diffMs < 0) 0L else diffMs / (1000L * 60L * 60L)
    }

    private fun parseCreatedAt(createdAt: String?): Instant? {
        if (createdAt.isNullOrBlank()) return null
        val value = createdAt.trim()
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
