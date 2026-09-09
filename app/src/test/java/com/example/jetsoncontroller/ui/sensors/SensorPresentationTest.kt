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
    fun exposesOnlyTheFourRequestedGnssLabels() {
        assertEquals(
            "GNSS 수신 상태 미확인",
            gnssReceptionLabel(false, "rtk_fixed", "fixed")
        )
        assertEquals("RTK FIX 미확인", gnssReceptionLabel(true, "gps"))
        assertEquals("RTK FIX 미확인", gnssReceptionLabel(true, "dgps"))
        assertEquals("RTK FLOAT", gnssReceptionLabel(true, "rtk_float"))
        assertEquals("RTK FIX 확인", gnssReceptionLabel(true, "rtk_fixed"))
    }

    @Test
    fun fallsBackToExplicitRtkStatusWhenFixTypeIsGeneric() {
        assertEquals("RTK FLOAT", gnssReceptionLabel(true, "gps", "float"))
        assertEquals("RTK FIX 확인", gnssReceptionLabel(true, "dgps", "fixed"))
        assertEquals("RTK FIX 확인", gnssReceptionLabel(true, "rtk_fix"))
    }

    @Test
    fun explicitRtkStatusOverridesConflictingFixType() {
        assertEquals("RTK FIX 미확인", gnssReceptionLabel(true, "rtk_fixed", "off"))
        assertEquals("RTK FLOAT", gnssReceptionLabel(true, "rtk_fixed", "float"))
        assertEquals("RTK FIX 확인", gnssReceptionLabel(true, "rtk_float", "fixed"))
    }

    @Test
    fun connectedGnssIsAvailableWhileWaitingForSamples() {
        assertTrue(
            effectiveGnssAvailability(
                deviceOnline = true,
                telemetryAvailable = true,
                telemetryFresh = true,
                sensorConnected = true,
                sensorActive = false,
                legacyRunning = false
            )
        )
        assertFalse(
            effectiveGnssAvailability(
                deviceOnline = true,
                telemetryAvailable = true,
                telemetryFresh = true,
                sensorConnected = false,
                sensorActive = false,
                legacyRunning = false
            )
        )
    }

    @Test
    fun deviceLocationStateUsesOfflineStaleOffPrecedence() {
        assertEquals(
            DeviceLocationAvailability.OFFLINE,
            deviceLocationAvailability(
                deviceOnline = false,
                telemetryFresh = false,
                gnssAvailable = false,
                hasValidLocation = false
            )
        )
        assertEquals(
            DeviceLocationAvailability.STALE,
            deviceLocationAvailability(
                deviceOnline = true,
                telemetryFresh = false,
                gnssAvailable = false,
                hasValidLocation = false
            )
        )
        assertEquals(
            DeviceLocationAvailability.OFF,
            deviceLocationAvailability(
                deviceOnline = true,
                telemetryFresh = true,
                gnssAvailable = false,
                hasValidLocation = true
            )
        )
        assertEquals(
            DeviceLocationAvailability.NO_FIX,
            deviceLocationAvailability(
                deviceOnline = true,
                telemetryFresh = true,
                gnssAvailable = true,
                hasValidLocation = false
            )
        )
        assertEquals(
            DeviceLocationAvailability.ACTIVE,
            deviceLocationAvailability(
                deviceOnline = true,
                telemetryFresh = true,
                gnssAvailable = true,
                hasValidLocation = true
            )
        )
    }

    @Test
    fun validatesMapCoordinates() {
        assertTrue(GnssSensorStatus(latitude = 37.5, longitude = 127.0).hasValidLocation())
        assertFalse(GnssSensorStatus(latitude = 91.0, longitude = 127.0).hasValidLocation())
        assertFalse(
            GnssSensorStatus(latitude = Double.NaN, longitude = 127.0).hasValidLocation()
        )
    }
}
