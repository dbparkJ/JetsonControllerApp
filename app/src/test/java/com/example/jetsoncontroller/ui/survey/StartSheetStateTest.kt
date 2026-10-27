package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.Tone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartSheetStateTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun `context creation and checking replace previous pass with checking rows and a busy disabled confirmation`() {
        listOf("create_context", "preflight").forEach { operation ->
            val sheet = present(ready().copy(operation = operation))
            assertEquals(GeoStartPhase.CHECKING, sheet.phase)
            assertTrue(sheet.checks.all { it.phase == GeoStartPhase.CHECKING })
            assertEquals(listOf("연결", "장치 시간", "저장 공간", "카메라", "위치 품질"), sheet.checks.map { it.label })
            val rules = startSheetRules(sheet)
            assertTrue(rules.busy)
            assertFalse(rules.canStart)
            assertEquals("점검하는 중", rules.primaryLabel)
        }
    }

    @Test fun `passed sheet binds device ticket project and zone and enables one confirmation`() {
        val state = ready().copy(preparationDraft = startTestDraft().copy(ticketRowId = "row"))
        val sheet = startSheetState(state, "GEO-X-1", "신월4거리 북측 보조 구간", "구역 안",
            100_001, 5_001)
        assertEquals("신월4거리 북측 보조 구간", sheet.sectionName)
        assertEquals("수원시 도로대장", sheet.projectName)
        assertTrue(sheet.ticketLinked)
        assertTrue(sheet.insideZone)
        assertEquals("GEO-X-1", sheet.deviceName)
        assertTrue(startSheetRules(sheet).canStart)
    }

    @Test fun `failed storage retains its value Korean reason and device files remedy`() {
        val initial = ready()
        val checked = requireNotNull(initial.preflight)
        val state = initial.copy(preflight = checked.copy(ready = false, checks = checked.checks.copy(
            storage = checked.checks.storage.copy(state = "INSUFFICIENT", availableBytes = 12_000_000_000))))
        var opens = 0
        val sheet = startSheetState(state, "GEO-X-1", nowEpochMillis = 100_001,
            nowElapsedMillis = 5_001, onStorage = { opens++ })
        val storage = sheet.checks.single { it.label == "저장 공간" }
        assertEquals("${Formats.size(12_000_000_000)} 남음", storage.value)
        assertEquals("저장 공간이 수집 설정의 최소값보다 적습니다.", storage.reason)
        assertEquals("장치 파일", storage.remedy?.label)
        requireNotNull(storage.remedy).onClick()
        assertEquals(1, opens)
        assertFalse(startSheetRules(sheet).canStart)
        assertTrue(startSheetRules(sheet).showRecheck)
        assertEquals("점검을 통과해야 시작할 수 있습니다.", startSheetRules(sheet).blockingReason)
    }

    @Test fun `failed required sensors are listed separately and never expose device error codes`() {
        val initial = ready()
        val checked = requireNotNull(initial.preflight)
        val state = initial.copy(preflight = checked.copy(ready = false, checks = checked.checks.copy(
            sensors = listOf(PreflightSensorCheck("camera", "REQUIRED", "LOST", detail = "INTERNAL_CODE"),
                PreflightSensorCheck("gnss", "REQUIRED", "STALE", detail = "raw hardware error")))))
        val sheet = present(state)
        val failures = sheet.checks.filter { it.label in listOf("카메라", "위치 품질") }
        assertEquals(2, failures.size)
        assertTrue(failures.all { it.phase == GeoStartPhase.FAILED && it.reason == "센서 수신 상태를 확인하세요." })
        assertFalse(startSheetRules(sheet).canStart)
    }

    @Test fun `an expired check a changed ticket or lost control disables confirmation`() {
        val state = ready()
        listOf(
            startSheetState(state, "GEO-X-1", nowEpochMillis = 220_001, nowElapsedMillis = 5_001),
            startSheetState(state, "GEO-X-1", nowEpochMillis = 100_001, nowElapsedMillis = 125_001),
            present(state.copy(error = TicketChangedMessage)),
            present(state.copy(controlAvailable = false)),
            startSheetState(state, "GEO-X-1", nowEpochMillis = 100_001, nowElapsedMillis = 5_001, preparationReady = false)
        ).forEach { assertFalse(startSheetRules(it).canStart) }
        assertEquals(TicketChangedMessage, startSheetRules(present(state.copy(error = TicketChangedMessage))).blockingReason)
    }

    @Test fun `optional failures stay visible without changing a passed required-check verdict`() {
        val state = ready()
        val checked = requireNotNull(state.preflight)
        val optional = state.copy(preflight = checked.copy(checks = checked.checks.copy(sensors =
            checked.checks.sensors + PreflightSensorCheck("imu", "OPTIONAL", "STALE"))))
        assertTrue(startSheetRules(present(optional)).canStart)
        val warning = optionalStartChecks(optional).single()
        assertEquals("관성 센서", warning.label)
        assertEquals(GeoStartPhase.FAILED, warning.phase)
        assertEquals("선택 센서 · 미확인", warning.value)
    }

    @Test fun `a recheck clears old evidence while waiting and never creates a project or starts a run`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource()
        val vm = SurveyRunViewModel(source, StartTestPersistence())
        advanceUntilIdle()
        vm.runPreflight()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.preflight)
        source.preflightGate = CompletableDeferred()
        vm.runPreflight()
        runCurrent()
        assertNull(vm.uiState.value.preflight)
        assertEquals("preflight", vm.uiState.value.operation)
        assertEquals(GeoStartPhase.CHECKING, present(vm.uiState.value).phase)
        assertTrue(source.projectRequests.isEmpty())
        assertTrue(source.sectionRequests.isEmpty())
        assertTrue(source.starts.isEmpty())
        source.preflightGate?.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canStart)
    }

    @Test fun `passive readiness uses sample freshness and does not turn missing or invalid storage into zero`() {
        val camera = CameraSensorStatus(configured = true, connected = true, active = true, lastFrameAtEpochMillis = 99_000)
        val gnss = GnssSensorStatus(configured = true, connected = true, active = true,
            lastSampleAtEpochMillis = 99_000, fixType = "rtk_fixed", latitude = 37.5, longitude = 126.9)
        val status = JetsonStatus(collectedAtEpochMillis = 99_000, storageTotalBytes = 100,
            storageAvailableBytes = 0, sensorTelemetryAvailable = true, sensorTelemetryFresh = true,
            cameraSensor = camera, gnssSensor = gnss)
        val state = ready().copy(preparationTelemetry = PreparationTelemetry("device", status, "같은 네트워크"))
        fun value(source: SurveyRunUiState, label: String, now: Long = 100_000) =
            preparationReadiness(source, now).single { it.label == label }.value
        assertEquals("${Formats.size(0)} 남음", value(state, "저장 공간"))
        assertEquals("수신 중", value(state, "카메라"))
        assertEquals("정밀 위치", value(state, "위치 품질"))
        assertEquals("미확인", value(state, "저장 공간", 120_000))
        assertEquals("위치 미확인", value(state.copy(preparationTelemetry = state.preparationTelemetry.copy(
            status = status.copy(gnssSensor = gnss.copy(lastSampleAtEpochMillis = 1)))), "위치 품질"))
        val invalid = status.copy(metricValidity = mapOf("storageAvailableBytes" to MetricValidity("invalid")))
        assertEquals("미확인", value(state.copy(preparationTelemetry = state.preparationTelemetry.copy(status = invalid)), "저장 공간"))
        assertEquals("미확인", value(state.copy(preparationTelemetry = PreparationTelemetry("other", status)), "저장 공간"))
    }

    @Test fun `pending start is the primary explanation even when the device is offline`() {
        val state = ready().copy(online = false,
            pendingStart = PendingContextualStart("pipe", "project", 1, "section", 1, "policy", "preflight", "request"))
        assertEquals("시작 확인 중", preparationActionReason(state))
        assertFalse(startSheetRules(present(state)).canStart)
    }

    @Test fun `the readiness icon tone follows the preparation explanation`() {
        val pending = PendingContextualStart("pipe", "project", 1, "section", 1, "policy", "preflight", "request")
        val cases = mapOf(
            ready() to Tone.SUCCESS,
            ready().copy(online = false, pendingStart = pending) to Tone.PENDING,
            ready().copy(runStateConfirmed = false) to Tone.PENDING,
            ready().copy(online = false) to Tone.WARNING,
            ready().copy(observedActiveRunCount = 1) to Tone.WARNING,
            ready().copy(policy = null) to Tone.WARNING
        )
        cases.forEach { (state, tone) ->
            assertEquals(preparationActionReason(state), tone, preparationActionTone(state))
            assertEquals(tone == Tone.SUCCESS, preparationActionReason(state).endsWith(" · 시작 전 점검을 진행하세요."))
        }
    }

    @Test fun `new section reason belongs to the project choice unless editing is locked`() {
        val state = ready()
        assertNull(preparationEditDisabledReason(state))
        assertNull(preparationNewSectionDisabledReason(state))
        assertNull(preparationNewSectionDisabledReason(state.copy(selectedProject = null)))
        assertNull(preparationNewSectionDisabledReason(state.copy(preparationDraft = null)))
        val noProject = state.copy(selectedProject = null, preparationDraft = null)
        assertEquals("프로젝트를 먼저 선택하세요.", preparationNewSectionDisabledReason(noProject))
        val pending = PendingContextualStart("pipe", "project", 1, "section", 1, "policy", "preflight", "request")
        val locked = noProject.copy(pendingStart = pending, restoringLocalState = true, operation = "preflight")
        assertEquals("진행 중인 수집과 시작 요청을 먼저 확인하세요.", preparationNewSectionDisabledReason(locked))
        assertEquals(preparationEditDisabledReason(locked), preparationNewSectionDisabledReason(locked))
    }

    @Test fun `restoring and busy preparation retain the edit gate and explain the disabled choice`() {
        val restoring = ready().copy(restoringLocalState = true, operation = "preflight")
        assertEquals("저장된 선택을 확인하고 있습니다.", preparationEditDisabledReason(restoring))
        assertEquals(preparationEditDisabledReason(restoring), preparationNewSectionDisabledReason(restoring))
        val busy = ready().copy(operation = "preflight")
        assertEquals("진행 중인 작업이 끝난 뒤 선택할 수 있습니다.", preparationEditDisabledReason(busy))
        assertEquals(preparationEditDisabledReason(busy), preparationNewSectionDisabledReason(busy))
    }

    private fun ready(): SurveyRunUiState {
        val source = StartTestSource()
        return SurveyRunUiState(deviceId = "device", online = true, runStateConfirmed = true,
            pipelineId = "pipe", selectedProject = source.project, selectedSection = source.section,
            policy = source.policy, preflight = source.checked(100_000), preflightReceivedAtElapsedRealtime = 5_000,
            preparationDraft = startTestDraft())
    }

    private fun present(state: SurveyRunUiState) = startSheetState(state, "GEO-X-1",
        nowEpochMillis = 100_001, nowElapsedMillis = 5_001)
}
