package com.example.jetsoncontroller.data.server

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerTicketGeometryParseTest {
    private val gson = Gson()

    @Test
    fun `ticket payload without zone contract fields still parses`() {
        val response = gson.fromJson(
            """{"tickets":[{"rowId":"row-1","title":"Ticket","revision":"ticket-r3"}]}""",
            ServerTicketsResponse::class.java
        )

        val ticket = response.tickets.single()
        assertEquals("ticket-r3", ticket.revision)
        assertNull(ticket.features)
        assertNull(ticket.bufferM)
        assertNull(ticket.range)
        assertNull(ticket.bbox)
        assertNull(ticket.lengthM)
        assertNull(ticket.areaM2)
        assertNull(ticket.geometryRevision)
        assertNull(ticket.geometryHash)
    }

    @Test
    fun `present zone contract fields are retained alongside the row revision`() {
        val response = gson.fromJson(
            """{"tickets":[{"rowId":"row-1","title":"Ticket","revision":"ticket-r3","features":{"type":"FeatureCollection","features":[]},"bufferM":24.5,"range":{"type":"MultiPolygon","coordinates":[]},"bbox":[126.1,37.1,127.2,38.2],"lengthM":1200.0,"areaM2":340.0,"geometryRevision":"zone-r8","geometryHash":"sha256-value"}]}""",
            ServerTicketsResponse::class.java
        )

        val ticket = response.tickets.single()
        assertEquals("ticket-r3", ticket.revision)
        assertEquals("zone-r8", ticket.geometryRevision)
        assertEquals("sha256-value", ticket.geometryHash)
        assertEquals(24.5, ticket.bufferM!!, 0.0)
        assertEquals(1200.0, ticket.lengthM!!, 0.0)
        assertEquals(340.0, ticket.areaM2!!, 0.0)
        assertEquals(listOf(126.1, 37.1, 127.2, 38.2), ticket.bbox)
        assertEquals("FeatureCollection", ticket.features?.asJsonObject?.get("type")?.asString)
        assertEquals("MultiPolygon", ticket.range?.asJsonObject?.get("type")?.asString)
    }
}
