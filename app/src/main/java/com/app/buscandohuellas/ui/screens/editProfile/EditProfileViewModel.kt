package com.app.buscandohuellas.ui.screens.editProfile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.model.Profile
import com.app.buscandohuellas.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * ViewModel de la pantalla "Editar perfil".
 *
 * Solo permite editar el nombre y la foto. La foto se sube y se persiste en el
 * momento en que el usuario la selecciona; [saveChanges] únicamente guarda el
 * nombre contra la tabla `profiles`.
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    /** Identificador real del usuario, ya resuelto desde la sesión si hacía falta. */
    private var resolvedUserId: String? = null

    /**
     * Carga el perfil de [userId]. Si llega vacío (o el marcador [CURRENT_USER]),
     * se resuelve el usuario autenticado desde la sesión de Supabase.
     */
    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isLoading = true, error = null) }

            val sessionUser = supabaseClient.auth.currentUserOrNull()
            val userIdResolved = userId
                .takeIf { it.isNotBlank() && it != CURRENT_USER }
                ?: sessionUser?.id
            resolvedUserId = userIdResolved

            if (userIdResolved.isNullOrBlank()) {
                Log.e(TAG, "No hay usuario autenticado para editar el perfil")
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = "No se pudo identificar al usuario autenticado."
                    )
                }
                return@launch
            }

            profileRepository.getProfile(userIdResolved)
                .onSuccess { profile ->
                    _uiState.update { current ->
                        current.copy(
                            currentProfile = profile,
                            fullName = profile.fullName.orEmpty(),
                            avatarUrl = profile.avatarUrl,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    // Si la fila de `profiles` todavía no existe se usan los
                    // datos de la sesión para no dejar el formulario vacío.
                    val fallbackName = sessionUser?.userMetadata
                        ?.get(FULL_NAME_KEY)
                        ?.jsonPrimitive
                        ?.contentOrNull
                        .orEmpty()

                    Log.e(TAG, "No se pudo cargar el perfil de $userIdResolved", error)
                    _uiState.update { current ->
                        current.copy(
                            currentProfile = Profile(
                                id = userIdResolved,
                                fullName = fallbackName,
                                email = sessionUser?.email
                            ),
                            fullName = fallbackName,
                            avatarUrl = null,
                            isLoading = false,
                            error = if (sessionUser == null) {
                                error.message ?: "No se pudo cargar el perfil."
                            } else {
                                null
                            }
                        )
                    }
                }
        }
    }

    fun updateFullName(name: String) {
        _uiState.update { current ->
            current.copy(fullName = name, error = null, isSuccess = false)
        }
    }

    /**
     * Muestra la imagen elegida de inmediato (vista previa optimista) y la sube
     * a Storage. Al terminar, el avatar del perfil queda persistido sin esperar
     * a que el usuario pulse "Guardar cambios".
     */
    fun selectImage(imageUri: Uri) {
        val state = _uiState.value
        if (state.isUploadingImage || state.isSaving) return

        val userId = resolvedUserId ?: state.currentProfile?.id
        if (userId.isNullOrBlank()) {
            _uiState.update { current ->
                current.copy(error = "No se pudo identificar al usuario autenticado.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    avatarPreviewUri = imageUri.toString(),
                    isUploadingImage = true,
                    error = null,
                    isSuccess = false
                )
            }

            profileRepository.uploadAvatar(userId, imageUri)
                .onSuccess { url ->
                    _uiState.update { current ->
                        current.copy(
                            currentProfile = current.currentProfile?.copy(avatarUrl = url),
                            avatarUrl = url,
                            avatarPreviewUri = null,
                            isUploadingImage = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "No se pudo subir la foto de perfil", error)
                    _uiState.update { current ->
                        current.copy(
                            // Se descarta la vista previa para no mostrar una
                            // foto que en realidad no se guardó.
                            avatarPreviewUri = null,
                            isUploadingImage = false,
                            error = error.message ?: "No se pudo subir la foto de perfil."
                        )
                    }
                }
        }
    }

    /** Valida el nombre y lo guarda en Supabase. */
    fun saveChanges() {
        val state = _uiState.value
        if (state.isSaving || state.isUploadingImage) return

        if (!state.isNameValid) {
            _uiState.update { it.copy(error = NAME_EMPTY_MESSAGE) }
            return
        }

        val profile = state.currentProfile
        if (profile == null) {
            _uiState.update { it.copy(error = "No se pudo cargar el perfil. Inténtalo de nuevo.") }
            return
        }
        if (!state.hasChanges) return

        val newName = state.fullName.trim()

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isSaving = true, error = null) }

            profileRepository.updateProfile(profile.id, newName)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            currentProfile = profile.copy(fullName = newName),
                            fullName = newName,
                            isSaving = false,
                            error = null,
                            isSuccess = true
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "No se pudo actualizar el perfil", error)
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            error = error.message ?: "No se pudo actualizar el perfil."
                        )
                    }
                }
        }
    }

    /** Limpia la marca de éxito después de mostrar el snackbar. */
    fun consumeSuccess() {
        _uiState.update { current -> current.copy(isSuccess = false) }
    }

    private companion object {
        const val TAG = "EditProfileViewModel"
        const val CURRENT_USER = "me"
        const val FULL_NAME_KEY = "full_name"
        const val NAME_EMPTY_MESSAGE = "El nombre no puede estar vacío"
    }
}
