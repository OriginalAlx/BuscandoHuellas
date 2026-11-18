package com.app.buscandohuellas.ui.screens.editProfile

import com.app.buscandohuellas.domain.model.Profile

/**
 * Estado del formulario de edición de perfil.
 *
 * [currentProfile] guarda el último perfil confirmado por el servidor y se usa
 * para calcular [hasChanges] y para saber si el nombre realmente cambió.
 */
data class EditProfileUiState(
    val currentProfile: Profile? = null,
    val fullName: String = "",
    val avatarUrl: String? = null,
    /** URI local mostrada mientras la nueva foto se sube a Storage. */
    val avatarPreviewUri: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
) {

    /** El nombre no puede quedar vacío ni contener solo espacios. */
    val isNameValid: Boolean
        get() = fullName.trim().isNotEmpty()

    /** Hay un nombre distinto al confirmado por el servidor. */
    val hasChanges: Boolean
        get() {
            val profile = currentProfile ?: return false
            return fullName.trim() != profile.fullName.orEmpty().trim()
        }

    /** El botón de guardar solo se habilita con datos válidos y cambios reales. */
    val canSave: Boolean
        get() = !isSaving && !isUploadingImage && isNameValid && hasChanges

    /** Iniciales usadas como respaldo cuando el usuario no tiene foto. */
    val initials: String
        get() = fullName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
}
