package com.app.buscandohuellas.ui.screens.register

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.RegisterUser
import com.app.buscandohuellas.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Código de Deep Links eliminado - confirmación de email desactivada en Supabase
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()

    fun onFullNameChange(value: String) {
        _uiState.update {
            it.copy(
                fullName = value,
                fullNameError = if (it.hasAttemptedSubmit) validateFullName(value) else null,
                status = it.status.withoutError()
            )
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = if (it.hasAttemptedSubmit) validateEmail(value) else null,
                status = it.status.withoutError()
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = if (it.hasAttemptedSubmit) validatePassword(value) else null,
                confirmPasswordError = if (it.hasAttemptedSubmit) {
                    validateConfirmPassword(it.confirmPassword, value)
                } else {
                    it.confirmPasswordError
                },
                status = it.status.withoutError()
            )
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                confirmPassword = value,
                confirmPasswordError = if (it.hasAttemptedSubmit) {
                    validateConfirmPassword(value, it.password)
                } else {
                    null
                },
                status = it.status.withoutError()
            )
        }
    }

    fun onRegister() {
        if (_uiState.value.status is RegisterStatus.Loading) return

        _uiState.update { it.copy(hasAttemptedSubmit = true) }
        val state = _uiState.value

        val fullNameError = validateFullName(state.fullName)
        val emailError = validateEmail(state.email)
        val passwordError = validatePassword(state.password)
        val confirmPasswordError = validateConfirmPassword(state.confirmPassword, state.password)

        if (fullNameError != null || emailError != null || passwordError != null ||
            confirmPasswordError != null
        ) {
            _uiState.update {
                it.copy(
                    fullNameError = fullNameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(status = RegisterStatus.Loading) }
            authRepository.registerUser(
                RegisterUser(
                    fullName = state.fullName.trim(),
                    email = state.email.trim(),
                    password = state.password,
                    confirmPassword = state.confirmPassword
                )
            )
                .onSuccess { user ->
                    _uiState.update { it.copy(status = RegisterStatus.Success(user)) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            status = RegisterStatus.Error(
                                error.message
                                    ?: "No se pudo crear la cuenta. Inténtalo de nuevo."
                            )
                        )
                    }
                }
        }
    }

    private fun validateFullName(value: String): String? = when {
        value.trim().isEmpty() -> "Ingresa tu nombre completo."
        value.trim().length < 3 -> "El nombre debe tener al menos 3 caracteres."
        else -> null
    }

    private fun validateEmail(value: String): String? = when {
        value.trim().isEmpty() -> "El correo electrónico es obligatorio."
        !Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches() ->
            "Ingresa un correo electrónico válido."

        else -> null
    }

    private fun validatePassword(value: String): String? = when {
        value.isEmpty() -> "La contraseña es obligatoria."
        value.length < RegisterUser.MIN_PASSWORD_LENGTH ->
            "Debe tener al menos ${RegisterUser.MIN_PASSWORD_LENGTH} caracteres."

        else -> null
    }

    private fun validateConfirmPassword(confirm: String, password: String): String? = when {
        confirm.isEmpty() -> "Confirma tu contraseña."
        confirm != password -> "Las contraseñas no coinciden."
        else -> null
    }

    // Limpia el mensaje de error al editar un campo, pero conserva el estado
    // Loading o el Success de un registro ya completado.
    private fun RegisterStatus.withoutError(): RegisterStatus =
        if (this is RegisterStatus.Error) {
            RegisterStatus.Idle
        } else {
            this
        }
}
