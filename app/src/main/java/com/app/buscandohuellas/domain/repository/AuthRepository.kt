package com.app.buscandohuellas.domain.repository

import com.app.buscandohuellas.domain.model.RegisterUser
import com.app.buscandohuellas.domain.model.User


interface AuthRepository {

    suspend fun login(email: String, password: String): Result<User>

    suspend fun registerUser(user: RegisterUser): Result<User>

    suspend fun getCurrentUser(): User?

    suspend fun logout()
}
