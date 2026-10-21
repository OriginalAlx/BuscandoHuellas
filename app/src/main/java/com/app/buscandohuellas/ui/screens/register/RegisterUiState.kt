package com.app.buscandohuellas.ui.screens.register

import com.app.buscandohuellas.domain.model.User

// Código de Deep Links eliminado - confirmación de email desactivada en Supabase
sealed interface RegisterStatus {
    data object Idle : RegisterStatus
    data object Loading : RegisterStatus
    data class Success(val user: User) : RegisterStatus
    data class Error(val message: String) : RegisterStatus
}

enum class PasswordStrength {
    WEAK,
    MEDIUM,
    STRONG,
    EXCELLENT;

    companion object {
        fun from(password: String): PasswordStrength {
            if (password.isEmpty()) return WEAK
            var score = 0
            if (password.length >= 8) score++
            if (password.any { it.isUpperCase() }) score++
            if (password.any { it.isDigit() }) score++
            if (password.any { !it.isLetterOrDigit() }) score++
            return when {
                score >= 4 -> EXCELLENT
                score == 3 -> STRONG
                score == 2 -> MEDIUM
                else -> WEAK
            }
        }
    }
}

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val status: RegisterStatus = RegisterStatus.Idle,
    val hasAttemptedSubmit: Boolean = false
) {
    val isLoading: Boolean get() = status is RegisterStatus.Loading
    val errorMessage: String? get() = (status as? RegisterStatus.Error)?.message
    val registeredUser: User? get() = (status as? RegisterStatus.Success)?.user
    val passwordStrength: PasswordStrength get() = PasswordStrength.from(password)
    val hasFieldErrors: Boolean
        get() = fullNameError != null || emailError != null ||
            passwordError != null || confirmPasswordError != null
}
