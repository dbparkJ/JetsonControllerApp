package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.PendingTicketSelection
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class WorkMapTargetTest {
    private val first = ServerTicket("row-1", "같은 이름", revision = "1")
    private val second = ServerTicket("row-2", "같은 이름", revision = "2")
    private val preparation = TicketPreparation("profile", "credential", "device", first, "pipeline")
    private val selection = PendingTicketSelection("profile", first.rowId, first.title, null, null, "device", "pipeline")
    private val tickets = ServerTicketUiState(profileId = "profile", deviceId = "device", credentialRevision = "credential",
        tickets = listOf(first, second))

    @Test fun switchingTicketIdsReplacesGeometryEvenWhenNamesAreEqual() {
        assertSame(first, workMapTicket("DEVICE", "profile", "pipeline", preparation, selection, tickets))
        assertSame(second, workMapTicket("device", "profile", "pipeline", preparation, selection.copy(rowId = second.rowId), tickets))
        assertNull(workMapTicket("device", "profile", "pipeline", preparation, selection.copy(rowId = "missing"), tickets))
        assertNull(workMapTicket("device", "profile", "pipeline", null, null, tickets))
    }

    @Test fun deviceProfileAndPipelineChangesNeverReuseThePreviousMap() {
        assertNull(workMapTicket("other-device", "profile", "pipeline", preparation, selection, tickets))
        assertNull(workMapTicket("device", "other-profile", "pipeline", preparation, selection, tickets))
        assertNull(workMapTicket("device", "profile", "other-pipeline", preparation, selection, tickets))
        assertNull(workMapTicket(null, "profile", "pipeline", preparation, selection, tickets))
        assertNull(workMapTicket("device", null, "pipeline", preparation, selection, tickets))
    }

    @Test fun onlyTheBoundSnapshotCanFillAnUnavailableLiveList() {
        val unrelated = tickets.copy(deviceId = "other-device", tickets = listOf(first.copy(revision = "wrong")))
        assertSame(first, workMapTicket("device", "profile", "pipeline", preparation, selection, unrelated))
        assertNull(workMapTicket("device", "profile", "pipeline", null, selection, unrelated))
        assertNull(workMapTicket("device", "profile", "pipeline", preparation, selection,
            tickets.copy(credentialRevision = "changed")))
        val updated = first.copy(revision = "new")
        assertSame(updated, workMapTicket("device", "profile", "pipeline", preparation, selection,
            tickets.copy(tickets = listOf(updated))))
    }

    @Test fun bothRouteSlotsUseTheTicketAdapterInsideAnIdentityKey() {
        val graph = r8UiSource("navigation/WorkGraph.kt")
        assertEquals(2, Regex("mapCard = mapCard").findAll(graph).count())
        val adapter = graph.substringAfter("private fun WorkTargetMapCard").substringBefore("private fun WorkOngoingStrip")
        assertTrue(adapter.contains("key(deviceId, profileId, pipelineId, ticket.rowId, ticket.revision, ticket.zoneRevision, ticket.zoneHash, selectionKey)"))
        assertTrue(adapter.contains("rememberTicketMapData(ticket, deviceId)"))
        assertTrue(adapter.contains("GeoMapCardWithDestination("))
        assertTrue(graph.contains("ticketPreparationContextMatches(preparation,"))
        assertTrue(graph.contains("surveyRunState.selectedSection?.surveySectionId"))
    }
}
