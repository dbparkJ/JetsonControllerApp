package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.ui.map.TicketGeometryMapTest
import com.example.jetsoncontroller.ui.survey.ticketStillMatches
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TicketUpdatesTest {
    private val ticket = ServerTicket("row", "구간", revision = "r1",
        geometry = JsonParser.parseString(TicketGeometryMapTest.geometry))

    @Test fun `detail and sheet map predicate hides absent and cleared geometry`() {
        assertTrue(ticketHasMap(ticket))
        assertFalse(ticketHasMap(ticket.copy(geometry = null)))
        assertFalse(ticketHasMap(ticket.copy(geometry = JsonNull.INSTANCE)))
        assertTrue(ticketHasMap(ticket.copy(geometry = JsonParser.parseString("""{"broken":true}"""))))
        val rangePending = ticket.geometry!!.deepCopy().asJsonObject.apply { remove("range") }
        assertTrue(ticketHasMap(ticket.copy(geometry = rangePending)))
    }

    @Test fun `refresh retains unchanged objects and order while applying additions changes and deletions`() {
        val second = ServerTicket("second", "다른 구간", revision = "r1")
        val deleted = ServerTicket("deleted", "삭제 구간")
        val added = ServerTicket("added", "새 구간")
        val changed = second.copy(title = "변경 구간", revision = "r2")
        val result = reconcileTickets(listOf(ticket, second, deleted), listOf(added, changed, ticket.copy()))
        assertEquals(listOf("row", "second", "added"), result.map { it.rowId })
        assertSame(ticket, result[0])
        assertEquals(changed, result[1])
        assertEquals(changed, displayedTicket(second, result, "second"))
        assertTrue(reconcileTickets(result, emptyList()).isEmpty())
        assertSame(ticket, displayedTicket(ticket, emptyList(), "row"))
    }

    @Test fun `row revision zone revision content change and clearing invalidate the old start draft`() {
        val draft = ticketDraft(ticket, "device", "profile", "credentials", "pipe", null, 1)
        val changedRevision = ticket.geometry!!.deepCopy().asJsonObject.apply { addProperty("revision", 8) }
        val changedContent = ticket.geometry!!.deepCopy().asJsonObject.apply {
            getAsJsonObject("range").addProperty("bufferM", 40)
        }
        assertTrue(ticketStillMatches(draft, "profile", listOf(ticket)))
        for (updated in listOf(ticket.copy(revision = "r2"), ticket.copy(geometry = changedRevision),
            ticket.copy(geometry = changedContent), ticket.copy(geometry = null))) {
            assertNotEquals(ticket.validationKey(), updated.validationKey())
            assertFalse(ticketStillMatches(draft, "profile", listOf(updated)))
            val current = ticketDraft(updated, "device", "profile", "credentials", "pipe", draft, 2)
            assertTrue(ticketStillMatches(current, "profile", listOf(updated)))
            assertEquals(updated, displayedTicket(ticket, listOf(updated), "row"))
        }
    }
}
