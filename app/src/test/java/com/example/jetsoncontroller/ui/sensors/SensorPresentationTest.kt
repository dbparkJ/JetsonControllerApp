package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.GnssSensorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorPresentationTest {
    @Test
    fun freshHeartbeatMarksSensorActive() {
        val state = sensorPresentation(
            configured = true,
            connected = true,
            active = true,
            telemetryAvailable = true,
            telemetryFresh = true,
            legacyRunning = false
        )

        assertEquals(SensorActivity.ACTIVE, state.activity)
        assertEquals("활성", state.badge)
    }

    @Test
    fun staleBridgeOverridesPreviouslyActiveSensor() {
        val state = sensorPresentation(
            configured = true,
            connected = false,
            active = false,
            telemetryAvailable = true,
            telemetryFresh = false,
            legacyRunning = true
        )

        assertEquals(SensorActivity.STALE, state.activity)
    }

    @Test
    fun labelsEveryRequestedGnssFixMode() {
        assertEquals("GPS", gnssFixLabel("gps"))
        assertEquals("DGPS", gnssFixLabel("dgps"))
        assertEquals("RTK Fix", gnssFixLabel("rtk_fixed"))
        assertEquals("RTK Float", gnssFixLabel("rtk_float"))
    }

    @Test
    fun validatesMapCoordinates() {
        assertTrue(GnssSensorStatus(latitude = 37.5, longitude = 127.0).hasValidLocation())
        assertFalse(GnssSensorStatus(latitude = 91.0, longitude = 127.0).hasValidLocation())
    }
}
