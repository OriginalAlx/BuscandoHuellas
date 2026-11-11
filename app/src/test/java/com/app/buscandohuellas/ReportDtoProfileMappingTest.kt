package com.app.buscandohuellas.data.dto

import com.app.buscandohuellas.domain.model.Report
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportDtoProfileMappingTest {

    private fun dto(
        profile: ProfileDto? = null,
        userName: String? = null,
        userAvatarUrl: String? = null,
        locationText: String? = null,
        rawLocation: String? = null
    ) = ReportDto(
        id = "report-id",
        userId = "user-id",
        petName = "Max",
        description = "Perro perdido",
        status = "LOST",
        locationText = locationText,
        rawLocation = rawLocation,
        userName = userName,
        userAvatarUrl = userAvatarUrl,
        profile = profile
    )

    @Test
    fun mapsEmbeddedProfileNameAndAvatar() {
        val report = dto(
            profile = ProfileDto(
                fullName = "Ana Pérez",
                avatarUrl = "https://example.com/ana.png"
            )
        ).toDomain()

        assertEquals("Ana Pérez", report.userName)
        assertEquals("https://example.com/ana.png", report.userAvatarUrl)
    }

    @Test
    fun embeddedProfileWinsOverLegacyColumns() {
        val report = dto(
            profile = ProfileDto(fullName = "Ana Pérez", avatarUrl = "https://example.com/a.png"),
            userName = "Nombre viejo",
            userAvatarUrl = "https://example.com/old.png"
        ).toDomain()

        assertEquals("Ana Pérez", report.userName)
        assertEquals("https://example.com/a.png", report.userAvatarUrl)
    }

    @Test
    fun fallsBackToLegacyUserNameColumns() {
        val report = dto(
            userName = "Luis Gómez",
            userAvatarUrl = "https://example.com/luis.png"
        ).toDomain()

        assertEquals("Luis Gómez", report.userName)
        assertEquals("https://example.com/luis.png", report.userAvatarUrl)
    }

    @Test
    fun fallsBackToCommunityNameWhenProfileIsMissingOrBlank() {
        val missingProfile = dto().toDomain()
        val blankProfile = dto(profile = ProfileDto(fullName = "   ", avatarUrl = "  ")).toDomain()

        assertEquals(Report.DEFAULT_USER_NAME, missingProfile.userName)
        assertEquals(Report.DEFAULT_USER_NAME, blankProfile.userName)
        assertNull(missingProfile.userAvatarUrl)
        assertNull(blankProfile.userAvatarUrl)
    }

    @Test
    fun parsesWktLocation() {
        val report = dto(locationText = "POINT(-57.250217 -25.780673)").toDomain()

        assertEquals(-57.250217, report.longitude ?: Double.NaN, 0.000001)
        assertEquals(-25.780673, report.latitude ?: Double.NaN, 0.000001)
    }

    @Test
    fun parsesEwkbLocationFromBaseTable() {
        val report = dto(
            rawLocation = "0101000020E61000006E1D9F1D07A04CC08027632BDAC739C0"
        ).toDomain()

        assertEquals(-57.25021715420995, report.longitude ?: Double.NaN, 0.0000001)
        assertEquals(-25.78067275210151, report.latitude ?: Double.NaN, 0.0000001)
    }

    @Test
    fun neverKeepsRawEwkbAsHumanReadableLocation() {
        val report = dto(
            rawLocation = "0101000020E61000006E1D9F1D07A04CC08027632BDAC739C0"
        ).toDomain()

        assertNull(report.location)
    }
}
