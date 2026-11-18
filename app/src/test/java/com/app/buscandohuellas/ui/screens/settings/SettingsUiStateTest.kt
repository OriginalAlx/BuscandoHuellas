package com.app.buscandohuellas.ui.screens.settings

import com.app.buscandohuellas.data.model.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiStateTest {

    @Test
    fun requiresExactDeleteConfirmation() {
        assertFalse(SettingsUiState(deleteConfirmationText = "").canDeleteAccount)
        assertFalse(SettingsUiState(deleteConfirmationText = "eliminar").canDeleteAccount)
        assertFalse(SettingsUiState(deleteConfirmationText = "ELIMINAR ").canDeleteAccount)
        assertTrue(SettingsUiState(deleteConfirmationText = "ELIMINAR").canDeleteAccount)
    }

    @Test
    fun blocksDeleteWhileDeleting() {
        val state = SettingsUiState(
            deleteConfirmationText = SettingsUiState.DELETE_CONFIRMATION,
            isDeletingAccount = true
        )

        assertFalse(state.canDeleteAccount)
    }

    @Test
    fun themeStorageFallsBackToSystem() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStorage(null))
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStorage("UNKNOWN"))
        assertEquals(AppTheme.LIGHT, AppTheme.fromStorage("LIGHT"))
        assertEquals(AppTheme.DARK, AppTheme.fromStorage("DARK"))
    }
}
