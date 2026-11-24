package com.app.buscandohuellas.domain.model

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportStatusTest {

    @Test
    fun parsesKnownValuesIgnoringCaseAndWhitespace() {
        assertEquals(ReportStatus.LOST, ReportStatus.fromString("lost"))
        assertEquals(ReportStatus.SIGHTED, ReportStatus.fromString(" SIGHTED "))
        assertEquals(ReportStatus.FOUND, ReportStatus.fromString("Found"))
        assertEquals(ReportStatus.RESOLVED, ReportStatus.fromString(" resolved "))
    }

    @Test
    fun fallsBackToLostForUnknownOrBlankValues() {
        assertEquals(ReportStatus.LOST, ReportStatus.fromString("UNKNOWN"))
        assertEquals(ReportStatus.LOST, ReportStatus.fromString(null))
        assertEquals(ReportStatus.LOST, ReportStatus.fromString(""))
    }

    @Test
    fun rotatesThroughStates() {
        assertEquals(ReportStatus.SIGHTED, ReportStatus.LOST.next())
        assertEquals(ReportStatus.RESOLVED, ReportStatus.SIGHTED.next())
        assertEquals(ReportStatus.RESOLVED, ReportStatus.FOUND.next())
        assertEquals(ReportStatus.LOST, ReportStatus.RESOLVED.next())
    }

    @Test
    fun exposesSpanishLabels() {
        assertEquals("PERDIDA", ReportStatus.LOST.label)
        assertEquals("AVISTADA", ReportStatus.SIGHTED.label)
        assertEquals("ENCONTRADA", ReportStatus.FOUND.label)
        assertEquals("RESUELTA", ReportStatus.RESOLVED.label)
    }

    @Test
    fun exposesComposeColorsForBothThemes() {
        assertEquals(Color(0xFFDC2626), ReportStatus.LOST.lightTextColor)
        assertEquals(Color(0xFFFEE2E2), ReportStatus.LOST.lightBackgroundColor)
        assertEquals(Color(0xFFF87171), ReportStatus.LOST.darkTextColor)
        assertEquals(Color(0xFF4A1D1D), ReportStatus.LOST.darkBackgroundColor)

        assertEquals(Color(0xFFEA580C), ReportStatus.SIGHTED.lightTextColor)
        assertEquals(Color(0xFFFFEDD5), ReportStatus.SIGHTED.lightBackgroundColor)
        assertEquals(Color(0xFFFB923C), ReportStatus.SIGHTED.darkTextColor)
        assertEquals(Color(0xFF4A2A12), ReportStatus.SIGHTED.darkBackgroundColor)

        assertEquals(Color(0xFF059669), ReportStatus.FOUND.lightTextColor)
        assertEquals(Color(0xFFD1FAE5), ReportStatus.FOUND.lightBackgroundColor)
        assertEquals(Color(0xFF34D399), ReportStatus.FOUND.darkTextColor)
        assertEquals(Color(0xFF123B2C), ReportStatus.FOUND.darkBackgroundColor)

        assertEquals(Color(0xFF6D5BD0), ReportStatus.RESOLVED.lightTextColor)
        assertEquals(Color(0xFFEDE9FE), ReportStatus.RESOLVED.lightBackgroundColor)
        assertEquals(Color(0xFFC4B5FD), ReportStatus.RESOLVED.darkTextColor)
        assertEquals(Color(0xFF312E81), ReportStatus.RESOLVED.darkBackgroundColor)
    }

    @Test
    fun exposesMapColors() {
        assertEquals(0xFFDC2626.toInt(), ReportStatus.LOST.mapColor)
        assertEquals(0xFFEA580C.toInt(), ReportStatus.SIGHTED.mapColor)
        assertEquals(0xFF059669.toInt(), ReportStatus.FOUND.mapColor)
        assertEquals(0xFF8B7CFF.toInt(), ReportStatus.RESOLVED.mapColor)
    }
}
