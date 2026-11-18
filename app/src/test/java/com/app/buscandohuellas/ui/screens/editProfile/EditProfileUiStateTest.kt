package com.app.buscandohuellas.ui.screens.editProfile

import com.app.buscandohuellas.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditProfileUiStateTest {

    private val profile = Profile(
        id = "user-id",
        fullName = "Camila Rodríguez",
        email = "camila@email.com",
        avatarUrl = null
    )

    @Test
    fun rejectsBlankName() {
        val blankName = EditProfileUiState(currentProfile = profile, fullName = "   ")
        val emptyName = EditProfileUiState(currentProfile = profile, fullName = "")

        assertFalse(blankName.isNameValid)
        assertFalse(emptyName.isNameValid)
        assertFalse(blankName.canSave)
        assertFalse(emptyName.canSave)
    }

    @Test
    fun requiresNameChangesToEnableSave() {
        val unchanged = EditProfileUiState(
            currentProfile = profile,
            fullName = "Camila Rodríguez"
        )
        val changed = unchanged.copy(fullName = "Camila R.")

        assertFalse(unchanged.hasChanges)
        assertFalse(unchanged.canSave)
        assertTrue(changed.hasChanges)
        assertTrue(changed.canSave)
    }

    @Test
    fun ignoresAvatarChangesWhenSavingName() {
        // El avatar se persiste al seleccionarlo, así que por sí solo no debe
        // habilitar el botón de guardar nombre.
        val state = EditProfileUiState(
            currentProfile = profile,
            fullName = "Camila Rodríguez",
            avatarUrl = "https://example.com/avatar.png"
        )

        assertFalse(state.hasChanges)
        assertFalse(state.canSave)
    }

    @Test
    fun disablesSaveWhileBusy() {
        val changed = EditProfileUiState(
            currentProfile = profile,
            fullName = "Camila R."
        )

        assertFalse(changed.copy(isSaving = true).canSave)
        assertFalse(changed.copy(isUploadingImage = true).canSave)
    }

    @Test
    fun buildsInitialsFromFullName() {
        val state = EditProfileUiState(fullName = "  camila  rodríguez ")

        assertEquals("CR", state.initials)
        assertEquals("", EditProfileUiState(fullName = "   ").initials)
    }
}
