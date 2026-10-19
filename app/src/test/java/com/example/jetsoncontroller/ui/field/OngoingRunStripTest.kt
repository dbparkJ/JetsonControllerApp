package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.StopTarget
import com.example.jetsoncontroller.ui.components.geo.GeoStatusStripState
import com.example.jetsoncontroller.ui.components.geo.GeoStripKind
import com.example.jetsoncontroller.ui.components.geo.highestPriorityStrip
import org.junit.Assert.*
import org.junit.Test

class OngoingRunStripTest {
    private val run = runStopFixture()

    @Test fun `F4 one action opens the immutable run target`() {
        var target: StopTarget? = null
        val strip = ongoingRunStripState("device-a", "GEO-X-1", run, { target = it })!!
        assertEquals("GEO-X-1 · 신월4거리 북측 보조 구간 수집 중 · 00:12:34", strip.text)
        assertEquals("진행 보기", strip.actions.single().label)
        strip.actions.single().onClick()
        assertEquals(runTarget(run), target)
    }

    @Test fun `no entry for another device a terminal run or an unconfirmed start`() {
        assertNull(ongoingRunStripState("other", "GEO-X-1", run, {}))
        assertNull(ongoingRunStripState(null, "GEO-X-1", run, {}))
        assertNull(ongoingRunStripState("device-a", "", run, {}))
        assertNull(ongoingRunStripState("device-a", "GEO-X-1", run.copy(active = false), {}))
        assertNull(ongoingRunStripState("device-a", "GEO-X-1", run, {}, awaitingStart = true))
        assertNull(ongoingRunStripState("device-a", "GEO-X-1", run, {}, workRoot = true))
    }

    @Test fun `shared entry participates in the single strip priority list`() {
        val entry = ongoingRunStripState("device-a", "GEO-X-1", run, {})!!
        val outside = GeoStatusStripState(GeoStripKind.OUTSIDE_ZONE, "GEO-X-1", "구역 밖")
        val disconnected = GeoStatusStripState(GeoStripKind.DISCONNECTED, "GEO-X-1", "연결 끊김")
        val authentication = GeoStatusStripState(GeoStripKind.AUTHENTICATION_ERROR, "GEO-X-1", "인증 필요")
        val sync = GeoStatusStripState(GeoStripKind.LAST_SYNC, "GEO-X-1", "마지막 동기화")
        assertEquals(entry, highestPriorityStrip(listOf(sync, entry)))
        assertEquals(outside, highestPriorityStrip(listOf(entry, outside)))
        assertEquals(disconnected, highestPriorityStrip(listOf(entry, outside, disconnected)))
        assertEquals(authentication, highestPriorityStrip(listOf(entry, disconnected, authentication)))
    }
}
