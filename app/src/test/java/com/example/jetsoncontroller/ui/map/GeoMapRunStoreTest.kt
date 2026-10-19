package com.example.jetsoncontroller.ui.map

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeoMapRunStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun firstGeometrySurvivesReopenAndLaterBoardEdits() {
        val first = GeoMapGeometry("revision-1", "hash-1", GeoGeometryState.RANGE_PENDING)
        val next = first.copy(revision = "revision-2", hash = "hash-2")
        val store = GeoMapRunStore(folder.root)
        assertEquals(first, store.freeze("device-a", "run-1", first))
        assertEquals(first, store.freeze("device-a", "run-1", next))
        assertEquals(first, GeoMapRunStore(folder.root).read("DEVICE-A", "run-1"))
        assertNull(store.read("device-b", "run-1"))
        assertNull(store.read("device-a", "run-2"))
        assertNull(store.read(null, "run-1"))
    }

    @Test fun acceptedStartBindsArchivedGeometryAfterPreparationWasCleared() {
        val ticket = ServerTicket("ticket", "구간", revision = "row-1", features = JsonParser.parseString(
            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[127,37]}}]}"""),
            geometryRevision = "geometry-1", geometryHash = "hash-1")
        val start = PendingContextualStart("pipeline", "project", 1, "section", 1, "policy", "preflight", "request",
            deviceId = "device-a", ticketRowId = "ticket", ticketRevision = "row-1", geometryRevision = "geometry-1", geometryHash = "hash-1")
        GeoMapRunStore(folder.root).cache(ticket)
        val reopened = GeoMapRunStore(folder.root)
        val changed = ticket.copy(revision = "row-2", geometryRevision = "geometry-2", geometryHash = "hash-2")
        assertEquals(mapGeometry(ticket), reopened.bindAccepted("device-a", "run-1", start, listOf(changed)))
        assertNull(reopened.bindAccepted("device-b", "run-1", start, listOf(ticket)))
    }
}
