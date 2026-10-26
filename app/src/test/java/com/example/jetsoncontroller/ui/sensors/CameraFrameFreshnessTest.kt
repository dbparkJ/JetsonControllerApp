package com.example.jetsoncontroller.ui.sensors
import org.junit.Assert.*
import org.junit.Test
class CameraFrameFreshnessTest {
    @Test fun sensorActivityAloneCannotClaimLiveVideo() {
        assertFalse(cameraFrameIsLive(true, false, null, 10000, false))
        assertFalse(cameraFrameIsLive(true, true, 1000, 5001, false))
        assertTrue(cameraFrameIsLive(true, true, 1000, 1001, true))
        assertFalse(cameraFrameIsLive(false, true, 1000, 1001, false))
        assertTrue(cameraFrameIsLive(true, true, 1000, 1000 + CAMERA_FRAME_MIN_STALE_MS, false))
    }

    @Test fun frameAgeRequiresAnObservedTimestampAndExpiresAtTheNamedBoundary() {
        assertFalse(cameraFrameIsLive(true, true, null, 10_000L, false))
        assertFalse(cameraFrameIsLive(true, true, 10_001L, 10_000L, false))
        assertTrue(cameraFrameIsLive(true, true, 10_000L, 10_000L, false))
        assertTrue(cameraFrameIsLive(true, true, 10_000L, 10_000L + CAMERA_FRAME_MIN_STALE_MS, false))
        assertFalse(cameraFrameIsLive(true, true, 10_000L, 10_001L + CAMERA_FRAME_MIN_STALE_MS, false))
        assertTrue(cameraFrameIsLive(true, true, 14_001L, 14_001L, false))
    }

    @Test fun oneSecondFramesWithJitterNeverAlternateBetweenLiveAndDelayed() {
        var freshness = CameraFrameFreshness().received(10_000)
        val intervals = listOf(980L, 1_170L, 850L, 1_250L, 920L, 1_080L)
        repeat(100) { index ->
            val next = freshness.receivedAt!! + intervals[index % intervals.size]
            for (tick in freshness.receivedAt!!..next step CAMERA_FRAME_CHECK_INTERVAL_MS) {
                freshness = freshness.checked(tick)
                assertFalse(freshness.delayed)
            }
            freshness = freshness.received(next)
            assertFalse(freshness.delayed)
        }
    }

    @Test fun anOutageNeedsTwoTimelyNewFramesToRecover() {
        var freshness = CameraFrameFreshness().received(10_000).received(11_000)
        freshness = freshness.checked(11_001 + freshness.staleAfterMillis)
        assertTrue(freshness.delayed)
        val intervalBeforeOutage = freshness.intervalMillis
        freshness = freshness.received(30_000)
        assertTrue(freshness.delayed)
        assertEquals(intervalBeforeOutage, freshness.intervalMillis)
        assertEquals(1, freshness.recoveryCount)
        // Repeated/unchanged responses and ticks do not count as recovery frames.
        freshness = freshness.received(30_000).checked(30_500)
        assertEquals(1, freshness.recoveryCount)
        freshness = freshness.received(31_000)
        assertFalse(freshness.delayed)
        assertEquals(CAMERA_FRAME_RECOVERY_COUNT, freshness.recoveryCount)
    }

    @Test fun aLongGapDetectedOnArrivalAlsoRequiresRecovery() {
        val firstRecovery = CameraFrameFreshness().received(10_000).received(30_000)
        assertTrue(firstRecovery.delayed)
        val lateRecovery = firstRecovery.received(33_000)
        assertTrue(lateRecovery.delayed)
        assertEquals(1, lateRecovery.recoveryCount)
        assertFalse(lateRecovery.received(34_000).delayed)
    }

    @Test fun observedIntervalsExtendTheThreshold() {
        var freshness = CameraFrameFreshness().received(1_000)
        repeat(12) { freshness = freshness.received(freshness.receivedAt!! + 2_000) }
        assertTrue(freshness.staleAfterMillis > CAMERA_FRAME_MIN_STALE_MS)
        assertEquals(freshness.intervalMillis * CAMERA_FRAME_INTERVAL_MULTIPLIER, freshness.staleAfterMillis)
        assertEquals(CameraStageState.LIVE,
            cameraStageState(true, true, freshness.receivedAt, freshness.receivedAt!! + 2_500,
                false, freshness = freshness))
    }
}
