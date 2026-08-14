package com.example.jetsoncontroller.ui.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPreviewPolicyTest {
    @Test
    fun previewRefreshRateMatchesBridgeRate() {
        assertEquals(250L, CAMERA_PREVIEW_INTERVAL_MS)
        assertEquals(1_000L, CAMERA_RETRY_INTERVAL_MS)
    }
}
