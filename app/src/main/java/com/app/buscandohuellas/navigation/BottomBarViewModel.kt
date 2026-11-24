package com.app.buscandohuellas.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.buscandohuellas.domain.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Alimenta el badge de Alertas del BottomBar.
 *
 * Reacciona a los cambios de sesión (login/logout) y al Flow en tiempo real
 * del [AlertRepository], de modo que el contador se actualiza aunque el
 * usuario esté en otra pestaña.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BottomBarViewModel @Inject constructor(
    alertRepository: AlertRepository,
    supabaseClient: SupabaseClient
) : ViewModel() {

    val unreadCount: StateFlow<Int> = supabaseClient.auth.sessionStatus
        .map { status ->
            (status as? SessionStatus.Authenticated)?.session?.user?.id
        }
        .distinctUntilChanged()
        .flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(0)
            } else {
                alertRepository.getUnreadAlertsCount(userId)
                    .catch { emit(0) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )
}
