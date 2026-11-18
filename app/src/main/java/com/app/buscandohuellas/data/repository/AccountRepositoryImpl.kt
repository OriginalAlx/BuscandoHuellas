package com.app.buscandohuellas.data.repository

import android.util.Log
import com.app.buscandohuellas.data.NotificationPreferencesRepository
import com.app.buscandohuellas.data.NotifiedReportsRepository
import com.app.buscandohuellas.domain.repository.AccountRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

private const val TAG = "AccountRepository"

/**
 * Implementación de [AccountRepository] sobre Supabase y DataStore.
 *
 * El orden de borrado importa: primero las filas que dependen del perfil
 * (tokens y reportes) y al final el perfil, para no violar claves foráneas.
 */
class AccountRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val notifiedReportsRepository: NotifiedReportsRepository
) : AccountRepository {

    override suspend fun deleteAccount(): Result<Unit> {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId.isNullOrBlank()) {
            return Result.failure(Exception("No hay una sesión activa. Inicia sesión de nuevo."))
        }

        return try {
            // 1. Tokens FCM asociados al usuario.
            deleteRows(table = TOKENS_TABLE, column = "user_id", userId = userId)

            // 2. Reportes publicados por el usuario.
            deleteRows(table = REPORTS_TABLE, column = "user_id", userId = userId)

            // 3. Perfil público.
            deleteRows(table = PROFILES_TABLE, column = "id", userId = userId)

            // 4. Preferencias y historial guardados en el dispositivo.
            notificationPreferencesRepository.clear()
            notifiedReportsRepository.clear()

            // 5. Cierre de sesión local y remoto.
            supabaseClient.auth.signOut()

            // Nota: supabase-kt 3.8.0 solo expone AdminApi.deleteUser(uid), que
            // exige la service role key y no debe usarse desde el cliente. La
            // fila de auth.users debe eliminarse desde una Edge Function o el
            // panel de Supabase; aquí se borran todos los datos de la app y se
            // invalida la sesión.
            Log.e(TAG, "Cuenta eliminada (datos + sesión) para user_id=$userId")
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpRequestException) {
            Log.e(TAG, "HttpRequestException eliminando la cuenta", error)
            Result.failure(Exception("No hay conexión a internet. Verifica tu red e intenta de nuevo."))
        } catch (error: Exception) {
            Log.e(TAG, "Error inesperado eliminando la cuenta", error)
            Result.failure(
                Exception(
                    error.message
                        ?: "No se pudo eliminar la cuenta. Inténtalo de nuevo o contacta a soporte."
                )
            )
        }
    }

    /** Borra las filas de [table] que pertenecen al usuario en [column]. */
    private suspend fun deleteRows(table: String, column: String, userId: String) {
        supabaseClient.from(table).delete {
            filter { eq(column, userId) }
        }
    }

    private companion object {
        const val TOKENS_TABLE = "user_notification_tokens"
        const val REPORTS_TABLE = "reports"
        const val PROFILES_TABLE = "profiles"
    }
}
