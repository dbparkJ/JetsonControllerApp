package com.example.jetsoncontroller.ui

import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadContext
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.field.runOutputStored
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class NavigationWorkflowTest {
    @Test
    fun `recent history opens its canonical run even before final manifest`() {
        val clicked = TaskRun("history-older", "pipe", "이전 수집", "saved-log", "started",
            runId = "older-run", deviceId = "device")
        assertEquals("run_result/older-run?deviceId=device&pipelineId=pipe&historyId=history-older",
            historyResultRoute(clicked, "device"))
        assertNotEquals(historyResultRoute(clicked, "device"),
            historyResultRoute(clicked.copy(id = "history-latest", runId = "latest-run"), "device"))
    }

    @Test
    fun `legacy history detail uses the entry identity and preserves encoded characters`() {
        val legacy = TaskRun("history/old 1", "pipe", "이전 수집", "log", "started")
        assertEquals("history_result/history%2Fold%201?deviceId=device%2Fa", historyResultRoute(legacy, "device/a"))
        assertEquals("run_result/run%2F1?deviceId=device&pipelineId=pipe%20one",
            runResultRoute("run/1", "device", "pipe one"))
    }

    @Test
    fun `result back preserves task history and transfer sources`() {
        listOf(Routes.TASKS, Routes.TASK_HISTORY, Routes.UPLOAD_PROGRESS, Routes.TRANSFERS, Routes.ACTIVE_RUN).forEach {
            assertEquals(it, resultReturnRoute(it))
        }
        assertEquals(Routes.TASKS, resultReturnRoute(null))
    }

    @Test
    fun `result saved log keeps the clicked pipeline and log identity`() {
        val run = pipelineRun("older-run", "device", "pipe", false)
        `when`(run.logId).thenReturn("saved-older-log")
        `when`(run.startedAt).thenReturn("started")
        `when`(run.state).thenReturn("COMPLETED")
        val entry = resultLogEntry(run)
        assertEquals("saved-older-log", entry.logId)
        assertEquals("older-run", entry.runId)
        assertEquals("pipe", entry.pipelineId)
        assertEquals("device", entry.deviceId)
    }

    @Test
    fun `collection tab resumes protected work before offering a new task`() {
        val pending = PendingContextualStart(
            "pipe", "project", 1, "section", 1, "policy", "preflight", "request"
        )

        assertEquals(
            SurveyOpenDecision.CURRENT_ACTIVE,
            collectionOpenDecision(SurveyRunUiState(pipelineId = "pipe", pendingStart = pending))
        )
        assertEquals(
            SurveyOpenDecision.WAIT_FOR_RESTORE,
            collectionOpenDecision(SurveyRunUiState(restoringLocalState = true))
        )
        assertEquals(
            SurveyOpenDecision.REQUESTED_PIPELINE,
            collectionOpenDecision(SurveyRunUiState())
        )
    }

    @Test
    fun `only the transport requested by the user completes connection navigation`() {
        val ble = TransportState.Connected(type = TransportType.BLE)
        val direct = TransportState.Connected(type = TransportType.WIFI_DIRECT)

        assertFalse(connectionAttemptCompleted(null, ble))
        assertFalse(connectionAttemptCompleted(TransportType.WIFI_DIRECT, ble))
        assertTrue(connectionAttemptCompleted(TransportType.WIFI_DIRECT, direct))
    }

    @Test
    fun `existing LAN connection does not reopen dashboard after back navigation`() {
        val lan = TransportState.Connected(type = TransportType.LAN)

        assertFalse(connectionAttemptCompleted(null, lan))
    }

    @Test
    fun `background device registration failure does not eject direct server workflow`() {
        assertFalse(deviceRegistrationRedirectAllowed("server_storage"))
        assertTrue(deviceRegistrationRedirectAllowed("dashboard"))
    }

    @Test
    fun `developer routes are revoked while normal network settings remain available`() {
        assertTrue(routeAllowed("network_settings", developerModeEnabled = false))
        assertTrue(routeAllowed("server_storage", developerModeEnabled = false))
        assertFalse(routeAllowed("admin_tools", developerModeEnabled = false))
        assertFalse(routeAllowed("pipeline_config/{pipelineId}", developerModeEnabled = false))
        assertFalse(routeAllowed("developer", developerModeEnabled = false))
        assertTrue(routeAllowed("developer", developerModeEnabled = true))
        assertEquals("settings", developerRevocationDestination("developer", false))
        assertEquals("settings", developerRevocationDestination("admin_tools", false))
        assertNull(developerRevocationDestination("developer", true))
        assertNull(developerRevocationDestination("network_settings", false))
    }

    @Test
    fun `pending start takes priority over stale terminal evidence`() {
        val terminal = terminalRun("old-run", "device", "pipe")
        val pending = PendingContextualStart("pipe", "project", 1, "section", 1,
            "policy", "preflight", "request")
        val state = SurveyRunUiState(
            deviceId = "device",
            pipelineId = "pipe",
            latestRun = terminal,
            pendingStart = pending
        )

        assertFalse(resultAwaitingReview(state))
        assertEquals(SurveyOpenDecision.CURRENT_ACTIVE, surveyOpenDecision(state, "other"))
    }

    @Test
    fun `exact unacknowledged terminal result is reviewed once then new collection is allowed`() {
        val terminal = terminalRun("run-1", "device", "pipe")
        val awaiting = SurveyRunUiState(
            deviceId = "device",
            pipelineId = "pipe",
            latestRun = terminal
        )

        assertTrue(resultAwaitingReview(awaiting))
        assertEquals(SurveyOpenDecision.CURRENT_RESULT, surveyOpenDecision(awaiting, "other"))

        val acknowledged = awaiting.copy(resultAcknowledged = true)
        assertFalse(resultAwaitingReview(acknowledged))
        assertEquals(SurveyOpenDecision.REQUESTED_PIPELINE, surveyOpenDecision(acknowledged, "other"))
    }

    @Test
    fun `stop target requires the exact active run identity`() {
        val run = pipelineRun("run-1", "device", "pipe", active = true)
        val stale = ManagedPipeline(
            id = "pipe", label = "수집", state = PipelineState.RUNNING,
            entrypoint = "run.py", config = "config", virtualenv = "venv",
            activeRunId = "older-run"
        )
        val exact = stale.copy(activeRunId = "run-1")

        assertNull(matchingStopTarget(run, listOf(stale)))
        assertEquals(exact, matchingStopTarget(run, listOf(stale, exact)))
        assertNull(matchingStopTarget(run, listOf(exact.copy(state = PipelineState.STOPPING))))
    }

    @Test
    fun `live collection map never reuses a different history route`() {
        val active = pipelineRun("run-1", "device", "pipe", active = true)
        val stale = TaskRun(
            id = "stale", pipelineId = "pipe", label = "이전 수집", logId = "old-log",
            startedAt = "2026-09-14T01:00:00Z", state = "COMPLETED", runId = "old-run",
            deviceId = "device"
        )
        val exact = TaskRun(
            id = "exact", pipelineId = "pipe", label = "현재 수집", logId = "live-log",
            startedAt = "2026-09-15T01:00:00Z", state = "RUNNING", runId = "run-1",
            deviceId = "device", active = true
        )

        assertEquals(exact, activeCollectionHistoryRun(active, listOf(stale, exact)))
        assertNull(activeCollectionHistoryRun(active, listOf(exact.copy(pipelineId = "other"))))
        assertNull(activeCollectionHistoryRun(active, listOf(exact.copy(deviceId = "other"))))
        assertNull(activeCollectionHistoryRun(pipelineRun("run-1", "device", "pipe", false), listOf(exact)))
    }

    @Test
    fun `cold home adopts only one fresh contextual run for the selected device`() {
        val receipt = mock(com.example.jetsoncontroller.model.ContextualStartReceipt::class.java)
        val context = mock(com.example.jetsoncontroller.model.SurveyContextSnapshot::class.java)
        val preflight = mock(com.example.jetsoncontroller.model.PipelinePreflight::class.java)
        `when`(receipt.runId).thenReturn("run-1")
        `when`(receipt.contextSnapshot).thenReturn(context)
        `when`(receipt.preflightSnapshot).thenReturn(preflight)
        `when`(context.deviceId).thenReturn("device")
        `when`(preflight.pipelineId).thenReturn("pipe")
        val running = ManagedPipeline(
            id = "pipe", label = "도로 수집", state = PipelineState.RUNNING,
            entrypoint = "run.py", config = "config", virtualenv = "venv",
            activeRunId = "run-1", contextualStart = receipt
        )

        assertEquals(running, discoverableCurrentPipeline("device", listOf(running), true))
        assertNull(discoverableCurrentPipeline("device", listOf(running), false))
        assertNull(discoverableCurrentPipeline("other-device", listOf(running), true))
        assertNull(discoverableCurrentPipeline("device", listOf(running, running), true))
    }

    @Test
    fun `home replaces an acknowledged old pipeline with fresh active device evidence`() {
        val old = terminalRun("old-run", "device", "pipe-1")
        val state = SurveyRunUiState(
            deviceId = "device",
            pipelineId = "pipe-1",
            latestRun = old,
            resultAcknowledged = true
        )
        val discovered = ManagedPipeline(
            id = "pipe-2", label = "새 수집", state = PipelineState.RUNNING,
            entrypoint = "run.py", config = "config", virtualenv = "venv",
            activeRunId = "run-2"
        )

        assertTrue(shouldAdoptDiscoveredPipeline(state, discovered))
        assertFalse(shouldAdoptDiscoveredPipeline(state.copy(unconfirmedRunId = "unconfirmed"), discovered))
        assertFalse(shouldAdoptDiscoveredPipeline(state.copy(restoringLocalState = true), discovered))
        assertFalse(shouldAdoptDiscoveredPipeline(state.copy(resultAcknowledged = false), discovered))
        assertFalse(
            shouldAdoptDiscoveredPipeline(
                state.copy(
                    pendingStart = PendingContextualStart(
                        "pipe-1", "project", 1, "section", 1,
                        "policy", "preflight", "request"
                    )
                ),
                discovered
            )
        )
        assertTrue(shouldAdoptDiscoveredPipeline(state.copy(pipelineId = "pipe-2"), discovered))
        val current = pipelineRun("run-2", "device", "pipe-2", active = true)
        assertFalse(
            shouldAdoptDiscoveredPipeline(
                state.copy(pipelineId = "pipe-2", activeRun = current, latestRun = current),
                discovered
            )
        )
    }

    @Test
    fun `server receipt uses only the exact run context`() {
        val run = pipelineRun("run-1", "device", "pipe", active = false)
        val other = uploadJob("job-other", "another-run", matched = true)
        val otherDevice = uploadJob("job-device", "run-1", matched = true, deviceId = "device-b")
        val otherPipeline = uploadJob("job-pipeline", "run-1", matched = true, pipelineId = "pipe-b")
        val otherOutput = uploadJob("job-output", "run-1", matched = true, outputId = "output-b")
        val exact = uploadJob("job-exact", "run-1", matched = true)

        assertEquals(
            null,
            serverReceiptPresentation(run, listOf(other, otherDevice, otherPipeline, otherOutput)).label
        )
        assertEquals(
            ServerReceiptPresentation("서버 수신 확인됨", StatusTone.SUCCESS),
            serverReceiptPresentation(run, listOf(other, exact))
        )
    }

    @Test
    fun `server receipt aggregates exact retries before presenting the newest failure`() {
        val run = pipelineRun("run-1", "device", "pipe", active = false)
        val oldFailure = uploadJob(
            "old-failure", "run-1", matched = false, state = UploadJobState.FAILED
        )
        val verifiedRetry = uploadJob("verified-retry", "run-1", matched = true)
        val activeRetry = uploadJob(
            "active-retry", "run-1", matched = null, state = UploadJobState.UPLOADING
        )

        assertEquals(
            ServerReceiptPresentation("서버 수신 확인됨", StatusTone.SUCCESS),
            serverReceiptPresentation(run, listOf(oldFailure, verifiedRetry))
        )
        assertEquals(
            ServerReceiptPresentation("파일 전송 중", StatusTone.PENDING),
            serverReceiptPresentation(run, listOf(oldFailure, activeRetry))
        )
        assertEquals(
            ServerReceiptPresentation("서버 수신 검증 불일치", StatusTone.WARNING),
            serverReceiptPresentation(run, listOf(oldFailure))
        )
    }

    @Test
    fun `stored result requires final state and exact manifest and upload identity`() {
        val manifest = mock(com.example.jetsoncontroller.model.PipelineOutputManifest::class.java)
        `when`(manifest.runId).thenReturn("run-1")
        val output = mock(com.example.jetsoncontroller.model.PipelineRunOutput::class.java)
        `when`(output.rootId).thenReturn("data")
        `when`(output.path).thenReturn("runs/run-1")
        `when`(output.outputId).thenReturn("output")
        `when`(output.manifestState).thenReturn("FINAL")
        `when`(output.manifest).thenReturn(manifest)
        val run = pipelineRun("run-1", "device", "pipe", active = false)
        `when`(run.finishedAt).thenReturn("finished")
        `when`(run.sourceRevision).thenReturn("source")
        `when`(run.configRevision).thenReturn("config")
        `when`(run.output).thenReturn(output)

        assertTrue(runOutputStored(run))
        `when`(output.manifestState).thenReturn("READY")
        assertFalse(runOutputStored(run))
        `when`(output.manifestState).thenReturn("FINAL")
        `when`(manifest.runId).thenReturn("other-run")
        assertFalse(runOutputStored(run))
    }

    @Test
    fun `legacy destinations map onto the four primary tabs`() {
        assertEquals(ControlSection.DEVICES, ControlSection.OVERVIEW.tab())
        assertEquals(ControlSection.DEVICES, ControlSection.SENSORS.tab())
        assertEquals(ControlSection.TASKS, ControlSection.COLLECTION.tab())
        assertEquals(ControlSection.TASKS, ControlSection.PIPELINES.tab())
        assertEquals(ControlSection.TRANSFERS, ControlSection.DATA.tab())
        assertEquals(ControlSection.SETTINGS, ControlSection.SETTINGS.tab())
        assertEquals(
            setOf(ControlSection.DEVICES, ControlSection.TASKS, ControlSection.TRANSFERS, ControlSection.SETTINGS),
            ControlSection.entries.map { it.tab() }.toSet()
        )
    }

    @Test
    fun `the app starts on the device root and each tab opens its root`() {
        assertEquals(Routes.DEVICES, startRoute())
        assertEquals(Routes.DEVICES, tabRoute(ControlSection.DEVICES))
        assertEquals(Routes.DEVICES, tabRoute(ControlSection.OVERVIEW))
        assertEquals(Routes.TASKS, tabRoute(ControlSection.TASKS))
        assertEquals(Routes.TASKS, tabRoute(ControlSection.COLLECTION))
        assertEquals(Routes.TRANSFERS, tabRoute(ControlSection.TRANSFERS))
        assertEquals(Routes.TRANSFERS, tabRoute(ControlSection.DATA))
        assertEquals(Routes.SETTINGS, tabRoute(ControlSection.SETTINGS))
        assertNotEquals(Routes.CONNECTION_HUB, startRoute())
    }

    @Test
    fun `re-tapping the open tab keeps its root instead of recreating it`() {
        // The second tap on 설정 used to recreate the root and crash during the transition.
        assertEquals(TabTap.STAY, tabTap(Routes.SETTINGS, tabRootInBackStack = true, Routes.SETTINGS))
        assertEquals(TabTap.STAY, tabTap(Routes.DEVICES, tabRootInBackStack = true, Routes.DEVICES))
    }

    @Test
    fun `tapping a tab from its sub-screen returns to the existing root`() {
        assertEquals(
            TabTap.RETURN_TO_TAB_ROOT,
            tabTap(Routes.ALERT_SETTINGS, tabRootInBackStack = true, Routes.SETTINGS)
        )
        assertEquals(TabTap.RETURN_TO_TAB_ROOT, tabTap(Routes.SETTINGS, tabRootInBackStack = true, Routes.DEVICES))
    }

    @Test
    fun `tapping another tab opens its root`() {
        assertEquals(TabTap.OPEN_TAB_ROOT, tabTap(Routes.DEVICES, tabRootInBackStack = false, Routes.SETTINGS))
        assertEquals(TabTap.OPEN_TAB_ROOT, tabTap(Routes.TASKS, tabRootInBackStack = false, Routes.TRANSFERS))
    }

    @Test
    fun `connection attempt completion returns to the device root`() {
        assertEquals(Routes.DEVICES, deviceFlowExitRoute(DeviceFlowExit.CONNECTION_COMPLETED))
    }

    @Test
    fun `finished pairing returns to the device root`() {
        assertEquals(Routes.DEVICES, deviceFlowExitRoute(DeviceFlowExit.PAIRING_READY))
    }

    @Test
    fun `cancelled pairing returns to the device root`() {
        assertEquals(Routes.DEVICES, deviceFlowExitRoute(DeviceFlowExit.PAIRING_CANCELLED))
    }

    @Test
    fun `disconnecting from the device detail returns to the device root`() {
        assertEquals(Routes.DEVICES, deviceFlowExitRoute(DeviceFlowExit.DETAIL_DISCONNECTED))
    }

    @Test
    fun `back from the device detail returns to the device root`() {
        assertEquals(Routes.DEVICES, deviceFlowExitRoute(DeviceFlowExit.DETAIL_BACK))
    }

    @Test
    fun `connection notice is one banner for screens that need the device and never for the root`() {
        assertEquals(
            ConnectionNotice("장치 연결을 기다리고 있습니다. 연결되면 최신 정보로 바뀝니다.", StatusTone.WARNING),
            connectionNoticeFor(Routes.PIPELINES, fullControlConnected = false, transportType = null, registrationRequired = false)
        )
        assertEquals(
            "전체 제어 연결이 필요합니다. 연결되면 최신 정보로 바뀝니다.",
            connectionNoticeFor(Routes.PIPELINES, fullControlConnected = false, transportType = TransportType.BLE, registrationRequired = false)?.message
        )
        assertNull(connectionNoticeFor(Routes.DEVICES, fullControlConnected = false, transportType = null, registrationRequired = false))
        assertNull(connectionNoticeFor(Routes.PIPELINES, fullControlConnected = true, transportType = TransportType.LAN, registrationRequired = false))
    }

    @Test
    fun `only screens with input promise to keep it, and settings never wait for a device`() {
        assertEquals(
            "장치 연결을 기다리고 있습니다. 작성한 내용은 유지됩니다.",
            connectionNoticeFor(Routes.SURVEY_RUN, fullControlConnected = false, transportType = null, registrationRequired = false)?.message
        )
        assertNull(connectionNoticeFor(Routes.SETTINGS, fullControlConnected = false, transportType = null, registrationRequired = false))
    }

    @Test
    fun `no device registered means no connection banner anywhere`() {
        listOf(Routes.PIPELINES, Routes.SURVEY_RUN, Routes.DATA, Routes.DEVICE_DETAIL).forEach { route ->
            assertNull(
                route,
                connectionNoticeFor(
                    route,
                    fullControlConnected = false,
                    transportType = null,
                    registrationRequired = false,
                    hasRegisteredDevice = false
                )
            )
        }
    }

    @Test
    fun `registration problems show an error banner instead of moving the operator to onboarding`() {
        val notice = connectionNoticeFor(Routes.DEVICES, fullControlConnected = false, transportType = null, registrationRequired = true)
        assertEquals(StatusTone.ERROR, notice?.tone)
        assertTrue(notice?.message.orEmpty().contains("QR"))
        assertNull(connectionNoticeFor("server_storage", fullControlConnected = false, transportType = null, registrationRequired = true))
    }

    private fun terminalRun(runId: String, deviceId: String, pipelineId: String): PipelineRun =
        pipelineRun(runId, deviceId, pipelineId, active = false)

    private fun pipelineRun(
        runId: String,
        deviceId: String,
        pipelineId: String,
        active: Boolean
    ): PipelineRun =
        mock(PipelineRun::class.java).also { run ->
            val context = UploadContext(
                surveyProjectId = "project", surveySectionId = "section", runId = runId,
                deviceId = deviceId, pipelineId = pipelineId, sourceRevision = "source",
                configSha256 = "config", outputId = "output", createdAt = "created"
            )
            `when`(run.runId).thenReturn(runId)
            `when`(run.deviceId).thenReturn(deviceId)
            `when`(run.pipelineId).thenReturn(pipelineId)
            `when`(run.active).thenReturn(active)
            `when`(run.uploadContext).thenReturn(context)
        }

    private fun uploadJob(
        id: String,
        runId: String,
        matched: Boolean?,
        deviceId: String = "device",
        pipelineId: String = "pipe",
        outputId: String = "output",
        state: UploadJobState = UploadJobState.COMPLETED
    ): UploadJob {
        val context = UploadContext(
            surveyProjectId = "project",
            surveySectionId = "section",
            runId = runId,
            deviceId = deviceId,
            pipelineId = pipelineId,
            sourceRevision = "source",
            configSha256 = "config",
            outputId = outputId,
            createdAt = "created"
        )
        return UploadJob(
            id = id,
            rootId = "data",
            relativePath = "runs/$runId",
            targetId = "server",
            state = state,
            bytesTotal = 10,
            bytesTransferred = 10,
            filesTotal = 1,
            filesTransferred = 1,
            currentFile = null,
            errorMessage = null,
            verification = matched?.let { UploadVerification(
                jobId = id,
                state = "VERIFIED",
                matched = it,
                deletionAllowed = it
            ) },
            context = context
        )
    }
}
