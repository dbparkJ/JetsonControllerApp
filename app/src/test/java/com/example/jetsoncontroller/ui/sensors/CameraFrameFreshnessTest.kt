package com.example.jetsoncontroller.ui.sensors
import org.junit.Assert.*
import org.junit.Test
class CameraFrameFreshnessTest {
    @Test fun sensorActivityAloneCannotClaimLiveVideo() {
        assertFalse(cameraFrameIsLive(true, false, null, 10000, false))
        assertFalse(cameraFrameIsLive(true, true, 1000, 5001, false))
        assertFalse(cameraFrameIsLive(true, true, 1000, 1001, true))
        assertFalse(cameraFrameIsLive(false, true, 1000, 1001, false))
        assertTrue(cameraFrameIsLive(true, true, 1000, 5000, false))
    }

    @Test fun frameAgeRequiresAnObservedTimestampAndExpiresAtTheExistingBoundary() {
        assertFalse(cameraFrameIsLive(true, true, null, 10_000L, false))
        assertFalse(cameraFrameIsLive(true, true, 10_001L, 10_000L, false))
        assertTrue(cameraFrameIsLive(true, true, 10_000L, 10_000L, false))
        assertTrue(cameraFrameIsLive(true, true, 10_000L, 14_000L, false))
        assertFalse(cameraFrameIsLive(true, true, 10_000L, 14_001L, false))
        assertTrue(cameraFrameIsLive(true, true, 14_001L, 14_001L, false))
    }
}
