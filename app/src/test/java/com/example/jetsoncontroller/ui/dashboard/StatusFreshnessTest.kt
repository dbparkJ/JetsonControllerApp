package com.example.jetsoncontroller.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusFreshnessTest {
    @Test
    fun `dashboard polls system metrics once per second`() {
        assertEquals(1_000L, STATUS_POLL_INTERVAL_MS)
    }

    @Test
    fun missingTimestamp_isUnknown() {
        assertEquals(StatusFreshness.UNKNOWN, statusFreshness(null, 20_000L))
    }

    @Test
    fun recentTimestamp_isCurrent() {
        assertEquals(StatusFreshness.CURRENT, statusFreshness(10_000L, 24_999L))
    }

    @Test
    fun oldTimestamp_isStale() {
        assertEquals(StatusFreshness.STALE, statusFreshness(10_000L, 25_001L))
        assertEquals(15L, statusAgeSeconds(10_000L, 25_001L))
    }
}
