package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.model.CameraSensorStatus
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MetricValidity
import com.example.jetsoncontroller.model.MobileRtkRelayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreparationStatusPresentationTest {
    @After fun resetMain() = Dispatchers.resetMain()

    private val telemetry = PreparationTelemetry("device", JetsonStatus(
        collectedAtEpochMillis = 100_500,
        storageTotalBytes = 100,
        storageAvailableBytes = 50,
        sensorTelemetryAvailable = true,
        sensorTelemetryFresh = true,
        cameraSensor = CameraSensorStatus(configured = true, connected = true, active = true,
            lastFrameAtEpochMillis = 100_500),
        gnssSensor = GnssSensorStatus(configured = true, connected = true, active = true,
            lastSampleAtEpochMillis = 100_500, fixType = "rtk_fixed", latitude = 37.5, longitude = 126.9)
    ), "직접 연결", "중계 등록됨")

    private fun rows(value: PreparationTelemetry, now: Long = 100_600, online: Boolean = true) =
        preparationReadiness(SurveyRunUiState(deviceId = "device", online = online,
            preparationTelemetry = value), now).associateBy { it.label }

    @Test fun `empty poll placeholders retain readings but never renew their freshness`() {
        val placeholder = telemetry.copy(status = JetsonStatus())
        val retained = retainPreparationStatus(telemetry, placeholder)
        assertEquals(rows(telemetry), rows(retained))
        assertEquals("수신 중", rows(retained).getValue("카메라").value)
        assertEquals("정밀 위치", rows(retained).getValue("위치 품질").value)
        val repeated = retainPreparationStatus(retained, placeholder)
        assertEquals(100_500L, repeated.status.collectedAtEpochMillis)
        assertEquals("미확인", rows(repeated, 115_501).getValue("저장 공간").value)
        assertEquals("미확인", rows(repeated, online = false).getValue("카메라").value)
        assertEquals("오프라인", rows(repeated, online = false).getValue("연결").value)
    }

    @Test fun `explicit sensor failure invalid storage and old samples replace healthy readings immediately`() {
        val failed = telemetry.copy(status = telemetry.status.copy(
            cameraSensor = telemetry.status.cameraSensor.copy(active = false, connected = false),
            gnssSensor = telemetry.status.gnssSensor.copy(lastSampleAtEpochMillis = 1),
            metricValidity = mapOf("storageAvailableBytes" to MetricValidity("invalid"))
        ), correctionRelay = "오류")
        val retained = retainPreparationStatus(telemetry, failed)
        assertEquals(failed, retained)
        assertEquals("미연결", rows(retained).getValue("카메라").value)
        assertEquals("위치 미확인", rows(retained).getValue("위치 품질").value)
        assertEquals("미확인", rows(retained).getValue("저장 공간").value)
        assertEquals("오류", rows(retained).getValue("보정 정보 중계").value)
    }

    @Test fun `another or cleared device never inherits readings`() {
        listOf("other", null).forEach { id ->
            val incoming = PreparationTelemetry(deviceId = id)
            assertEquals(incoming, retainPreparationStatus(telemetry, incoming))
        }
    }

    @Test fun `a fresh response between ticks requires the render clock and future timestamps remain unknown`() {
        // The old screen reused 100_000 until its next tick, rejecting a response received at 100_500.
        assertEquals("미확인", rows(telemetry, 100_000).getValue("카메라").value)
        assertEquals("수신 중", rows(telemetry, 100_600).getValue("카메라").value)
        val future = telemetry.copy(status = telemetry.status.copy(
            cameraSensor = telemetry.status.cameraSensor.copy(lastFrameAtEpochMillis = 110_000)))
        assertEquals("미확인", rows(future).getValue("카메라").value)
    }

    @Test fun `short relay renewal does not blink but a sustained renewal becomes visible`() = runTest {
        val registered = MobileRtkRelayState(active = true, pipelineId = "pipe")
        val relay = MutableStateFlow(registered)
        val labels = mutableListOf<String?>()
        backgroundScope.launch { relay.preparationRelayLabels().collect { labels += it } }
        runCurrent()
        relay.value = registered.copy(active = false, preparing = true)
        runCurrent()
        advanceTimeBy(500)
        relay.value = registered
        runCurrent()
        assertTrue(labels.all { it == "중계 등록됨" })
        relay.value = registered.copy(active = false, preparing = true)
        runCurrent()
        advanceTimeBy(749)
        assertEquals("중계 등록됨", labels.last())
        // Counter-only updates must not postpone a persistent transition.
        relay.value = relay.value.copy(bytesFromCaster = 123)
        runCurrent()
        advanceTimeBy(1)
        runCurrent()
        assertEquals("연결하는 중", labels.last())
    }

    @Test fun `relay errors stops and pipeline changes bypass renewal delay`() = runTest {
        val registered = MobileRtkRelayState(active = true, pipelineId = "pipe")
        val relay = MutableStateFlow(registered)
        val labels = mutableListOf<String?>()
        backgroundScope.launch { relay.preparationRelayLabels().collect { labels += it } }
        runCurrent()
        relay.value = registered.copy(active = false, preparing = true)
        runCurrent()
        relay.value = relay.value.copy(error = "network error")
        runCurrent()
        assertEquals("오류", labels.last())
        relay.value = registered
        runCurrent()
        relay.value = registered.copy(pipelineId = "other", active = false, preparing = true)
        runCurrent()
        assertEquals("연결하는 중", labels.last())
        relay.value = MobileRtkRelayState(pipelineId = "other")
        runCurrent()
        assertEquals("꺼짐", labels.last())
        relay.value = MobileRtkRelayState()
        runCurrent()
        assertNull(labels.last())
    }

    @Test fun `duplicate connected emissions preserve preflight while a real disconnect clears it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val connections = MutableSharedFlow<SurveyDeviceConnection>(replay = 1)
        connections.emit(SurveyDeviceConnection("device", true))
        val source = object : SurveyRunDataSource by StartTestSource() {
            override val deviceConnection: Flow<SurveyDeviceConnection> = connections
        }
        val vm = SurveyRunViewModel(source, StartTestPersistence())
        advanceUntilIdle()
        vm.runPreflight()
        advanceUntilIdle()
        val checked = requireNotNull(vm.uiState.value.preflight)
        connections.emit(SurveyDeviceConnection("device", true))
        runCurrent()
        assertSame(checked, vm.uiState.value.preflight)
        assertFalse(vm.uiState.value.restoringLocalState)
        connections.emit(SurveyDeviceConnection("device", false))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.online)
        assertNull(vm.uiState.value.preflight)
    }
}
