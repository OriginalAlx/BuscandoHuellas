package com.app.buscandohuellas.data

import com.app.buscandohuellas.domain.model.ReportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRadiusTest {

    @Test
    fun exposesTheFiveRadiusOptions() {
        assertEquals(5, NotificationRadius.entries.size)
        assertEquals(
            listOf(1.0, 3.0, 5.0, 10.0, null),
            NotificationRadius.entries.map { it.valueKm }
        )
    }

    @Test
    fun unlimitedOptionHasNoNumericValue() {
        assertTrue(NotificationRadius.ALL.isUnlimited)
        assertNull(NotificationRadius.ALL.valueKm)
        assertFalse(NotificationRadius.ONE_KM.isUnlimited)
    }

    @Test
    fun mapsPersistedValuesAndSentinel() {
        assertEquals(NotificationRadius.ONE_KM, NotificationRadius.fromKm(1.0))
        assertEquals(NotificationRadius.THREE_KM, NotificationRadius.fromKm(3.0))
        assertEquals(NotificationRadius.FIVE_KM, NotificationRadius.fromKm(5.0))
        assertEquals(NotificationRadius.TEN_KM, NotificationRadius.fromKm(10.0))
        assertEquals(NotificationRadius.ALL, NotificationRadius.fromKm(-1.0))
        assertEquals(NotificationRadius.ALL, NotificationRadius.fromKm(null))
        assertEquals(NotificationRadius.ALL, NotificationRadius.fromKm(7.0))
    }

    @Test
    fun statusFiltersMatchPreferences() {
        val preferences = NotificationPreferences(
            notifyLost = true,
            notifySighted = false,
            notifyFound = true
        )

        assertTrue(preferences.isStatusEnabled(ReportStatus.LOST))
        assertFalse(preferences.isStatusEnabled(ReportStatus.SIGHTED))
        assertTrue(preferences.isStatusEnabled(ReportStatus.FOUND))
    }
}
