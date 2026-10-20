package com.example.jetsoncontroller.ui.map

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.model.RoutePoint
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.ui.theme.GeoPalette
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

    @Test fun lineStylesKeepTheEarlierCasingsDashesAndColors() {
        listOf(GeoMapLineStyle.RANGE, GeoMapLineStyle.AREA, GeoMapLineStyle.ROUTE).forEach {
            assertEquals(MapBlue, it.color)
            assertEquals(2f, it.widthDp, 0f)
            assertEquals(5f, it.casingDp, 0f)
        }
        assertEquals(listOf(6f, 4f), GeoMapLineStyle.RANGE.dashDp)
        assertNull(GeoMapLineStyle.AREA.dashDp)
        assertNull(GeoMapLineStyle.ROUTE.dashDp)
        val trail = listOf(GeoMapLineStyle.PRECISE, GeoMapLineStyle.APPROXIMATE, GeoMapLineStyle.UNKNOWN)
        assertEquals(listOf(MapPrecise, MapApproximate, MapUnknown), trail.map { it.color })
        trail.forEach {
            assertEquals(4f, it.widthDp, 0f)
            assertEquals(7f, it.casingDp, 0f)
        }
        assertEquals(listOf(null, listOf(8f, 4f), listOf(2f, 4f)), trail.map { it.dashDp })
    }

    @Test fun darkPalettesUseNightModeOnTheGeneralMapOnly() {
        val night = mapAppearance(GeoMapBase.GENERAL, GeoPalette.GRAPHITE, baseMap = true)
        assertTrue(night.night)
        assertFalse(night.satellite)
        assertEquals(.22f, night.rangeFillOpacity, .0001f)
        val satellite = mapAppearance(GeoMapBase.SATELLITE, GeoPalette.GRAPHITE, baseMap = true)
        assertFalse(satellite.night)
        assertTrue(satellite.satellite)
        assertEquals(.16f, satellite.rangeFillOpacity, .0001f)
        assertFalse(mapAppearance(GeoMapBase.GENERAL, GeoPalette.STANDARD, baseMap = true).night)
        assertTrue(GeoPalette.entries.filter { it.isDark }.all { mapAppearance(GeoMapBase.GENERAL, it, baseMap = true).night })
        assertEquals(.16f, mapAppearance(GeoMapBase.GENERAL, GeoPalette.NAVY_DARK, baseMap = false).rangeFillOpacity, .0001f)
    }

    @Test fun zoneShapesUseNaverWindingWithoutTheClosingPoint() {
        val shapes = mapZoneShapes(geometry().sourceJson)
        assertEquals(1, shapes.routes.size)
        assertEquals(listOf(GeoMapPosition(37.0, 127.0)), shapes.points)
        // Features come first (one area), then the range.
        assertEquals(listOf(false, true), shapes.polygons.map { it.range })
        shapes.polygons.forEach {
            assertEquals(3, it.exterior.size)
            assertTrue("NAVER exteriors run clockwise", clockwise(it.exterior))
        }
        val holed = mapZoneShapes(mapGeometry(ServerTicket("ticket", "구간", range = JsonParser.parseString("""{"type":"MultiPolygon","coordinates":[
          [[[127,37],[128,37],[128,38],[127,38],[127,37]],[[127.1,37.1],[127.1,37.2],[127.2,37.2],[127.2,37.1],[127.1,37.1]]],
          [[[129,37],[130,37],[130,38],[129,37]]]
        ]}"""))).sourceJson)
        assertEquals(2, holed.polygons.size)
        assertTrue(holed.polygons.all { it.range && clockwise(it.exterior) })
        val hole = holed.polygons.first().holes.single()
        assertEquals(4, hole.size)
        assertFalse("NAVER holes run counter-clockwise", clockwise(hole))
        assertNull(naverRing(listOf(GeoMapPosition(37.0, 127.0), GeoMapPosition(37.1, 127.1), GeoMapPosition(37.0, 127.0)), clockwise = true))
        assertEquals(GeoMapZoneShapes(), mapZoneShapes("not json"))
    }

    private fun clockwise(ring: List<GeoMapPosition>) = ring.indices.sumOf { i ->
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        a.longitude * b.latitude - b.longitude * a.latitude
    } < 0

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
        fun styles(data: GeoMapData) = mapTrail(data).runs.map { it.style }
        assertEquals(listOf(GeoMapLineStyle.PRECISE, GeoMapLineStyle.APPROXIMATE, GeoMapLineStyle.UNKNOWN), styles(GeoMapData(trail = route)))
        val gapped = mapTrail(GeoMapData(trail = route, zone = GeoMapZoneSnapshot(gaps = listOf(GeoMapGap(2_000, 3_000)))))
        assertEquals(listOf(GeoMapLineStyle.PRECISE, GeoMapLineStyle.UNKNOWN), gapped.runs.map { it.style })
        assertEquals(listOf(GeoMapPosition(37.01, 127.01), GeoMapPosition(37.02, 127.02)), gapped.gaps)
        assertTrue(styles(GeoMapData(trail = route.take(2).map { it.copy(fixState = "NO_FIX") })).isEmpty())
        assertTrue(styles(GeoMapData(trail = listOf(route.first(), route[1].copy(latitude = Double.NaN)))).isEmpty())
    }

    @Test fun segmentsOfOneQualityShareALine() {
        val fixed = (0 until 5).map { RoutePoint(37.0 + it * .001, 127.0, 1_000L * (it + 1), fixState = "FIXED") }
        val run = mapTrail(GeoMapData(trail = fixed)).runs.single()
        assertEquals(GeoMapLineStyle.PRECISE, run.style)
        assertEquals(fixed.map { GeoMapPosition(it.latitude, it.longitude) }, run.points)
    }

    @Test fun staleAndFreshDeviceMarksDifferAndUncertaintyStartsAtThreeMeters() {
        val fresh = GeoMapData(device = GeoMapPosition(37.0, 127.0), uncertaintyM = 2.99)
        assertEquals(GeoMapDeviceMark(GeoMapPosition(37.0, 127.0), stale = false, uncertaintyM = null), mapDeviceMark(fresh))
        assertTrue(mapDeviceMark(fresh.copy(deviceStale = true))!!.stale)
        assertEquals(3.0, mapDeviceMark(fresh.copy(uncertaintyM = 3.0))!!.uncertaintyM!!, 0.0)
        assertNull(mapDeviceMark(fresh.copy(uncertaintyM = Double.NaN))!!.uncertaintyM)
        assertNull(mapDeviceMark(GeoMapData(device = GeoMapPosition(91.0, 127.0))))
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
