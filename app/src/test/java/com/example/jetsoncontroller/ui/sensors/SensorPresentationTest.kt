package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.CameraSensorStatus
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.ImuSensorStatus
import com.example.jetsoncontroller.model.JetsonStatus
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
            legacyRunning = false,
            deviceOnline = true,
            lastSampleAtEpochMillis = 1_000L,
            nowEpochMillis = 2_000L
        )

        assertEquals(SensorActivity.ACTIVE, state.activity)
        assertEquals("수신 중", state.badge)
    }

    @Test
    fun staleBridgeOverridesPreviouslyActiveSensor() {
        val state = sensorPresentation(
            configured = true,
            connected = false,
            active = false,
            telemetryAvailable = true,
            telemetryFresh = false,
            legacyRunning = true,
            deviceOnline = true
        )

        assertEquals(SensorActivity.STALE, state.activity)
    }

    @Test
    fun aFreshStatusResponseCannotRefreshAnOldSensorSample() {
        val status = activeStatus(sampleAt = 1_000L).copy(
            collectedAtEpochMillis = 20_000L,
            sensorTelemetryUpdatedAtEpochMillis = 20_000L,
            sensorTelemetryAgeSeconds = 0f
        )
        val states = sensorPresentations(status, deviceOnline = true, nowEpochMillis = 20_000L)
        listOf(states.camera, states.gnss, states.imu).forEach {
            assertEquals(SensorActivity.STALE, it.activity)
            assertEquals("지연", it.badge)
        }
    }

    @Test
    fun sampleFreshnessUsesTheExistingCurrentBoundary() {
        val status = activeStatus(sampleAt = 1_000L)
        val atBoundary = sensorPresentations(status, true, 16_000L)
        val pastBoundary = sensorPresentations(status, true, 16_001L)
        listOf(atBoundary.camera, atBoundary.gnss, atBoundary.imu).forEach {
            assertEquals("수신 중", it.badge)
        }
        listOf(pastBoundary.camera, pastBoundary.gnss, pastBoundary.imu).forEach {
            assertEquals("지연", it.badge)
        }
    }

    @Test
    fun offlineDevicesAndMissingTelemetryNeverReuseActiveOrServiceRunningFlags() {
        val status = activeStatus(sampleAt = 1_000L).copy(
            cameraRunning = true,
            gnssRunning = true,
            imuRunning = true
        )
        listOf(
            sensorPresentations(status, false, 2_000L),
            sensorPresentations(status.copy(sensorTelemetryAvailable = false), true, 2_000L),
            sensorPresentations(JetsonStatus(), false, 2_000L)
        ).forEach { states ->
            listOf(states.camera, states.gnss, states.imu).forEach {
                assertEquals("미확인", it.badge)
                assertEquals(SensorActivity.WAITING, it.activity)
            }
        }
    }

    @Test
    fun eachSensorUsesItsOwnSampleTimeRatherThanAnotherSensorOrTheBridgeTime() {
        val status = activeStatus(sampleAt = 20_000L).copy(
            cameraSensor = CameraSensorStatus(configured = true, connected = true, active = true, lastFrameAtEpochMillis = 1_000L),
            imuSensor = ImuSensorStatus(configured = true, connected = true, active = true)
        )
        val states = sensorPresentations(status, true, 20_000L)
        assertEquals("지연", states.camera.badge)
        assertEquals("수신 중", states.gnss.badge)
        assertEquals("미확인", states.imu.badge)
    }

    @Test
    fun sensorVocabularyCoversConnectedDisconnectedUnconfiguredAndStaleTelemetry() {
        val connected = sensorPresentation(
            configured = true, connected = true, active = false,
            telemetryAvailable = true, telemetryFresh = true, legacyRunning = false,
            deviceOnline = true, nowEpochMillis = 2_000L
        )
        assertEquals(SensorActivity.CONNECTED, connected.activity)
        assertEquals("대기 중", connected.badge)

        val disconnected = sensorPresentation(
            configured = true, connected = false, active = false,
            telemetryAvailable = true, telemetryFresh = true, legacyRunning = true,
            deviceOnline = true, nowEpochMillis = 2_000L
        )
        assertEquals(SensorActivity.DISCONNECTED, disconnected.activity)
        assertEquals("미연결", disconnected.badge)

        val unconfigured = sensorPresentation(
            configured = false, connected = true, active = true,
            telemetryAvailable = true, telemetryFresh = false, legacyRunning = true,
            deviceOnline = true, nowEpochMillis = 2_000L
        )
        assertEquals(SensorActivity.UNCONFIGURED, unconfigured.activity)
        assertEquals("미설정", unconfigured.badge)

        val states = sensorPresentations(activeStatus(1_000L).copy(sensorTelemetryFresh = false), true, 2_000L)
        listOf(states.camera, states.gnss, states.imu).forEach { assertEquals("지연", it.badge) }
    }

    @Test
    fun futureOrMissingSamplesCannotClaimActiveReception() {
        listOf(null, 3_000L).forEach { sampleAt ->
            val states = sensorPresentations(activeStatus(sampleAt), true, 2_000L)
            listOf(states.camera, states.gnss, states.imu).forEach { assertEquals("미확인", it.badge) }
        }
    }

    @Test
    fun idleConnectedSensorsCanWaitWithoutProducingNewSamples() {
        val state = sensorPresentation(
            configured = true, connected = true, active = false,
            telemetryAvailable = true, telemetryFresh = true, legacyRunning = false,
            deviceOnline = true, lastSampleAtEpochMillis = 1_000L, nowEpochMillis = 20_000L
        )
        assertEquals("대기 중", state.badge)
    }

    @Test
    fun adapterAcceptsTopLevelConfigurationFlagsForEachSensor() {
        val status = activeStatus(1_000L).copy(
            cameraConfigured = true,
            gnssConfigured = true,
            imuConfigured = true,
            cameraSensor = CameraSensorStatus(connected = true, active = true, lastFrameAtEpochMillis = 1_000L),
            gnssSensor = GnssSensorStatus(connected = true, active = true, lastSampleAtEpochMillis = 1_000L),
            imuSensor = ImuSensorStatus(connected = true, active = true, lastSampleAtEpochMillis = 1_000L)
        )
        val states = sensorPresentations(status, true, 2_000L)
        listOf(states.camera, states.gnss, states.imu).forEach { assertEquals("수신 중", it.badge) }
    }

    private fun activeStatus(sampleAt: Long?) = JetsonStatus(
        sensorTelemetryAvailable = true,
        sensorTelemetryFresh = true,
        cameraSensor = CameraSensorStatus(configured = true, connected = true, active = true, lastFrameAtEpochMillis = sampleAt),
        gnssSensor = GnssSensorStatus(configured = true, connected = true, active = true, lastSampleAtEpochMillis = sampleAt),
        imuSensor = ImuSensorStatus(configured = true, connected = true, active = true, lastSampleAtEpochMillis = sampleAt)
    )

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
