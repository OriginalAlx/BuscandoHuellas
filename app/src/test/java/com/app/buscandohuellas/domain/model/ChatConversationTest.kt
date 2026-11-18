package com.app.buscandohuellas.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatConversationTest {

    @Test
    fun buildsStableIdPerReport() {
        val first = ChatConversation.buildId(
            currentUserId = "user-1",
            ownerId = "owner-1",
            reportId = "report-1"
        )
        val second = ChatConversation.buildId(
            currentUserId = "user-1",
            ownerId = "owner-1",
            reportId = "report-1"
        )

        assertEquals(first, second)
        assertEquals("user-1:owner-1:report-1", first)
    }

    @Test
    fun separatesConversationsByReport() {
        val reportA = ChatConversation.buildId("user-1", "owner-1", "report-a")
        val reportB = ChatConversation.buildId("user-1", "owner-1", "report-b")

        assertEquals("user-1:owner-1:report-a", reportA)
        assertEquals("user-1:owner-1:report-b", reportB)
    }

    @Test
    fun supportsConversationWithoutReport() {
        val id = ChatConversation.buildId("user-1", "owner-1", null)

        assertEquals("user-1:owner-1:", id)
    }
}
