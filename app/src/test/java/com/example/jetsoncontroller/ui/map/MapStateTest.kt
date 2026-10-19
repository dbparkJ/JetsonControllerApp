package com.example.jetsoncontroller.ui.map

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.model.RoutePoint
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class MapStateTest {
    private fun geometry(range: Boolean = true) = mapGeometry(ServerTicket("ticket", "구간",
        features = JsonParser.parseString("""{"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"LineString","coordinates":[[127,37],[127.01,37.01]]}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[127,37]}},
          {"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[127,37],[128,37],[128,38],[127,37]]]}}
        ]}"""), range = if (range) JsonParser.parseString("""{"type":"Polygon","coordinates":[[[127,37],[128,37],[128,38],[127,37]]]}""") else null,
        bufferM = 10.0, lengthM = 1200.0, geometryRevision = "revision-1", geometryHash = "hash-1"))

    @Test fun noKeyStillBuildsAllOverlaySourcesAndLocalBackground() {
        val style = JsonParser.parseString(mapStyleJson(GeoMapBase.GENERAL, "", "#F1F3F7", 1f)).asJsonObject
        assertEquals(setOf("zone", "trail", "device", "phone"), style.getAsJsonObject("sources").keySet())
        val layers = style.getAsJsonArray("layers").map { it.asJsonObject.get("id").asString }
        assertEquals(listOf("background", "range-fill", "range-casing", "range-edge", "route-casing", "route", "area-casing", "area-edge", "points",
            "trail-casing", "trail-precise", "trail-approximate", "trail-unknown", "gap-casing", "gap-rings", "device-uncertainty",
            "device-outer", "device-marker", "device-stale", "phone-halo", "phone-marker"), layers)
        assertFalse(style.toString().contains("https://"))
    }

    @Test fun stylesKeepCasingsPatternsAndFixedColorsInDarkAndSatelliteModes() {
        fun layers(base: GeoMapBase, dim: Float) = JsonParser.parseString(mapStyleJson(base, "fixture", "#000000", dim))
            .asJsonObject.getAsJsonArray("layers").associate { it.asJsonObject.get("id").asString to it.asJsonObject.getAsJsonObject("paint") }
        val general = layers(GeoMapBase.GENERAL, .75f)
        val satellite = layers(GeoMapBase.SATELLITE, .75f)
        assertEquals(.75f, general.getValue("raster").get("raster-brightness-max").asFloat, .0001f)
        assertEquals(-.2f, general.getValue("raster").get("raster-saturation").asFloat, .0001f)
        assertEquals(1f, satellite.getValue("raster").get("raster-brightness-max").asFloat, .0001f)
        assertEquals(.22f, general.getValue("range-fill").get("fill-opacity").asFloat, .0001f)
        assertEquals(.16f, satellite.getValue("range-fill").get("fill-opacity").asFloat, .0001f)
        listOf("range-casing", "route-casing", "area-casing").forEach { assertEquals(5f, general.getValue(it).get("line-width").asFloat, .0001f) }
        assertEquals(7f, general.getValue("trail-casing").get("line-width").asFloat, .0001f)
        assertEquals("[3.0,2.0]", general.getValue("range-edge").get("line-dasharray").toString())
        assertEquals("[2.0,1.0]", general.getValue("trail-approximate").get("line-dasharray").toString())
        assertEquals("[0.5,1.0]", general.getValue("trail-unknown").get("line-dasharray").toString())
        listOf("range-edge", "route", "area-edge", "trail-precise", "trail-approximate", "trail-unknown", "device-marker").forEach {
            assertEquals(general.getValue(it), satellite.getValue(it))
        }
    }

    @Test fun cardSizesAndLandscapeUseCurrentPane() {
        assertEquals(200, mapCardHeight(GeoMapMode.ZONE, false))
        assertEquals(200, mapCardHeight(GeoMapMode.ROUTE, false))
        assertEquals(240, mapCardHeight(GeoMapMode.RUN, false))
        assertEquals(160, mapCardHeight(GeoMapMode.RUN, true))
        assertTrue(mapUsesSidePanel(882f, 356f))
        assertFalse(mapUsesSidePanel(412f, 440f))
        assertFalse(mapUsesSidePanel(1280f, 720f))
    }

    @Test fun cardsStopForHiddenBackgroundedAndFullMapStates() {
        assertTrue(mapCardActive(true, true, false))
        assertFalse(mapCardActive(false, true, false))
        assertFalse(mapCardActive(true, false, false))
        assertFalse(mapCardActive(true, true, true))
    }

    @Test fun absentPendingAndInvalidGeometryRemainDifferent() {
        assertEquals(GeoGeometryState.ABSENT, mapGeometry(ServerTicket("ticket", "구간")).state)
        assertEquals(GeoGeometryState.ABSENT, mapGeometry(ServerTicket("ticket", "구간", features = JsonParser.parseString("""{"type":"FeatureCollection","features":[]}"""))).state)
        assertEquals(GeoGeometryState.RANGE_PENDING, geometry(false).state)
        assertEquals("구역 범위가 아직 계산되지 않았습니다.", mapPositionSentence(GeoMapData(geometry = geometry(false))))
        val invalid = mapGeometry(ServerTicket("ticket", "구간", range = JsonParser.parseString("""{"type":"Polygon","coordinates":[[[127,37],[128,37],[128,38],[127,38]]]}""")))
        assertEquals(GeoGeometryState.INVALID, invalid.state)
        assertEquals(emptyMapCollection(), invalid.sourceJson)
        assertEquals("수집 구역 정보를 읽을 수 없습니다.", mapPositionSentence(GeoMapData(geometry = invalid)))
        assertNotNull(invalid.invalidReason)
    }

    @Test fun boundsAndAllPolygonHolesAreValidatedWithoutComputingABuffer() {
        val ticket = ServerTicket("ticket", "구간", range = JsonParser.parseString("""{"type":"MultiPolygon","coordinates":[
          [[[127,37],[128,37],[128,38],[127,37]],[[127.1,37.1],[127.2,37.1],[127.2,37.2],[127.1,37.1]]],
          [[[129,37],[130,37],[130,38],[129,37]]]
        ]}"""))
        assertEquals(GeoGeometryState.READY, mapGeometry(ticket).state)
        val swapped = ticket.copy(range = JsonParser.parseString("""{"type":"Polygon","coordinates":[[[37,127],[37,128],[38,128],[37,127]]]}"""))
        assertEquals(GeoGeometryState.INVALID, mapGeometry(swapped).state)
        assertNull(zoneBadge(GeoMapMode.RUN, GeoMapData(geometry = geometry(false), zone = GeoMapZoneSnapshot(GeoZoneState.INSIDE))))
    }

    @Test fun ticketOutsideUsesCaptionAndRunOutsideUsesBadge() {
        val data = GeoMapData(geometry = geometry(), device = GeoMapPosition(37.0, 127.0), zone = GeoMapZoneSnapshot(GeoZoneState.OUTSIDE, 350.0))
        assertNull(zoneBadge(GeoMapMode.ZONE, data))
        assertEquals("구역 밖 · 350\u00A0m", zoneBadge(GeoMapMode.RUN, data)?.first)
        assertEquals("장치가 구역에서 350\u00A0m 떨어져 있습니다.", mapPositionSentence(data))
        assertEquals("경로 1.2\u00A0km · 폭 20\u00A0m · 영역 1곳 · 지점 1곳", data.geometry!!.summary)
        assertNull(zoneBadge(GeoMapMode.ROUTE, data))
        assertEquals("장치가 구역 안에 있습니다.", mapPositionSentence(data.copy(zone = GeoMapZoneSnapshot(GeoZoneState.INSIDE))))
        assertEquals("장치가 구역 경계 근처에 있습니다.", mapPositionSentence(data.copy(zone = GeoMapZoneSnapshot(GeoZoneState.NEAR))))
        assertEquals("장치 위치를 확인할 수 없습니다.", mapPositionSentence(data.copy(deviceStale = true)))
    }

    @Test fun resultDoesNotInventObservationsOrRoundAwaySeconds() {
        assertEquals("", mapResultCaption(GeoMapZoneSnapshot()))
        assertEquals("구역 밖 기록 2회 · 1분 10초 · 관찰하지 못한 시간 2분", mapResultCaption(GeoMapZoneSnapshot(
            outsideCount = 2, outsideMillis = 70_000, unobservedMillis = 120_000)))
        assertEquals("0초", mapObservationDuration(0))
    }

    @Test fun entryFitIncludesNearbyDeviceButNotOneMoreThanTwoKilometersAway() {
        val geometry = geometry().copy(positions = listOf(GeoMapPosition(37.0, 127.0), GeoMapPosition(37.001, 127.001)))
        val close = GeoMapPosition(37.002, 127.002)
        assertTrue(close in initialMapPositions(GeoMapData(geometry, device = close)))
        val far = GeoMapPosition(38.0, 128.0)
        assertFalse(far in initialMapPositions(GeoMapData(geometry, device = far)))
    }

    @Test fun changedTicketCannotReplaceBoundRevision() {
        val first = geometry()
        assertEquals(first, boundMapGeometry(first, "revision-1", "hash-1"))
        assertNull(boundMapGeometry(first.copy(hash = "hash-2"), "revision-1", "hash-1"))
        assertNull(boundMapGeometry(first, null, null))
    }

    @Test fun trailsBreakAtObservationGapsAndInvalidOrNoFixSamples() {
        val route = listOf(
            RoutePoint(37.0, 127.0, 1_000, fixState = "FIXED"),
            RoutePoint(37.01, 127.01, 2_000, fixState = "FIXED"),
            RoutePoint(37.02, 127.02, 3_000, fixState = "FLOAT"),
            RoutePoint(37.03, 127.03, 4_000, fixState = "UNKNOWN"))
        fun kinds(data: GeoMapData) = JsonParser.parseString(mapTrailJson(data)).asJsonObject.getAsJsonArray("features")
            .map { it.asJsonObject.getAsJsonObject("properties").get("kind").asString }
        assertEquals(listOf("precise", "approximate", "unknown"), kinds(GeoMapData(trail = route)))
        assertEquals(listOf("precise", "gap", "gap", "unknown"), kinds(GeoMapData(trail = route,
            zone = GeoMapZoneSnapshot(gaps = listOf(GeoMapGap(2_000, 3_000))))))
        assertTrue(kinds(GeoMapData(trail = route.take(2).map { it.copy(fixState = "NO_FIX") })).isEmpty())
        assertTrue(kinds(GeoMapData(trail = listOf(route.first(), route[1].copy(latitude = Double.NaN)))).isEmpty())
    }

    @Test fun staleAndFreshDeviceSourcesDifferAndUncertaintyStartsAtThreeMeters() {
        val fresh = GeoMapData(device = GeoMapPosition(37.0, 127.0), uncertaintyM = 2.99)
        val json = mapDeviceJson(fresh)
        assertTrue(json.contains("\"device\""))
        assertFalse(json.contains("uncertainty"))
        assertTrue(mapDeviceJson(fresh.copy(deviceStale = true)).contains("device-stale"))
        assertTrue(mapDeviceJson(fresh.copy(uncertaintyM = 3.0)).contains("uncertainty"))
        assertEquals(emptyMapCollection(), mapDeviceJson(GeoMapData(device = GeoMapPosition(91.0, 127.0))))
        assertNull(mapDevice(GnssSensorStatus(fixType = "NO_FIX", latitude = 37.0, longitude = 127.0)))
    }

    @Test fun everyModeHasItsOwnTitleAndParameter() {
        assertEquals(listOf("수집 구역", "수집 중", "수집 경로", "장치 위치"), GeoMapMode.entries.map { it.title })
        GeoMapMode.entries.forEach { assertEquals(it, GeoMapMode.fromParameter(it.parameter)) }
        assertNull(GeoMapMode.fromParameter("unknown"))
    }

    @Test fun stalePositionUsesSampleAgeOnTheDeviceClockAndSuppressesInsideBadge() {
        val gnss = GnssSensorStatus(connected = true, lastSampleAtEpochMillis = 1_000L)
        assertFalse(mapDeviceStale(gnss, true, true, 11_000L))
        assertTrue(mapDeviceStale(gnss, true, true, 11_001L))
        assertTrue(mapDeviceStale(gnss, false, true, 2_000L))
        assertTrue(mapDeviceStale(gnss, true, false, 2_000L))
        assertTrue(mapDeviceStale(gnss.copy(lastSampleAtEpochMillis = null), true, true, 2_000L))
        val stale = GeoMapData(geometry = geometry(), deviceStale = true, zone = GeoMapZoneSnapshot(GeoZoneState.INSIDE))
        assertEquals("위치 미확인", zoneBadge(GeoMapMode.RUN, stale)?.first)
        assertNull(zoneBadge(GeoMapMode.ZONE, stale))
    }
}
