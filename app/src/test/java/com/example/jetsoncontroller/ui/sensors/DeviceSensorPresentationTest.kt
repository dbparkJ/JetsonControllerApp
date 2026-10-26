package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.CameraSensorStatus
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.model.PositionQuality
import com.example.jetsoncontroller.ui.model.SensorState
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSensorPresentationTest {
    private val active = SensorPresentation(SensorActivity.ACTIVE, "수신 중", "센서 데이터 수신 중")

    @Test fun `camera sensor badges preserve actual sample delay and recovery independently of preview`() {
        val status = JetsonStatus(
            sensorTelemetryAvailable = true, sensorTelemetryFresh = true,
            cameraSensor = CameraSensorStatus(configured = true, connected = true, active = true,
                lastFrameAtEpochMillis = 1_000L)
        )
        fun cameraState(value: JetsonStatus, now: Long) =
            sensorPresentations(value, true, now).camera.activity.deviceSensorState()

        assertEquals(SensorState.RECEIVING, cameraState(status, 16_000L))
        assertEquals(SensorState.DELAYED, cameraState(status, 16_001L))
        val recovered = status.copy(cameraSensor = status.cameraSensor.copy(lastFrameAtEpochMillis = 16_001L))
        assertEquals(SensorState.RECEIVING, cameraState(recovered, 16_001L))
        assertEquals(SensorState.DELAYED, cameraState(recovered.copy(sensorTelemetryFresh = false), 16_001L))
        assertEquals(SensorState.UNKNOWN, cameraState(recovered.copy(sensorTelemetryAvailable = false), 16_001L))
    }

    @Test fun `receiving GNSS without coordinates is unknown even if RTK claims a fix`() {
        listOf(
            GnssSensorStatus(),
            GnssSensorStatus(fixType = "rtk_fixed", rtkStatus = "fixed"),
            GnssSensorStatus(latitude = Double.NaN, longitude = 127.0),
            GnssSensorStatus(latitude = 91.0, longitude = 127.0)
        ).forEach { gnss ->
            assertEquals(PositionQuality.UNKNOWN, deviceGnssState(active, gnss))
            assertEquals("위치 미확인", deviceGnssWord(active, gnss))
        }
    }

    @Test fun `valid positions use the quality word and tone together`() {
        val position = GnssSensorStatus(latitude = 37.0, longitude = 127.0, fixType = "gps")
        assertEquals(PositionQuality.APPROXIMATE, deviceGnssState(active, position))
        assertEquals(PositionQuality.APPROXIMATE,
            deviceGnssState(active, position.copy(fixType = "rtk_float")))
        assertEquals(PositionQuality.PRECISE,
            deviceGnssState(active, position.copy(fixType = "rtk_fixed")))
        assertEquals(PositionQuality.PRECISE,
            deviceGnssState(active, position.copy(rtkStatus = "fixed")))
    }

    @Test fun `sensor activity tones match the vocabulary including disconnection`() {
        val expected = mapOf(
            SensorActivity.ACTIVE to SensorState.RECEIVING,
            SensorActivity.STALE to SensorState.DELAYED,
            SensorActivity.DISCONNECTED to SensorState.DISCONNECTED,
            SensorActivity.CONNECTED to SensorState.WAITING,
            SensorActivity.WAITING to SensorState.UNKNOWN,
            SensorActivity.UNCONFIGURED to SensorState.NOT_CONFIGURED
        )
        expected.forEach { (activity, state) ->
            assertEquals(state, activity.deviceSensorState())
            assertEquals(state.tone, activity.deviceSensorTone())
            if (activity != SensorActivity.ACTIVE) {
                val presentation = SensorPresentation(activity, state.word, "")
                assertEquals(state, deviceGnssState(presentation,
                    GnssSensorStatus(latitude = 37.0, longitude = 127.0, fixType = "rtk_fixed")))
            }
        }
    }
}
