package com.app.buscandohuellas.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.data.ThemePreferencesRepository
import com.app.buscandohuellas.data.model.AppTheme
import com.app.buscandohuellas.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

/**
 * ViewModel de la pantalla de Configuración.
 *
 * Gestiona el tema persistido en DataStore y la eliminación de cuenta.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Carga el tema guardado al entrar a la pantalla. */
    fun loadSettings() {
        viewModelScope.launch {
            val theme = themePreferencesRepository.theme.first()
            _uiState.update { current ->
                current.copy(selectedTheme = theme, isLoadingTheme = false)
            }
        }
    }

    /** Aplica el tema de inmediato en la UI y lo persiste en DataStore. */
    fun updateTheme(theme: AppTheme) {
        _uiState.update { current ->
            current.copy(selectedTheme = theme, error = null)
        }

        viewModelScope.launch {
            try {
                themePreferencesRepository.setTheme(theme)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo guardar el tema", error)
                _uiState.update { current ->
                    current.copy(error = "No se pudo guardar el tema. Inténtalo de nuevo.")
                }
            }
        }
    }

    fun updateDeleteConfirmation(text: String) {
        _uiState.update { current ->
            current.copy(deleteConfirmationText = text, error = null)
        }
    }

    /** Valida la confirmación y elimina la cuenta junto con sus datos. */
    fun deleteAccount() {
        val state = _uiState.value
        if (state.isDeletingAccount) return

        if (state.deleteConfirmationText != SettingsUiState.DELETE_CONFIRMATION) {
            _uiState.update { current ->
                current.copy(error = "Escribe ELIMINAR para confirmar.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isDeletingAccount = true, error = null) }

            accountRepository.deleteAccount()
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            isDeletingAccount = false,
                            isAccountDeleted = true,
                            deleteConfirmationText = "",
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "No se pudo eliminar la cuenta", error)
                    _uiState.update { current ->
                        current.copy(
                            isDeletingAccount = false,
                            error = error.message
                                ?: "No se pudo eliminar la cuenta. Inténtalo de nuevo."
                        )
                    }
                }
        }
    }

    /** Limpia el estado del diálogo después de navegar al login. */
    fun resetDeleteState() {
        _uiState.update { current ->
            current.copy(
                isAccountDeleted = false,
                deleteConfirmationText = "",
                error = null
            )
        }
    }

    /** Limpia el error después de mostrarlo. */
    fun consumeError() {
        _uiState.update { current -> current.copy(error = null) }
    }
}
