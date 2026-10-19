package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.ui.navigation.Routes
import com.example.jetsoncontroller.ui.navigation.WorkTicketEvent
import com.example.jetsoncontroller.ui.navigation.primaryNavigationVisible
import com.example.jetsoncontroller.ui.navigation.ticketJourneyDestination
import org.junit.Assert.*
import org.junit.Test

/** F3 contracts used by WorkGraph. Device QA still exercises the composed three-tap journey. */
class TapCountTest {
    private val accepted = PendingContextualStart("pipe", "project", 1, "section", 1, "policy", "check", "request")

    @Test fun `F3 opens ticket then checks in place then replaces it with collecting in three taps`() {
        var destination = Routes.TASKS
        var taps = 0
        listOf(WorkTicketEvent.OPEN_TICKET, WorkTicketEvent.REQUEST_START, WorkTicketEvent.START_ACCEPTED).forEach { event ->
            taps++
            ticketJourneyDestination(event, "row/old 1", accepted)?.let { destination = it }
            when (event) {
                WorkTicketEvent.OPEN_TICKET, WorkTicketEvent.REQUEST_START -> assertEquals("ticket_detail/row%2Fold%201", destination)
                WorkTicketEvent.START_ACCEPTED -> assertEquals(Routes.ACTIVE_RUN, destination)
                else -> fail("Unexpected task choice")
            }
        }
        assertEquals(3, taps)
        assertTrue(primaryNavigationVisible(Routes.TICKET_DETAIL))
        assertFalse(primaryNavigationVisible(Routes.ACTIVE_RUN))
    }

    @Test fun `ambiguous task adds one choice without leaving the ticket entry`() {
        val events = listOf(WorkTicketEvent.OPEN_TICKET, WorkTicketEvent.REQUEST_START,
            WorkTicketEvent.SELECT_TASK, WorkTicketEvent.START_ACCEPTED)
        val destinations = events.mapNotNull { ticketJourneyDestination(it, "row", accepted) }
        assertEquals(listOf("ticket_detail/row", Routes.ACTIVE_RUN), destinations)
        assertEquals(4, events.size)
        assertNull(ticketJourneyDestination(WorkTicketEvent.START_ACCEPTED))
    }
}
