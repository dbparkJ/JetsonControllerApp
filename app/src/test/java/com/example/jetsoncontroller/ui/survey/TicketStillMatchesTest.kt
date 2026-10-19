package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.model.PreparationDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketStillMatchesTest {
    private val draft = PreparationDraft(
        deviceId = "geo-x-1", serverProfileId = "ops", ticketRowId = "row-7", ticketRevision = "r3"
    )
    private val ticket = ServerTicket(rowId = "row-7", title = "장치테스트", revision = "r3")

    @Test
    fun `a manual draft without a ticket always passes`() {
        assertTrue(ticketStillMatches(PreparationDraft(deviceId = "geo-x-1"), null, emptyList()))
    }

    @Test
    fun `the same ticket at the same revision on the same profile passes`() {
        assertTrue(ticketStillMatches(draft, "ops", listOf(ticket)))
    }

    @Test
    fun `a new revision, a missing ticket or another profile fails`() {
        assertFalse(ticketStillMatches(draft, "ops", listOf(ticket.copy(revision = "r4"))))
        assertFalse(ticketStillMatches(draft, "ops", emptyList()))
        assertFalse(ticketStillMatches(draft, "dev", listOf(ticket)))
    }

    @Test
    fun `a revision unknown on either side cannot be compared and passes`() {
        assertTrue(ticketStillMatches(draft.copy(ticketRevision = null), "ops", listOf(ticket)))
        assertTrue(ticketStillMatches(draft, "ops", listOf(ticket.copy(revision = null))))
    }
}
