package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StripPriorityTest {
    private val priority = listOf(GeoStripKind.AUTHENTICATION_ERROR, GeoStripKind.DISCONNECTED,
        GeoStripKind.OUTSIDE_ZONE, GeoStripKind.COLLECTING, GeoStripKind.LAST_SYNC)

    private fun strip(kind: GeoStripKind) = GeoStatusStripState(kind, "GEO-X-1", kind.name)

    @Test
    fun emptyListHidesTheStrip() {
        assertNull(highestPriorityStrip(emptyList()))
    }

    @Test
    fun everyCombinationUsesTheSpecifiedPriorityRegardlessOfInputOrder() {
        for (mask in 1 until (1 shl priority.size)) {
            val kinds = priority.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val candidates = kinds.map(::strip)
            assertEquals("subset $mask", kinds.first(), highestPriorityStrip(candidates)?.kind)
            assertEquals("reversed subset $mask", kinds.first(), highestPriorityStrip(candidates.reversed())?.kind)
        }
    }

    @Test
    fun equalPriorityKeepsTheFirstSuppliedTarget() {
        val first = strip(GeoStripKind.DISCONNECTED)
        val second = first.copy(deviceName = "GEO-X-2")
        assertSame(first, highestPriorityStrip(listOf(first, second)))
    }

    @Test
    fun ongoingRunRemainsAnEntryWhenOnlyServerFreshnessIsAlsoPresent() {
        assertEquals(GeoStripKind.COLLECTING, highestPriorityStrip(listOf(strip(GeoStripKind.LAST_SYNC), strip(GeoStripKind.COLLECTING)))?.kind)
    }

    @Test
    fun textAlwaysStartsWithTheFullDeviceName() {
        val state = GeoStatusStripState(GeoStripKind.DISCONNECTED, "GEO-X-현장지원차량-2", "연결이 끊겼습니다 · 마지막 응답 5분 전")
        assertEquals("GEO-X-현장지원차량-2 · 연결이 끊겼습니다 · 마지막 응답 5분 전", state.text)
    }
}
