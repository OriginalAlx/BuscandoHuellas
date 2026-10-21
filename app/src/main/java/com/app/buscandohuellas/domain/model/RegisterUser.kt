package com.app.buscandohuellas.domain.model

data class RegisterUser(
    val fullName: String,
    val email: String,
    val password: String,
    val confirmPassword: String,
    val role: String = ROLE_USER
) {
    companion object {
        const val ROLE_USER = "USER"
        const val MIN_PASSWORD_LENGTH = 6
    }
}
