package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.JetsonStatus
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraResolutionContractTest {
    @Test fun sensorOutputFromStatusJsonIsDisplayedBeforeStoredFrameSize() {
        val status = Gson().fromJson("""{"cameraSensor":{
            "frameWidth":1920,"frameHeight":1200,"sensorOutputWidth":1280,"sensorOutputHeight":800
        }}""", JetsonStatus::class.java)
        assertEquals("1280 × 800", cameraResolution(status.cameraSensor))
        assertEquals(1920, status.cameraSensor.frameWidth)
    }

    @Test fun olderStatusJsonKeepsItsOriginalResolution() {
        val status = Gson().fromJson("""{"cameraSensor":{"frameWidth":1920,"frameHeight":1200}}""",
            JetsonStatus::class.java)
        assertNull(status.cameraSensor.sensorOutputWidth)
        assertNull(status.cameraSensor.sensorOutputHeight)
        assertEquals("1920 × 1200", cameraResolution(status.cameraSensor))
    }
}
