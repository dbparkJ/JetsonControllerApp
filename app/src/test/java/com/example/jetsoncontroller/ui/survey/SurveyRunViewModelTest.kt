package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.data.survey.SurveyRunLocalState
import com.example.jetsoncontroller.data.survey.SurveyRunPersistence
import com.example.jetsoncontroller.data.survey.SurveySelection
import com.example.jetsoncontroller.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyRunViewModelTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `late non cooperative persistence result from old device cannot replace new scope`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val oldGate = CompletableDeferred<Unit>()
        val pendingA = pending("pipe", "project-a", "section-a")
        val projectB = project("project-b")
        val sectionB = section(projectB, "section-b")
        val source = FakeSource(projectB, sectionB)
        val persistence = FakePersistence(
            oldGate = oldGate,
            states = mapOf(
                "device-a" to SurveyRunLocalState(pendingStart = pendingA),
                "device-b" to SurveyRunLocalState(
                    selection = SurveySelection(projectB.surveyProjectId, projectB.revision,
                        sectionB.surveySectionId, sectionB.revision),
                    lastPipelineId = "pipe"
                )
            )
        )
        source.connection.value = SurveyDeviceConnection("device-a", true)
        val viewModel = SurveyRunViewModel(source, persistence)
        runCurrent()
        source.connection.value = SurveyDeviceConnection("device-b", true)
        advanceUntilIdle()

        assertEquals("device-b", viewModel.uiState.value.deviceId)
        assertEquals("project-b", viewModel.uiState.value.selectedProject?.surveyProjectId)
        assertEquals("section-b", viewModel.uiState.value.selectedSection?.surveySectionId)
        assertEquals(null, viewModel.uiState.value.pendingStart)

        oldGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("device-b", viewModel.uiState.value.deviceId)
        assertEquals(null, viewModel.uiState.value.pendingStart)
    }

    @Test
    fun `unknown pending request locks all context changes`() {
        val state = SurveyRunUiState(
            online = true,
            selectedProject = project("project"),
            selectedSection = section(project("project"), "section"),
            pendingStart = pending("pipe", "project", "section")
        )

        assertTrue(state.contextLocked)
        assertTrue(!state.canPreflight)
        assertEquals("수집 시작 여부를 확인하고 있습니다", startReadinessLabel(state))
    }

    @Test
    fun `cold start restores pending pipeline and blocks opening another profile`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = pending("pipe", "project", "section")
        val source = FakeSource(project("project"), section(project("project"), "section"))
        val persistence = FakePersistence(
            states = mapOf("device" to SurveyRunLocalState(pendingStart = pending))
        )
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()

        source.connection.value = SurveyDeviceConnection("device", true)
        advanceUntilIdle()

        assertEquals("pipe", viewModel.uiState.value.pipelineId)
        assertEquals(pending, viewModel.uiState.value.pendingStart)
        assertTrue(viewModel.uiState.value.contextLocked)

        viewModel.open("other-pipe", "다른 작업")
        advanceUntilIdle()
        assertEquals("pipe", viewModel.uiState.value.pipelineId)
        assertEquals(pending, viewModel.uiState.value.pendingStart)
    }

    @Test
    fun `terminal evidence survives offline and is isolated when device changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val project = project("project")
        val section = section(project, "section")
        val source = FakeSource(project, section, configuredPolicy = policy())
        source.runResult = Result.success(run("run-1", true, project, section))
        val persistence = FakePersistence(states = mapOf(
            "device-a" to SurveyRunLocalState(lastPipelineId = "pipe", lastRunId = "run-1")
        ))
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()

        source.connection.value = SurveyDeviceConnection("device-a", true)
        advanceUntilIdle()
        assertEquals("run-1", viewModel.uiState.value.activeRun?.runId)

        source.runResult = Result.success(run("run-1", false, project, section))
        viewModel.refresh()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.latestRun!!.active)
        assertNull(viewModel.uiState.value.activeRun)

        source.connection.value = SurveyDeviceConnection("device-a", false)
        advanceUntilIdle()
        assertEquals("run-1", viewModel.uiState.value.latestRun?.runId)

        source.connection.value = SurveyDeviceConnection("device-b", false)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.latestRun)
        assertNull(viewModel.uiState.value.pendingStart)
    }

    @Test
    fun `late accepted persistence from old device cannot replace new device request lock`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val acceptedGate = CompletableDeferred<Unit>()
        val project = project("project")
        val section = section(project, "section")
        val pendingA = pending("pipe", project.surveyProjectId, section.surveySectionId)
        val pendingB = pending("pipe-b", "project-b", "section-b")
        val source = FakeSource(project, section, configuredPolicy = policy())
        source.contextualReceipt = receipt(pendingA, project, section, "device-a")
        source.connection.value = SurveyDeviceConnection("device-a", true)
        val persistence = FakePersistence(
            states = mapOf(
                "device-a" to SurveyRunLocalState(pendingStart = pendingA),
                "device-b" to SurveyRunLocalState(pendingStart = pendingB)
            ),
            acceptedGate = acceptedGate
        )
        val viewModel = SurveyRunViewModel(source, persistence)
        runCurrent()

        source.connection.value = SurveyDeviceConnection("device-b", true)
        advanceUntilIdle()
        assertEquals("device-b", viewModel.uiState.value.deviceId)
        assertEquals(pendingB, viewModel.uiState.value.pendingStart)

        acceptedGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("device-b", viewModel.uiState.value.deviceId)
        assertEquals("pipe-b", viewModel.uiState.value.pipelineId)
        assertEquals(pendingB, viewModel.uiState.value.pendingStart)
    }

    @Test
    fun `run query must return the exact requested run identity`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val project = project("project")
        val section = section(project, "section")
        val source = FakeSource(project, section, configuredPolicy = policy())
        source.runResult = Result.success(run("different-run", false, project, section, "device"))
        val persistence = FakePersistence(states = mapOf(
            "device" to SurveyRunLocalState(lastPipelineId = "pipe", lastRunId = "requested-run")
        ))
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()

        source.connection.value = SurveyDeviceConnection("device", true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.latestRun)
        assertEquals("requested-run", viewModel.uiState.value.unconfirmedRunId)
    }

    @Test
    fun `acknowledged terminal run remains visible and allows a new profile`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val project = project("project")
        val section = section(project, "section")
        val source = FakeSource(project, section, configuredPolicy = policy())
        source.runResult = Result.success(run("run-1", false, project, section, "device"))
        val persistence = FakePersistence(states = mapOf(
            "device" to SurveyRunLocalState(lastPipelineId = "pipe", lastRunId = "run-1")
        ))
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        source.connection.value = SurveyDeviceConnection("device", true)
        advanceUntilIdle()

        viewModel.acknowledgeResult("run-1")
        advanceUntilIdle()
        assertEquals("run-1", persistence.savedAcknowledgedRunId)
        assertEquals("run-1", viewModel.uiState.value.latestRun?.runId)
        assertFalse(viewModel.uiState.value.contextLocked)

        viewModel.open("other-pipe", "다른 작업")
        advanceUntilIdle()
        assertEquals("other-pipe", viewModel.uiState.value.pipelineId)
    }

    @Test
    fun `expired preflight rejection clears request lock and requires a new preflight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val project = project("project")
        val section = section(project, "section")
        val policy = policy()
        val preflight = preflight(project, section, policy)
        val source = FakeSource(
            project, section, configuredPolicy = policy, configuredPreflight = preflight,
            startFailure = com.example.jetsoncontroller.data.network.JetsonApiException(
                409, "PREFLIGHT_NOT_READY", null, "preflight expired after 120 seconds"
            )
        )
        val persistence = FakePersistence(
            oldGate = CompletableDeferred(Unit),
            states = mapOf("device" to SurveyRunLocalState(
                selection = SurveySelection("project", 1, "section", 1)
            ))
        )
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        source.connection.value = SurveyDeviceConnection("device", true)
        advanceUntilIdle()
        viewModel.open("pipe", "수집")
        advanceUntilIdle()
        viewModel.runPreflight()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.preflight?.ready == true)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.pendingStart)
        assertEquals(null, viewModel.uiState.value.preflight)
        assertTrue(!viewModel.uiState.value.contextLocked)
        assertEquals(null, persistence.savedPending)
    }

    private fun pending(pipeline: String, project: String, section: String) = PendingContextualStart(
        pipeline, project, 1, section, 1, "policy", "preflight", "request"
    )

    private fun project(id: String) = SurveyProject(id, id, 1, "created", "updated")
    private fun section(project: SurveyProject, id: String) =
        SurveySection(id, project.surveyProjectId, id, 1, "created", "updated")
    private fun policy() = PipelineRunPolicy(
        pipelineId = "pipe", revision = "policy", requiredSensors = listOf("camera"),
        outputRootId = "data", updatedAt = "updated"
    )
    private fun preflight(project: SurveyProject, section: SurveySection, policy: PipelineRunPolicy): PipelinePreflight {
        val context = SurveyContextSnapshot(
            deviceId = "device", surveyProjectId = project.surveyProjectId,
            surveyProjectLabel = project.label, surveyProjectRevision = project.revision,
            surveySectionId = section.surveySectionId, surveySectionLabel = section.label,
            surveySectionRevision = section.revision, capturedAt = "captured"
        )
        return PipelinePreflight(
            preflightId = "preflight", pipelineId = "pipe", deviceId = "device",
            surveyProject = SurveyProjectSummary(project.surveyProjectId, project.label, project.revision),
            surveySection = SurveySectionSummary(section.surveySectionId, project.surveyProjectId, section.label, section.revision),
            contextSnapshot = context, policy = policy, sourceRevision = "source", configRevision = "config",
            checkedAt = "checked", checkedAtEpochMillis = 1,
            checks = PipelinePreflightChecks(
                time = PreflightCheck("READY"),
                storage = PreflightStorageCheck("READY", "data", "", 100, 0),
                sensors = listOf(PreflightSensorCheck("camera", "REQUIRED", "ACTIVE"))
            ), ready = true
        )
    }

    private fun run(
        runId: String,
        active: Boolean,
        project: SurveyProject,
        section: SurveySection,
        deviceId: String = "device-a"
    ): PipelineRun {
        val policy = policy()
        val preflight = preflight(project, section, policy)
        val output = PipelineRunOutput("data", "runs/$runId", "output-$runId", "READY")
        val upload = UploadContext(
            surveyProjectId = project.surveyProjectId,
            surveySectionId = section.surveySectionId,
            runId = runId,
            deviceId = deviceId,
            pipelineId = "pipe",
            sourceRevision = "source",
            configSha256 = "config",
            outputId = output.outputId,
            createdAt = "created"
        )
        return PipelineRun(
            runId = runId,
            logId = "log-$runId",
            pipelineId = "pipe",
            deviceId = deviceId,
            state = if (active) "RUNNING" else "COMPLETED",
            startedAt = "started",
            finishedAt = if (active) null else "finished",
            exitCode = if (active) null else 0,
            contextSnapshot = preflight.contextSnapshot,
            policySnapshot = policy,
            preflightSnapshot = preflight,
            sourceRevision = "source",
            configRevision = "config",
            output = output,
            uploadContext = upload,
            active = active
        )
    }

    private fun receipt(
        pending: PendingContextualStart,
        project: SurveyProject,
        section: SurveySection,
        deviceId: String
    ): ContextualStartReceipt {
        val policy = policy()
        val checked = preflight(project, section, policy).copy(deviceId = deviceId)
        val context = checked.contextSnapshot.copy(deviceId = deviceId)
        val output = PipelineRunOutput("data", "runs/run-1", "output-run-1")
        return ContextualStartReceipt(
            runId = "run-1",
            clientRequestId = pending.clientRequestId,
            outcome = "STARTED",
            contextSnapshot = context,
            preflightSnapshot = checked.copy(contextSnapshot = context),
            output = output,
            uploadContext = UploadContext(
                surveyProjectId = project.surveyProjectId,
                surveySectionId = section.surveySectionId,
                runId = "run-1",
                deviceId = deviceId,
                pipelineId = pending.pipelineId,
                sourceRevision = checked.sourceRevision,
                configSha256 = checked.configRevision,
                outputId = output.outputId,
                createdAt = "created"
            ),
            statusUrl = "/runs/run-1"
        )
    }

    private class FakePersistence(
        private val oldGate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
        private val states: Map<String, SurveyRunLocalState> = emptyMap(),
        private val acceptedGate: CompletableDeferred<Unit>? = null
    ) : SurveyRunPersistence {
        var savedPending: PendingContextualStart? = null
        var savedAcknowledgedRunId: String? = null
        override fun state(deviceId: String): Flow<SurveyRunLocalState> = if (deviceId == "device-a") flow {
            withContext(NonCancellable) { oldGate.await() }
            emit(states.getValue(deviceId))
        } else flowOf(states[deviceId] ?: SurveyRunLocalState())
        override suspend fun saveSelection(deviceId: String, selection: SurveySelection?) = Unit
        override suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?) {
            savedPending = pending
        }
        override suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String) {
            if (deviceId == "device-a") acceptedGate?.let { gate ->
                withContext(NonCancellable) { gate.await() }
            }
        }
        override suspend fun saveAcknowledgedRun(deviceId: String, runId: String) {
            savedAcknowledgedRunId = runId
        }
    }

    private class FakeSource(
        project: SurveyProject,
        section: SurveySection,
        private val configuredPolicy: PipelineRunPolicy? = null,
        private val configuredPreflight: PipelinePreflight? = null,
        private val startFailure: Throwable? = null
    ) : SurveyRunDataSource {
        val connection = MutableStateFlow(SurveyDeviceConnection(null, false))
        var runResult: Result<PipelineRun>? = null
        var contextualReceipt: ContextualStartReceipt? = null
        private val projects = listOf(project)
        private val sections = listOf(section)
        override val deviceConnection: Flow<SurveyDeviceConnection> = connection
        override suspend fun projects() = Result.success(projects)
        override suspend fun createProject(request: SurveyLabelMutationRequest) = Result.failure<SurveyProject>(UnsupportedOperationException())
        override suspend fun sections(projectId: String) = Result.success(sections)
        override suspend fun createSection(projectId: String, request: SurveyLabelMutationRequest) = Result.failure<SurveySection>(UnsupportedOperationException())
        override suspend fun policy(pipelineId: String) = configuredPolicy?.let { Result.success(it) }
            ?: Result.failure(com.example.jetsoncontroller.data.network.JetsonApiException(
                404, "POLICY_NOT_CONFIGURED", null, "missing"
            ))
        override suspend fun savePolicy(pipelineId: String, request: UpdatePipelineRunPolicyRequest) = Result.failure<PipelineRunPolicy>(UnsupportedOperationException())
        override suspend fun preflight(pipelineId: String, request: PipelinePreflightRequest) =
            configuredPreflight?.let { Result.success(it) } ?: Result.failure(UnsupportedOperationException())
        override suspend fun contextualStart(pipelineId: String, request: ContextualStartRequest) =
            Result.failure<ManagedPipeline>(startFailure ?: UnsupportedOperationException())
        override suspend fun pipelineRun(runId: String) =
            runResult ?: Result.failure<PipelineRun>(UnsupportedOperationException())
        override suspend fun pipelines() = Result.success(listOf(ManagedPipeline(
            id = "pipe", label = "수집", entrypoint = "run.py", config = "config", virtualenv = ".venv",
            contextualStart = contextualReceipt
        )))
        override suspend fun roots() = Result.success(emptyList<RemoteRoot>())
    }
}
