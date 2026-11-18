package com.app.buscandohuellas.ui.screens.chat

import com.app.buscandohuellas.domain.model.ChatConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {

    @Test
    fun blocksSendWithBlankInput() {
        val state = ChatUiState(
            input = "   ",
            currentUserId = "user-1",
            connectionState = ChatConnectionState.CONNECTED
        )

        assertFalse(state.canSend)
    }

    @Test
    fun allowsSendWhenEverythingIsReady() {
        val state = ChatUiState(
            input = "Hola, ¿tienes noticias de la mascota?",
            currentUserId = "user-1",
            connectionState = ChatConnectionState.CONNECTED
        )

        assertTrue(state.canSend)
    }

    @Test
    fun blocksSendWithoutConnection() {
        val state = ChatUiState(
            input = "Hola",
            currentUserId = "user-1",
            connectionState = ChatConnectionState.DISCONNECTED
        )

        assertFalse(state.canSend)
        assertFalse(state.canWrite)
    }

    @Test
    fun blocksSendWhileConnecting() {
        val state = ChatUiState(
            input = "Hola",
            currentUserId = "user-1",
            connectionState = ChatConnectionState.CONNECTING
        )

        assertFalse(state.canSend)
    }

    @Test
    fun blocksSendWithoutSession() {
        val state = ChatUiState(
            input = "Hola",
            currentUserId = null,
            isAuthenticated = false,
            connectionState = ChatConnectionState.CONNECTED
        )

        assertFalse(state.canSend)
    }

    @Test
    fun blocksDuplicateSendWhileSending() {
        val state = ChatUiState(
            input = "Hola",
            currentUserId = "user-1",
            isSending = true,
            connectionState = ChatConnectionState.CONNECTED
        )

        assertFalse(state.canSend)
    }

    @Test
    fun blocksSendWhileSessionIsLoading() {
        val state = ChatUiState(
            input = "Hola",
            currentUserId = null,
            isAuthenticated = true,
            connectionState = ChatConnectionState.CONNECTED
        )

        assertFalse(state.canSend)
        assertFalse(state.canWrite)
    }

    @Test
    fun startsEmptyWithoutDefaultInput() {
        val state = ChatUiState()

        assertTrue(state.isEmpty)
        assertTrue(state.input.isEmpty())
    }
}
