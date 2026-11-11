package com.app.buscandohuellas.ui.screens.reportDetail

import com.app.buscandohuellas.domain.model.Report
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReportDetailFormattingTest {

    private val now = Instant.parse("2026-11-11T15:00:00Z")

    @Test
    fun formatsRecentTimeAsMoment() {
        val createdAt = "2026-11-11T14:59:30Z"
        assertEquals("Hace un momento", formatRelativeTime(createdAt, now))
    }

    @Test
    fun formatsHours() {
        val createdAt = "2026-11-11T13:00:00Z"
        assertEquals("Hace 2 horas", formatRelativeTime(createdAt, now))
    }

    @Test
    fun formatsSingleDay() {
        val createdAt = "2026-11-10T15:00:00Z"
        assertEquals("Hace 1 día", formatRelativeTime(createdAt, now))
    }

    @Test
    fun formatsWeeks() {
        val createdAt = "2026-10-21T15:00:00Z"
        assertEquals("Hace 3 semanas", formatRelativeTime(createdAt, now))
    }

    @Test
    fun parsesOffsetDateTimeFromSupabase() {
        val createdAt = "2026-11-11T12:59:00.716935+00:00"
        assertEquals("Hace 2 horas", formatRelativeTime(createdAt, now))
    }

    @Test
    fun returnsEmptyForMissingDate() {
        assertEquals("", formatRelativeTime(null, now))
    }

    @Test
    fun formatsDistanceInKilometers() {
        assertEquals("A 2.5 km de ti", formatDistanceLabel(2.5, null))
    }

    @Test
    fun formatsDistanceInMeters() {
        assertEquals("A 450 m de ti", formatDistanceLabel(0.45, null))
    }

    @Test
    fun appendsLocationReferenceToDistance() {
        assertEquals(
            "A 2.5 km de ti · Universidad Católica",
            formatDistanceLabel(2.5, "Universidad Católica")
        )
    }

    @Test
    fun locationReferencePrefersLocationName() {
        val report = Report(
            userId = "user",
            petName = "Max",
            description = "",
            location = "POINT(-57.25 -25.78)",
            locationName = "Universidad Católica"
        )

        assertEquals("Universidad Católica", report.locationReference())
    }

    @Test
    fun locationReferenceUsesHumanReadableLocation() {
        val report = Report(
            userId = "user",
            petName = "Max",
            description = "",
            location = "Av. España, Asunción"
        )

        assertEquals("Av. España, Asunción", report.locationReference())
    }

    @Test
    fun locationReferenceIgnoresRawCoordinates() {
        val report = Report(
            userId = "user",
            petName = "Max",
            description = "",
            location = "-25.78000, -57.25000"
        )

        assertEquals(null, report.locationReference())
    }

    @Test
    fun locationReferenceIgnoresWktPoint() {
        val report = Report(
            userId = "user",
            petName = "Max",
            description = "",
            location = "POINT(-57.25 -25.78)"
        )

        assertEquals(null, report.locationReference())
    }
}
