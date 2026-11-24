package com.app.buscandohuellas.ui.screens.chat

import com.app.buscandohuellas.domain.model.ChatConversation
import com.app.buscandohuellas.domain.model.ReportMatch
import com.app.buscandohuellas.domain.model.ReportStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {

    @Test
    fun blocksSendWithBlankInput() {
        val state = ChatUiState(
            input = "   ",
            currentUserId = "user-1",
            conversation = conversation()
        )

        assertFalse(state.canSend)
    }

    @Test
    fun allowsSendWhenEverythingIsReady() {
        val state = ChatUiState(
            input = "Hola, ¿tienes noticias de la mascota?",
            currentUserId = "user-1",
            conversation = conversation()
        )

        assertTrue(state.canSend)
    }

    @Test
    fun blocksSendWhileLoading() {
        val state = ChatUiState(
            input = "Hola",
            currentUserId = "user-1",
            conversation = conversation(),
            isLoading = true
        )

        assertFalse(state.canSend)
    }

    @Test
    fun blocksDuplicateSendWhileSending() {
        val state = ChatUiState(
            input = "Hola",
            currentUserId = "user-1",
            conversation = conversation(),
            isSending = true
        )

        assertFalse(state.canSend)
    }

    @Test
    fun blocksSendWithoutSession() {
        val state = ChatUiState(input = "Hola", currentUserId = "")

        assertFalse(state.canSend)
    }

    @Test
    fun startsEmptyWithoutDefaultInput() {
        val state = ChatUiState()

        assertTrue(state.isEmpty)
        assertTrue(state.input.isEmpty())
        assertNull(state.conversation)
    }

    @Test
    fun bannerOnlyShowsForOwnerWithPendingMatch() {
        val match = ReportMatch(
            id = "match-1",
            lostReportId = "report-1",
            foundReportId = "report-2",
            matchedBy = "finder-1"
        )

        val ownerState = ChatUiState(
            currentUserId = "owner-1",
            reportOwnerId = "owner-1",
            pendingMatch = match
        )
        assertTrue(ownerState.showMatchBanner)

        assertFalse(ChatUiState().showMatchBanner)
        assertFalse(
            ChatUiState(
                currentUserId = "owner-1",
                reportOwnerId = "owner-1",
                pendingMatch = match,
                isLoadingMatch = true
            ).showMatchBanner
        )
        assertFalse(
            ChatUiState(
                currentUserId = "owner-1",
                reportOwnerId = "owner-1",
                pendingMatch = match.copy(confirmedByOwner = true)
            ).showMatchBanner
        )
        assertFalse(
            ChatUiState(
                currentUserId = "owner-1",
                reportOwnerId = "owner-1",
                pendingMatch = match.copy(rejectedByOwner = true)
            ).showMatchBanner
        )
    }

    @Test
    fun bannerIsHiddenForContributor() {
        val match = ReportMatch(
            id = "match-1",
            lostReportId = "report-1",
            foundReportId = "report-2",
            matchedBy = "finder-1"
        )

        val contributorState = ChatUiState(
            currentUserId = "finder-1",
            reportOwnerId = "owner-1",
            pendingMatch = match
        )

        assertFalse(contributorState.isReportOwner)
        assertFalse(contributorState.showMatchBanner)
    }

    @Test
    fun deliveryButtonOnlyShowsForOwnerWhenSighted() {
        val ownerBase = ChatUiState(
            currentUserId = "owner-1",
            reportOwnerId = "owner-1",
            reportStatus = ReportStatus.SIGHTED
        )
        assertTrue(ownerBase.canShowDeliveryButton)

        assertFalse(
            ownerBase.copy(reportStatus = ReportStatus.LOST).canShowDeliveryButton
        )
        assertFalse(
            ownerBase.copy(reportStatus = ReportStatus.RESOLVED).canShowDeliveryButton
        )
        assertFalse(
            ownerBase.copy(currentUserId = "finder-1").canShowDeliveryButton
        )
        assertFalse(
            ownerBase.copy(isCompletingDelivery = true).canShowDeliveryButton
        )
    }

    @Test
    fun matchActionsBlockOtherSends() {
        val match = ReportMatch(
            id = "match-1",
            lostReportId = "report-1",
            foundReportId = "report-2",
            matchedBy = "finder-1"
        )

        val confirming = ChatUiState(
            input = "Hola",
            currentUserId = "user-1",
            conversation = conversation(),
            pendingMatch = match,
            isConfirmingMatch = true
        )
        assertTrue(confirming.isBusy)
        assertFalse(confirming.canSend)

        val rejecting = ChatUiState(
            input = "Hola",
            currentUserId = "user-1",
            conversation = conversation(),
            pendingMatch = match,
            isRejectingMatch = true
        )
        assertTrue(rejecting.isBusy)
        assertFalse(rejecting.canSend)
    }

    private fun conversation() = ChatConversation(
        id = "conversation-1",
        ownerId = "owner-1",
        ownerName = "Dueño"
    )
}
