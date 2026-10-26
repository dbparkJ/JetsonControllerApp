package com.example.jetsoncontroller.ui.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPreviewPolicyTest {
    @Test
    fun previewRefreshRateMatchesBridgeRate() {
        assertEquals(67L, CAMERA_PREVIEW_LEGACY_INTERVAL_MS)
        assertEquals(1_000L, CAMERA_RETRY_INTERVAL_MS)
    }

    @Test
    fun framesBetweenClockTicksStayLive() {
        // The Compose clock ticks once a second, while frames arrive more often.
        for (receivedAt in 10_000L..12_000L step 100) {
            val tick = receivedAt / 1_000L * 1_000L
            assertEquals("Frame received at $receivedAt, UI tick $tick",
                CameraStageState.LIVE,
                cameraStageState(true, true, receivedAt, tick, false, checkedAt = receivedAt))
        }
    }

    @Test
    fun unchangedFrameResponsesDoNotHideStallsAndANewFrameRecoversImmediately() {
        val receivedAt = 10_100L
        // A successful long-poll with the same revision updates checkedAt only.
        assertEquals(CameraStageState.LIVE,
            cameraStageState(true, true, receivedAt, 14_000L, false, checkedAt = 14_100L))
        assertEquals(CameraStageState.DELAYED,
            cameraStageState(true, true, receivedAt, 14_000L, false, checkedAt = 14_101L))
        assertEquals(CameraStageState.DELAYED,
            cameraStageState(true, true, receivedAt, 16_000L, false, checkedAt = 16_100L))
        // Recovery does not wait for the next UI tick.
        assertEquals(CameraStageState.LIVE,
            cameraStageState(true, true, 16_200L, 16_000L, false, checkedAt = 16_200L))
        // With no further responses, clock ticks still expire the recovered frame.
        assertEquals(CameraStageState.DELAYED,
            cameraStageState(true, true, 16_200L, 20_201L, false, checkedAt = 16_200L))
    }

    @Test
    fun localResponseTimeCannotOverrideErrorsInactiveSensorsOrInvalidFrameTimes() {
        assertEquals(CameraStageState.DELAYED,
            cameraStageState(true, true, 10_100L, 10_000L, true, checkedAt = 10_100L))
        assertEquals(CameraStageState.DELAYED,
            cameraStageState(false, true, 10_100L, 10_000L, false, checkedAt = 10_100L))
        assertEquals(CameraStageState.DELAYED,
            cameraStageState(true, true, 10_101L, 10_000L, false, checkedAt = 10_100L))
        assertEquals(CameraStageState.WAITING,
            cameraStageState(true, false, null, 10_000L, false, checkedAt = 10_100L))
    }
}
