package com.app.buscandohuellas.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReportReasonTest {

    @Test
    fun exposesExactlyTheSixAllowedReasons() {
        assertEquals(6, ReportReason.entries.size)

        assertEquals(
            listOf(
                "SPAM_DUPLICATE",
                "FAKE_SCAM",
                "INCORRECT_INFO",
                "INAPPROPRIATE",
                "PRIVATE_INFO",
                "OTHER"
            ),
            ReportReason.entries.map { it.value }
        )
    }

    @Test
    fun exposesSpanishDisplayNames() {
        assertEquals("Es spam o está duplicado", ReportReason.SPAM_DUPLICATE.displayName)
        assertEquals("Es falso o parece una estafa", ReportReason.FAKE_SCAM.displayName)
        assertEquals("La información es incorrecta", ReportReason.INCORRECT_INFO.displayName)
        assertEquals("Contenido inapropiado u ofensivo", ReportReason.INAPPROPRIATE.displayName)
        assertEquals("Contiene información privada", ReportReason.PRIVATE_INFO.displayName)
        assertEquals("Otro motivo", ReportReason.OTHER.displayName)
    }

    @Test
    fun doesNotIncludeAlreadyFoundReason() {
        assertFalse(
            ReportReason.entries.any { reason ->
                reason.value.contains("FOUND", ignoreCase = true) ||
                    reason.displayName.contains("encontrada", ignoreCase = true)
            }
        )
    }
}
