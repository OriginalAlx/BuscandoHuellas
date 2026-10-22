package com.app.buscandohuellas.ui.screens.login

import com.app.buscandohuellas.domain.model.User


data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val user: User? = null
)
