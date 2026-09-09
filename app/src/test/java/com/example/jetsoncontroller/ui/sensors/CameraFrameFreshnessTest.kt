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
}
