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
        assertEquals(ReportStatus.FOUND, ReportStatus.SIGHTED.next())
        assertEquals(ReportStatus.LOST, ReportStatus.FOUND.next())
    }

    @Test
    fun exposesSpanishLabels() {
        assertEquals("PERDIDA", ReportStatus.LOST.label)
        assertEquals("AVISTADA", ReportStatus.SIGHTED.label)
        assertEquals("ENCONTRADA", ReportStatus.FOUND.label)
    }

    @Test
    fun exposesComposeColors() {
        assertEquals(Color(0xFFDC2626), ReportStatus.LOST.textColor)
        assertEquals(Color(0xFFFEE2E2), ReportStatus.LOST.backgroundColor)

        assertEquals(Color(0xFFEA580C), ReportStatus.SIGHTED.textColor)
        assertEquals(Color(0xFFFFEDD5), ReportStatus.SIGHTED.backgroundColor)

        assertEquals(Color(0xFF059669), ReportStatus.FOUND.textColor)
        assertEquals(Color(0xFFD1FAE5), ReportStatus.FOUND.backgroundColor)
    }

    @Test
    fun exposesMapColors() {
        assertEquals(0xFFDC2626.toInt(), ReportStatus.LOST.mapColor)
        assertEquals(0xFFEA580C.toInt(), ReportStatus.SIGHTED.mapColor)
        assertEquals(0xFF059669.toInt(), ReportStatus.FOUND.mapColor)
    }
}
