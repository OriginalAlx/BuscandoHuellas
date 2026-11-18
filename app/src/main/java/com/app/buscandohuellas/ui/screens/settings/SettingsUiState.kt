package com.app.buscandohuellas.ui.screens.settings

import com.app.buscandohuellas.data.model.AppTheme

/**
 * Estado de la pantalla de Configuración.
 *
 * [deleteConfirmationText] debe coincidir exactamente con "ELIMINAR" para
 * habilitar el borrado de la cuenta; [isAccountDeleted] avisa a la UI de que
 * ya puede navegar al login.
 */
data class SettingsUiState(
    val selectedTheme: AppTheme = AppTheme.SYSTEM,
    val isDeletingAccount: Boolean = false,
    val deleteConfirmationText: String = "",
    val error: String? = null,
    val isAccountDeleted: Boolean = false,
    val isLoadingTheme: Boolean = true
) {

    /** Solo se permite borrar con la confirmación exacta y sin otra operación en curso. */
    val canDeleteAccount: Boolean
        get() = deleteConfirmationText == DELETE_CONFIRMATION && !isDeletingAccount

    companion object {
        const val DELETE_CONFIRMATION = "ELIMINAR"
    }
}
