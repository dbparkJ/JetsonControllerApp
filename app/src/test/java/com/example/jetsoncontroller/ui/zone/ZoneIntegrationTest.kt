package com.example.jetsoncontroller.ui.zone

import com.example.jetsoncontroller.data.server.*
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.data.zone.ZoneContract
import com.example.jetsoncontroller.ui.map.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZoneIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun ticket(revision: String = "zone-1", offset: Double = 0.0): ServerTicket = Gson().fromJson(
        """{"rowId":"ticket","title":"구간","revision":"row-1","geometry":{
          "schemaVersion":1,"revision":"$revision",
          "features":{"type":"FeatureCollection","features":[]},
          "range":{"bufferM":10,"lengthM":100,"areaM2":10000,"bbox":[0,0,1,1],
          "polygon":{"type":"Polygon","coordinates":[[[$offset,0],[1,0],[1,1],[$offset,1],[$offset,0]]]}}}}
        """, ServerTicket::class.java)

    @Test fun nestedGeometrySurvivesOfflineCacheWithItsOwnRevision() {
        val original = ticket()
        val file = temporary.newFile()
        TicketCacheStore(file).save(TicketCacheSnapshot("profile", "device", listOf(original), emptyList(),
            null, null, null, null, "now"))
        val restored = TicketCacheStore(file).read("profile", "device")!!.tickets.single()
        assertEquals(original.geometry, restored.geometry)
        assertEquals("row-1", restored.revision)
        assertEquals("zone-1", restored.geometryRevision)
        assertEquals(original.zoneHash, restored.geometryHash)
        assertEquals(GeoGeometryState.READY, mapGeometry(restored).state)
        assertEquals(20.0, ZoneContract.fromTicket(restored).contract!!.widthM!!, 0.0)
    }

    @Test fun runKeepsItsStartGeometryAcrossEditsAndRecordsOnlyObservedExcursions() {
        val root = temporary.newFolder()
        val original = ticket()
        val maps = GeoMapRunStore(root)
        maps.cache(original)
        val start = PendingContextualStart(deviceId = "device", pipelineId = "pipeline",
            surveyProjectId = "project", surveyProjectRevision = 1, surveySectionId = "section", surveySectionRevision = 1,
            policyRevision = "policy", preflightId = "preflight", clientRequestId = "request", ticketRowId = original.rowId,
            ticketRevision = original.revision, geometryRevision = original.zoneRevision, geometryHash = original.zoneHash)
        val frozen = maps.bindAccepted("device", "run", start, listOf(ticket("zone-2", 0.2)))!!
        assertEquals("zone-1", frozen.revision)
        val eventsFile = root.resolve("observations.json")
        val events = PhoneObservedZoneEvents(eventsFile)
        val key = ZoneRunKey("device", "run")
        events.startRun(key, com.example.jetsoncontroller.data.zone.ZoneValidation.parse(frozen.contractJson!!).contract!!, 1_000)
        fun sample(time: Long, lon: Double) = ZoneSample(0.5, lon, ZoneFixCategory.RTK_FIXED, time)
        assertNull(events.observe(key, sample(1_000, 2.0), 1_000).transition)
        assertTrue(events.observe(key, sample(2_000, 2.0), 2_000).transition!!.rejectHaptic)
        assertNull(events.observe(key, sample(2_000, 2.0), 2_000).transition)
        assertNull(events.observe(key, sample(3_000, 2.0), 3_000).transition)
        events.pause(key, 4_000)
        assertEquals(GeoZoneState.UNKNOWN, events.read(key)!!.mapOutput(4_000).state)
        events.observe(key, sample(6_000, 0.5), 6_000)
        events.observe(key, sample(7_000, 0.5), 7_000)
        events.streamStale(key, 18_001, 11_001)
        assertEquals(GeoZoneState.UNKNOWN, events.read(key)!!.mapOutput(18_001).state)
        events.endRun(key, 20_000)
        val restored = PhoneObservedZoneEvents(eventsFile).read(key)!!
        val result = restored.mapOutput(100_000)
        assertEquals(1, result.outsideCount)
        assertEquals(2_000L, result.outsideMillis)
        assertEquals(5_000L, result.unobservedMillis)
        assertTrue(result.phoneObserved)
        assertEquals(1, result.resultObservation()!!.excursions)
        assertNotNull(result.gaps)
    }
}
