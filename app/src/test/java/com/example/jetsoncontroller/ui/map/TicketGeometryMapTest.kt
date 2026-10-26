package com.example.jetsoncontroller.ui.map

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketsResponse
import com.example.jetsoncontroller.data.server.TicketCacheSnapshot
import com.example.jetsoncontroller.data.server.TicketCacheStore
import com.example.jetsoncontroller.data.zone.ZoneContract
import com.example.jetsoncontroller.ui.zone.ZoneFixCategory
import com.example.jetsoncontroller.ui.zone.ZoneMonitor
import com.example.jetsoncontroller.ui.zone.ZoneSample
import com.example.jetsoncontroller.ui.zone.ZoneState
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TicketGeometryMapTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `operational geometry survives API cache contract and map conversion`() {
        val ticket = Gson().fromJson("""{"tickets":[{"rowId":"row","title":"구간","revision":"r1","geometry":$geometry}]}""",
            ServerTicketsResponse::class.java).tickets.single()
        val file = temporary.newFile("tickets.json")
        TicketCacheStore(file).save(TicketCacheSnapshot("profile", "device", listOf(ticket), emptyList(),
            null, null, null, null, "2026-10-26T02:00:00Z"))
        val restored = requireNotNull(TicketCacheStore(file).read("profile", "device")).tickets.single()
        assertEquals(ticket.geometry, restored.geometry)
        val contract = requireNotNull(ZoneContract.fromTicket(restored).contract)
        assertEquals("7", contract.revision)
        assertEquals(30.0, contract.bufferM!!, 0.0)
        assertEquals(60.0, contract.widthM!!, 0.0)
        assertEquals(listOf(127.0, 37.0, 127.01, 37.01), contract.bbox)
        assertEquals(100.0, contract.lengthM!!, 0.0)
        assertEquals(6000.0, contract.areaM2!!, 0.0)
        assertEquals(2, contract.features.size)
        assertEquals(1, contract.range!!.polygons.size)
        assertEquals("7", restored.zoneRevision)
        assertEquals(contract.hash, restored.zoneHash)
        val map = mapGeometry(restored)
        assertEquals(GeoGeometryState.READY, map.state)
        assertEquals(1, map.pointCount)
        assertEquals("7", map.revision)
        assertEquals(contract.hash, map.hash)
        val features = JsonParser.parseString(map.sourceJson).asJsonObject.getAsJsonArray("features")
        assertEquals(listOf("route", "point", "range"), features.map {
            it.asJsonObject.getAsJsonObject("properties").get("kind").asString
        })
        assertEquals(listOf("LineString", "Point", "Polygon"), features.map {
            it.asJsonObject.getAsJsonObject("geometry").get("type").asString
        })
        assertEquals(JsonParser.parseString(geometry), JsonParser.parseString(map.contractJson))
    }

    @Test fun `array and legacy FeatureCollection render the same geometry`() {
        val array = JsonParser.parseString(geometry).asJsonObject
        val legacy = array.deepCopy().apply {
            add("features", JsonParser.parseString("""{"type":"FeatureCollection","features":${array.get("features")}}"""))
        }
        assertEquals(mapGeometry(ServerTicket("row", "구간", geometry = array)).sourceJson,
            mapGeometry(ServerTicket("row", "구간", geometry = legacy)).sourceJson)
    }

    @Test fun `zone preview badges include inside outside unknown and stale location`() {
        val mapped = mapGeometry(ServerTicket("row", "구간", geometry = JsonParser.parseString(geometry)))
        fun badge(state: GeoZoneState, stale: Boolean = false) = zoneBadge(GeoMapMode.ZONE,
            GeoMapData(geometry = mapped, zone = GeoMapZoneSnapshot(state), deviceStale = stale))?.first
        assertEquals("구역 안", badge(GeoZoneState.INSIDE))
        assertEquals("구역 밖", badge(GeoZoneState.OUTSIDE))
        assertEquals("위치 미확인", badge(GeoZoneState.UNKNOWN))
        assertEquals("위치 미확인", badge(GeoZoneState.INSIDE, stale = true))
        assertNull(zoneBadge(GeoMapMode.ZONE, GeoMapData()))
    }

    @Test fun `operational polygon classifies device samples and loses judgement without a fresh fix`() {
        val range = ZoneContract.parse(JsonParser.parseString(geometry)).contract!!.range
        fun sample(time: Long, latitude: Double = 37.005, longitude: Double = 127.005) =
            ZoneSample(latitude, longitude, ZoneFixCategory.RTK_FIXED, time)
        val first = ZoneMonitor.evaluate(range, sample(1_000), 1_000).monitor
        val inside = ZoneMonitor.evaluate(range, sample(2_000), 2_000, first).monitor
        assertEquals(ZoneState.INSIDE, inside.state)
        val leaving = ZoneMonitor.evaluate(range, sample(3_000, 37.02, 127.02), 3_000, inside).monitor
        val outside = ZoneMonitor.evaluate(range, sample(4_000, 37.02, 127.02), 4_000, leaving)
        assertEquals(ZoneState.OUTSIDE, outside.monitor.state)
        assertFalse(outside.transition!!.exitWarning)
        assertEquals(ZoneState.UNKNOWN, ZoneMonitor.evaluate(range, null, 5_000, outside.monitor).monitor.state)
        assertEquals(ZoneState.UNKNOWN, ZoneMonitor.evaluate(range, sample(2_000), 12_001, inside).monitor.state)
        assertEquals(ZoneState.UNKNOWN, ZoneMonitor.evaluate(null, sample(2_000), 2_000).monitor.state)
    }

    companion object {
        val geometry = """{"schemaVersion":1,"features":[
            {"type":"Feature","id":"route","properties":{"role":"ROUTE","label":"북측"},
             "geometry":{"type":"LineString","coordinates":[[127.001,37.001],[127.009,37.009]]}},
            {"type":"Feature","id":"point","properties":{"role":"POINT"},
             "geometry":{"type":"Point","coordinates":[127.005,37.005]}}],
            "range":{"bufferM":30,"polygon":{"type":"Polygon","coordinates":[[[127,37],[127.01,37],[127.01,37.01],[127,37.01],[127,37]]]},
            "bbox":[127,37,127.01,37.01],"lengthM":100,"areaM2":6000},"revision":7}"""
    }
}
