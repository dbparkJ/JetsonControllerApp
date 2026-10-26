package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.CameraSensorStatus
import org.junit.Assert.*
import org.junit.Test

class CameraStagePresentationTest {
    @Test fun `first frame errors and stalled received frames have separate states`() {
        assertEquals(CameraStageState.WAITING, cameraStageState(true, false, null, 10_000, false))
        assertEquals(CameraStageState.ERROR, cameraStageState(true, false, null, 10_000, true))
        assertEquals(CameraStageState.LIVE, cameraStageState(true, true, 7_000, 10_000, false))
        assertEquals(CameraStageState.DELAYED, cameraStageState(true, true, 7_000, 10_001, false))
        assertEquals(CameraStageState.DELAYED, cameraStageState(false, true, 9_999, 10_000, false))
        assertEquals(CameraStageState.DELAYED, cameraStageState(true, true, 10_001, 10_000, false))
    }

    @Test fun `sensor output dimensions take precedence as a complete pair`() {
        assertEquals("1280 × 800", cameraResolution(CameraSensorStatus(
            frameWidth = 1920, frameHeight = 1200, sensorOutputWidth = 1280, sensorOutputHeight = 800)))
        assertEquals("1920 × 1200", cameraResolution(CameraSensorStatus(
            frameWidth = 1920, frameHeight = 1200)))
        assertEquals("1920 × 1200", cameraResolution(CameraSensorStatus(
            frameWidth = 1920, frameHeight = 1200, sensorOutputWidth = 1280, sensorOutputHeight = 0)))
        assertEquals("1280 × 800", cameraResolution(CameraSensorStatus(sensorOutputWidth = 1280, sensorOutputHeight = 800)))
    }

    @Test fun `capture requires a current frame an idle command and at least one destination`() {
        CameraStageState.entries.forEach { stage ->
            assertEquals(stage == CameraStageState.LIVE, cameraCaptureEnabled(stage, false, true, false))
            assertFalse(cameraCaptureEnabled(stage, true, true, true))
            assertFalse(cameraCaptureEnabled(stage, false, false, false))
        }
        assertTrue(cameraCaptureEnabled(CameraStageState.LIVE, false, false, true))
    }

    @Test fun `preview stays with its opener and cannot follow a new current device`() {
        assertTrue(cameraTargetCanStream(true, true, "A", "a", "A"))
        assertFalse(cameraTargetCanStream(true, true, "A", "B", "B"))
        assertFalse(cameraTargetCanStream(true, true, "A", "A", "B"))
        assertFalse(cameraTargetCanStream(false, true, "A", "A", "A"))
        assertFalse(cameraTargetCanStream(true, true, null, null, null))
        assertNull(cameraResolution(CameraSensorStatus(frameWidth = 1920, frameHeight = 0)))
        assertEquals("1920 × 1080", cameraResolution(CameraSensorStatus(frameWidth = 1920, frameHeight = 1080)))
    }
}
