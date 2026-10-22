package com.app.buscandohuellas.data.repository

import android.util.Log
import com.app.buscandohuellas.domain.model.RegisterUser
import com.app.buscandohuellas.domain.model.User
import com.app.buscandohuellas.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put


private const val TAG = "AuthRepository"
private const val FULL_NAME_KEY = "full_name"

class AuthRepositoryImpl(
    private val supabaseClient: SupabaseClient
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> = try {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val sessionUser = supabaseClient.auth.currentUserOrNull()
            ?: throw IllegalStateException("No se pudo obtener la sesión del usuario")

        
        val profile = selectProfile(sessionUser.id)
        Result.success(
            profile?.withMetadataName(sessionUser) ?: sessionUser.toDomainUser()
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: RestException) {
        Log.e(TAG, "RestException en login", error)
        Result.failure(Exception(friendlyMessage(error)))
    } catch (error: HttpRequestException) {
        Log.e(TAG, "HttpRequestException en login", error)
        Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
    } catch (error: Exception) {
        Log.e(TAG, "Error inesperado en login", error)
        Result.failure(Exception(friendlyMessage(error)))
    }

    override suspend fun registerUser(user: RegisterUser): Result<User> {
        
        
        if (user.role != RegisterUser.ROLE_USER) {
            return Result.failure(Exception("Solo se pueden crear cuentas de usuario."))
        }

        val fullName = user.fullName.trim()
        val email = user.email.trim()

        if (fullName.isEmpty()) {
            return Result.failure(Exception("El nombre completo es obligatorio."))
        }
        if (user.password.length < RegisterUser.MIN_PASSWORD_LENGTH) {
            return Result.failure(
                Exception("La contraseña debe tener al menos ${RegisterUser.MIN_PASSWORD_LENGTH} caracteres.")
            )
        }
        if (user.password != user.confirmPassword) {
            return Result.failure(Exception("Las contraseñas no coinciden."))
        }

        return try {
            signUpOrSignIn(user.copy(fullName = fullName, email = email))

            val sessionUser = supabaseClient.auth.currentUserOrNull()
            if (sessionUser == null) {
                
                
                Result.failure(
                    Exception("No se pudo completar el registro. Inténtalo de nuevo.")
                )
            } else {
                
                
                val profile = ensureProfile(sessionUser.id, sessionUser.email ?: email)
                Result.success(
                    profile?.withMetadataName(sessionUser) ?: sessionUser.toDomainUser()
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AuthWeakPasswordException) {
            Log.e(TAG, "Contraseña débil en registerUser", error)
            Result.failure(
                Exception("La contraseña es demasiado débil. Añade mayúsculas, números o símbolos.")
            )
        } catch (error: RestException) {
            Log.e(TAG, "RestException en registerUser", error)
            Result.failure(Exception(friendlyMessage(error)))
        } catch (error: HttpRequestException) {
            Log.e(TAG, "HttpRequestException en registerUser", error)
            Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
        } catch (error: Exception) {
            Log.e(TAG, "Error inesperado en registerUser", error)
            Result.failure(Exception(friendlyMessage(error)))
        }
    }

    override suspend fun getCurrentUser(): User? {
        val sessionUser = supabaseClient.auth.currentUserOrNull() ?: return null
        return try {
            val profile = selectProfile(sessionUser.id)
            profile?.withMetadataName(sessionUser) ?: sessionUser.toDomainUser()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            sessionUser.toDomainUser()
        }
    }

    override suspend fun logout() {
        supabaseClient.auth.signOut()
    }

    
    private fun UserInfo.toDomainUser(role: String = RegisterUser.ROLE_USER): User = User(
        id = id,
        email = email.orEmpty(),
        role = role,
        fullName = fullNameFromMetadata()
    )

    
    private fun User.withMetadataName(sessionUser: UserInfo): User =
        copy(fullName = sessionUser.fullNameFromMetadata() ?: fullName)

    
    private fun UserInfo.fullNameFromMetadata(): String? =
        userMetadata?.get(FULL_NAME_KEY)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private suspend fun signUpOrSignIn(user: RegisterUser) {
        try {
            
            supabaseClient.auth.signUpWith(Email) {
                email = user.email
                password = user.password
                data = buildJsonObject {
                    put("full_name", user.fullName)
                }
            }
        } catch (signUpError: CancellationException) {
            throw signUpError
        } catch (signUpError: RestException) {
            if (!isAlreadyRegistered(signUpError)) throw signUpError
            Log.i(TAG, "Cuenta ya registrada, se retoma el alta con signIn")
            try {
                supabaseClient.auth.signInWith(Email) {
                    email = user.email
                    password = user.password
                }
            } catch (signInError: CancellationException) {
                throw signInError
            } catch (signInError: HttpRequestException) {
                throw signInError
            } catch (signInError: RestException) {
                if (signInError.message.orEmpty().contains("not confirmed", ignoreCase = true)) {
                    throw signInError
                }
                Log.w(TAG, "Cuenta existente con contraseña distinta", signInError)
                throw signUpError
            } catch (signInError: Exception) {
                Log.w(TAG, "No se pudo iniciar sesión tras registro duplicado", signInError)
                throw signUpError
            }
        }
    }

    private suspend fun ensureProfile(userId: String, email: String): User? {
        selectProfile(userId)?.let { return it }

        return try {
            supabaseClient.from("users")
                .insert(
                    UserInsertRow(
                        id = userId,
                        email = email,
                        role = RegisterUser.ROLE_USER,
                        passwordHash = ""
                    )
                ) { select() }
                .decodeSingleOrNull<User>()
        } catch (insertError: CancellationException) {
            throw insertError
        } catch (insertError: RestException) {
            
            Log.w(TAG, "No se pudo insertar el perfil, se reintenta con select", insertError)
            selectProfile(userId)
        }
    }

    private suspend fun selectProfile(userId: String): User? = try {
        supabaseClient.from("users")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<User>()
    } catch (selectError: CancellationException) {
        throw selectError
    } catch (selectError: Exception) {
        Log.w(TAG, "No se pudo leer el perfil de $userId", selectError)
        null
    }

    private fun isAlreadyRegistered(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("already registered", ignoreCase = true) ||
            message.contains("user_already_exists", ignoreCase = true)
    }

    private fun friendlyMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Invalid login credentials", ignoreCase = true) ||
                message.contains("invalid_credentials", ignoreCase = true) ->
                "Correo o contraseña incorrectos."

            message.contains("already registered", ignoreCase = true) ||
                message.contains("user_already_exists", ignoreCase = true) ->
                "Ya existe una cuenta con ese correo electrónico. Intenta iniciar sesión."

            message.contains("Email not confirmed", ignoreCase = true) ->
                "Tu correo electrónico aún no ha sido confirmado."

            message.contains("rate limit", ignoreCase = true) ->
                "Demasiados intentos. Espera un momento y vuelve a intentarlo."

            message.isNotBlank() -> message

            else -> "Ocurrió un error inesperado. Inténtalo de nuevo."
        }
    }
}


@Serializable
private data class UserInsertRow(
    val id: String,
    val email: String,
    val role: String,
    @SerialName("password_hash")
    val passwordHash: String
)
