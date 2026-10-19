package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.data.survey.SurveyRunLocalState
import com.example.jetsoncontroller.data.survey.SurveyRunPersistence
import com.example.jetsoncontroller.data.survey.SurveySelection
import com.example.jetsoncontroller.data.survey.decode
import com.example.jetsoncontroller.data.survey.deviceScope
import com.example.jetsoncontroller.data.survey.encode
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.model.OperationState
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartRevalidationTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `start tuple revalidates device control pipeline context ticket geometry and both clocks`() {
        val source = FakeSource()
        val checked = source.checked().copy(checkedAtEpochMillis = 1_000)
        val draft = PreparationDraft("device", "profile", "row", "r4", "pipe",
            PreparationSelection.Existing("project", 1), PreparationSelection.Existing("section", 1),
            projectRequestId = "project-request", sectionRequestId = "section-request",
            geometryRevision = "g1", geometryHash = "hash", credentialRevision = "credentials")
        val state = SurveyRunUiState(deviceId = "device", online = true, pipelineId = "pipe",
            runStateConfirmed = true, selectedProject = source.project, selectedSection = source.section,
            policy = source.policy, preflight = checked, preflightReceivedAtElapsedRealtime = 5_000,
            preparationDraft = draft)
        val target = StartTarget(draft, "preflight", "policy")
        assertNull(startRevalidationError(target, state, 2_000, 6_000))
        val wrongStates = listOf(
            state.copy(deviceId = "another"), state.copy(online = false), state.copy(controlAvailable = false),
            state.copy(pipelineId = "other"), state.copy(restoringLocalState = true), state.copy(runStateConfirmed = false),
            state.copy(observedActiveRunCount = 2), state.copy(unconfirmedRunId = "run"),
            state.copy(pendingStart = pending()), state.copy(selectedProject = source.project.copy(revision = 2)),
            state.copy(selectedSection = source.section.copy(revision = 2)),
            state.copy(preflight = checked.copy(ready = false)), state.copy(preflight = checked.copy(pipelineId = "other")),
            state.copy(preflight = checked.copy(preflightId = "other")), state.copy(policy = source.policy.copy(revision = "changed")),
            state.copy(preflightReceivedAtElapsedRealtime = null),
            state.copy(preparationDraft = draft.copy(projectRequestId = "different-request"))
        )
        wrongStates.forEach { assertNotNull(startRevalidationError(target, it, 2_000, 6_000)) }
        listOf(draft.copy(ticketRevision = "r5"), draft.copy(ticketRowId = "other"),
            draft.copy(serverProfileId = "other"), draft.copy(geometryRevision = "g2"),
            draft.copy(geometryHash = "changed"), draft.copy(credentialRevision = "new-credentials")).forEach {
            assertEquals(TicketChangedMessage, startRevalidationError(target, state.copy(preparationDraft = it), 2_000, 6_000))
        }
        assertNull(startRevalidationError(target, state, 121_000, 125_000))
        assertNotNull(startRevalidationError(target, state, 121_001, 6_000))
        assertNotNull(startRevalidationError(target, state, 2_000, 125_001))
        assertNotNull(startRevalidationError(target, state, 999, 6_000))
        assertNotNull(startRevalidationError(target, state, 2_000, 4_999))
    }

    @Test
    fun `task precedes preflight and preparation only saves creation decisions with retry ids`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource()
        val persistence = MemoryPersistence()
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        viewModel.runPreflight()
        advanceUntilIdle()
        assertEquals(0, source.preflightCalls)
        viewModel.open("pipe", "수집")
        advanceUntilIdle()
        viewModel.createProject("새 조사")
        viewModel.createSection("새 구간")
        advanceUntilIdle()
        val decided = requireNotNull(viewModel.uiState.value.preparationDraft)
        assertNotNull(decided.projectRequestId)
        assertNotNull(decided.sectionRequestId)
        assertTrue(source.projectRequests.isEmpty())
        assertTrue(source.sectionRequests.isEmpty())
        assertEquals(0, source.preflightCalls)

        source.failProject = true
        viewModel.createStartContext(decided)
        advanceUntilIdle()
        assertEquals(decided.projectRequestId, source.projectRequests.single().clientRequestId)
        assertEquals(decided, persistence.read("device").preparationDraft)

        val recreated = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        source.failProject = false
        source.failSection = true
        recreated.createStartContext(requireNotNull(recreated.uiState.value.preparationDraft))
        advanceUntilIdle()
        assertEquals(listOf(decided.projectRequestId, decided.projectRequestId), source.projectRequests.map { it.clientRequestId })
        assertEquals(PreparationSelection.Existing("created-project", 1), recreated.uiState.value.preparationDraft?.project)
        source.failSection = false
        recreated.createStartContext(requireNotNull(recreated.uiState.value.preparationDraft))
        advanceUntilIdle()
        assertEquals(2, source.projectRequests.size)
        assertEquals(listOf(decided.sectionRequestId, decided.sectionRequestId), source.sectionRequests.map { it.clientRequestId })
        assertEquals(listOf("새 구간", "새 구간"), source.sectionRequests.map { it.label })
        assertEquals(0, source.preflightCalls)
        recreated.runPreflight()
        advanceUntilIdle()
        assertTrue(recreated.uiState.value.canStart)
        assertEquals(1, source.preflightCalls)
    }

    @Test
    fun `server revision changes and a new active run reject start before the command is sent`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource()
        val persistence = MemoryPersistence(selectedState())
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        viewModel.runPreflight()
        advanceUntilIdle()
        val target = requireNotNull(viewModel.startTarget())
        source.remoteProject = source.project.copy(revision = 2)
        viewModel.start(target)
        advanceUntilIdle()
        assertTrue(source.starts.isEmpty())
        assertNull(persistence.read("device").pendingStart)

        source.remoteProject = source.project
        source.remoteActiveRunId = "someone-else"
        viewModel.start(target)
        advanceUntilIdle()
        assertTrue(source.starts.isEmpty())

        source.remoteActiveRunId = null
        val ticketDraft = target.draft.copy(serverProfileId = "profile", ticketRowId = "row", ticketRevision = "r4")
        viewModel.setPreparationDraft(ticketDraft)
        advanceUntilIdle()
        viewModel.runPreflight()
        advanceUntilIdle()
        source.rejectTicket = true
        viewModel.start(requireNotNull(viewModel.startTarget()))
        advanceUntilIdle()
        assertTrue(source.starts.isEmpty())
        assertEquals(TicketChangedMessage, viewModel.uiState.value.error)
    }

    @Test
    fun `device switch while start awaits persistence never sends to either device`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource()
        val persistence = MemoryPersistence(selectedState())
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        viewModel.runPreflight()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        persistence.pendingGate = gate
        assertNotNull(viewModel.start())
        runCurrent()
        source.connection.value = SurveyDeviceConnection("other-device", true)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(source.starts.isEmpty())
        assertEquals("other-device", viewModel.uiState.value.deviceId)
        assertNull(viewModel.uiState.value.pendingStart)
    }

    @Test
    fun `unknown start survives process death and reconnection and only replays the persisted tuple`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource()
        val persistence = MemoryPersistence(selectedState())
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        viewModel.runPreflight()
        advanceUntilIdle()
        val submitted = requireNotNull(viewModel.start())
        assertNull(viewModel.start()) // Double tap before the coroutine even runs.
        advanceUntilIdle()
        assertEquals(submitted, persistence.read("device").pendingStart)
        assertEquals(OperationState.START_PENDING, viewModel.uiState.value.nextAction("GEO-X-1", 1).operation)
        source.connection.value = SurveyDeviceConnection("device", false)
        advanceUntilIdle()
        val recreated = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        assertEquals(OperationState.START_PENDING, recreated.uiState.value.nextAction("GEO-X-1", 1).operation)
        source.connection.value = SurveyDeviceConnection("device", true)
        advanceUntilIdle()
        assertFalse(recreated.uiState.value.canStart)
        assertEquals(submitted, recreated.uiState.value.pendingStart)
        recreated.retryPendingStart()
        advanceUntilIdle()
        assertEquals(listOf(submitted.clientRequestId, submitted.clientRequestId), source.starts.map { it.clientRequestId })
        assertEquals(source.starts[0], source.starts[1])
    }

    @Test
    fun `accepted start clears the draft and binds ticket geometry to the run without changing wire fields`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource().also { it.acceptStart = true }
        val persistence = MemoryPersistence(selectedState())
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        val draft = PreparationDraft("device", "profile", "row", "r4", "pipe",
            PreparationSelection.Existing("project", 1), PreparationSelection.Existing("section", 1),
            geometryRevision = "g1", geometryHash = "hash", credentialRevision = "credentials")
        viewModel.setPreparationDraft(draft)
        advanceUntilIdle()
        viewModel.runPreflight()
        advanceUntilIdle()
        val submitted = requireNotNull(viewModel.start())
        advanceUntilIdle()
        val restored = persistence.read("device")
        assertNull(restored.preparationDraft)
        assertNull(restored.pendingStart)
        assertEquals(submitted, restored.acceptedStarts["accepted"])
        assertEquals("g1", restored.acceptedStarts["accepted"]?.geometryRevision)
        assertEquals("hash", restored.acceptedStarts["accepted"]?.geometryHash)
        assertEquals("row", viewModel.uiState.value.acceptedStarts["accepted"]?.ticketRowId)
        assertEquals(setOf("surveyProjectId", "surveySectionId", "surveyProjectRevision", "surveySectionRevision",
            "policyRevision", "preflightId", "clientRequestId"),
            JsonParser.parseString(Gson().toJson(source.starts.single())).asJsonObject.keySet())
    }

    @Test
    fun `restored unconfirmed run stays locked until an exact device answer is received`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource()
        val persistence = MemoryPersistence(selectedState().copy(unconfirmedRunId = "uncertain"))
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        assertEquals("uncertain", viewModel.uiState.value.unconfirmedRunId)
        source.connection.value = SurveyDeviceConnection("device", false)
        advanceUntilIdle()
        source.connection.value = SurveyDeviceConnection("device", true)
        advanceUntilIdle()
        assertEquals("uncertain", viewModel.uiState.value.unconfirmedRunId)
        assertEquals(OperationState.RUN_UNCONFIRMED, viewModel.uiState.value.nextAction("GEO-X-1", 1).operation)
        assertFalse(viewModel.uiState.value.canStart)
        source.remoteRun = source.run("unrelated")
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("uncertain", viewModel.uiState.value.unconfirmedRunId)
        source.remoteRun = source.run("uncertain").copy(active = false)
        viewModel.refresh()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.unconfirmedRunId)
        assertNull(persistence.read("device").unconfirmedRunId)
    }

    @Test
    fun `stop confirmation rechecks exact device and active run and sends expectedRunId`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeSource()
        val run = source.run("run")
        source.remoteRun = run
        source.remoteActiveRunId = "run"
        val persistence = MemoryPersistence(selectedState().copy(lastRunId = "run"))
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        val target = requireNotNull(viewModel.stopTarget())
        assertTrue(stopTargetMatches(target, viewModel.uiState.value))
        assertFalse(stopTargetMatches(target.copy(deviceId = "other"), viewModel.uiState.value))
        assertFalse(stopTargetMatches(target.copy(runId = "other"), viewModel.uiState.value))
        source.remoteActiveRunId = "replacement-run"
        viewModel.stop(target)
        advanceUntilIdle()
        assertTrue(source.stops.isEmpty())
        source.remoteActiveRunId = "run"
        source.remoteRun = run.copy(active = false)
        viewModel.stop(target)
        advanceUntilIdle()
        assertTrue(source.stops.isEmpty())
        source.remoteRun = run
        viewModel.stop(target)
        advanceUntilIdle()
        assertEquals(listOf(target), source.stops)
    }

    private fun selectedState() = SurveyRunLocalState(selection = SurveySelection("project", 1, "section", 1), lastPipelineId = "pipe")

    private fun pending() = PendingContextualStart("pipe", "project", 1, "section", 1, "policy", "preflight", "request")

    private class MemoryPersistence(initial: SurveyRunLocalState = SurveyRunLocalState()) : SurveyRunPersistence {
        private val encoded = mutableMapOf(deviceScope("device") to encode(initial))
        var pendingGate: CompletableDeferred<Unit>? = null
        fun read(deviceId: String) = decode(encoded[deviceScope(deviceId)])
        private fun update(deviceId: String, transform: (SurveyRunLocalState) -> SurveyRunLocalState) {
            encoded[deviceScope(deviceId)] = encode(transform(read(deviceId)))
        }
        override fun state(deviceId: String): Flow<SurveyRunLocalState> = flowOf(read(deviceId))
        override suspend fun saveSelection(deviceId: String, selection: SurveySelection?) = update(deviceId) { it.copy(selection = selection) }
        override suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?) {
            val gate = pendingGate
            if (gate == null) update(deviceId) { it.copy(pendingStart = pending) }
            else withContext(NonCancellable) {
                gate.await()
                update(deviceId) { it.copy(pendingStart = pending) }
            }
        }
        override suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String) = update(deviceId) {
            it.copy(pendingStart = null, preparationDraft = null, unconfirmedRunId = null, lastPipelineId = pipelineId, lastRunId = runId,
                acceptedStarts = it.pendingStart?.let { pending -> it.acceptedStarts + (runId to pending) } ?: it.acceptedStarts)
        }
        override suspend fun saveAcknowledgedRun(deviceId: String, runId: String) = update(deviceId) { it.copy(acknowledgedRunId = runId) }
        override suspend fun savePreparationDraft(deviceId: String, draft: PreparationDraft?) = update(deviceId) { it.copy(preparationDraft = draft) }
        override suspend fun saveUnconfirmedRun(deviceId: String, runId: String?) = update(deviceId) { it.copy(unconfirmedRunId = runId) }
    }

    private class FakeSource : SurveyRunDataSource {
        val connection = MutableStateFlow(SurveyDeviceConnection("device", true))
        override val deviceConnection: Flow<SurveyDeviceConnection> = connection
        val project = SurveyProject("project", "조사", 1, "created", "updated")
        val section = SurveySection("section", "project", "구간", 1, "created", "updated")
        val policy = PipelineRunPolicy(pipelineId = "pipe", revision = "policy", outputRootId = "data", updatedAt = "updated")
        var remoteProject = project
        var createdProject: SurveyProject? = null
        var createdSection: SurveySection? = null
        var remoteActiveRunId: String? = null
        var remoteRun: PipelineRun? = null
        var failProject = false
        var failSection = false
        var rejectTicket = false
        var acceptStart = false
        var preflightCalls = 0
        val projectRequests = mutableListOf<SurveyLabelMutationRequest>()
        val sectionRequests = mutableListOf<SurveyLabelMutationRequest>()
        val starts = mutableListOf<ContextualStartRequest>()
        val stops = mutableListOf<StopTarget>()
        override suspend fun projects() = Result.success(listOfNotNull(remoteProject, createdProject))
        override suspend fun sections(projectId: String) = Result.success(listOfNotNull(section, createdSection).filter { it.surveyProjectId == projectId })
        override suspend fun createProject(request: SurveyLabelMutationRequest): Result<SurveyProject> {
            projectRequests += request
            if (failProject) return Result.failure(IOException("lost creation response"))
            return Result.success(SurveyProject("created-project", request.label, 1, "created", "updated").also { createdProject = it })
        }
        override suspend fun createSection(projectId: String, request: SurveyLabelMutationRequest): Result<SurveySection> {
            sectionRequests += request
            if (failSection) return Result.failure(IOException("lost creation response"))
            return Result.success(SurveySection("created-section", projectId, request.label, 1, "created", "updated").also { createdSection = it })
        }
        override suspend fun policy(pipelineId: String) = Result.success(policy)
        override suspend fun savePolicy(pipelineId: String, request: UpdatePipelineRunPolicyRequest) = Result.success(policy)
        override suspend fun preflight(pipelineId: String, request: PipelinePreflightRequest): Result<PipelinePreflight> {
            preflightCalls++
            return Result.success(checked(createdProject ?: project, createdSection ?: section))
        }
        override suspend fun contextualStart(pipelineId: String, request: ContextualStartRequest): Result<ManagedPipeline> {
            starts += request
            if (acceptStart) {
                val run = run("accepted")
                remoteRun = run
                remoteActiveRunId = run.runId
                val receipt = ContextualStartReceipt(run.runId, request.clientRequestId, "STARTED", run.contextSnapshot,
                    run.preflightSnapshot, run.output, run.uploadContext, "/runs/${run.runId}")
                return Result.success(pipelines().getOrThrow().single().copy(contextualStart = receipt))
            }
            return Result.failure(IOException("response lost"))
        }
        override suspend fun pipelineRun(runId: String) = remoteRun?.let { Result.success(it) } ?: Result.failure(IllegalStateException("unknown run"))
        override suspend fun runLog(pipelineId: String, logId: String) = Result.success("")
        override suspend fun roots() = Result.success(emptyList<RemoteRoot>())
        override suspend fun revalidateTicket(draft: PreparationDraft): Result<Unit> =
            if (rejectTicket) Result.failure(IllegalStateException("revision changed")) else Result.success(Unit)
        override suspend fun pipelines() = Result.success(listOf(ManagedPipeline("pipe", "수집", entrypoint = "run.py",
            config = "config", virtualenv = ".venv", activeRunId = remoteActiveRunId,
            state = if (remoteActiveRunId == null) PipelineState.STOPPED else PipelineState.RUNNING)))
        override suspend fun stop(target: StopTarget): Result<ManagedPipeline> {
            stops += target
            return Result.success(pipelines().getOrThrow().single())
        }
        fun checked(project: SurveyProject = this.project, section: SurveySection = this.section): PipelinePreflight {
            val context = SurveyContextSnapshot(deviceId = "device", surveyProjectId = project.surveyProjectId,
                surveyProjectLabel = project.label, surveyProjectRevision = project.revision,
                surveySectionId = section.surveySectionId, surveySectionLabel = section.label,
                surveySectionRevision = section.revision, capturedAt = "captured")
            return PipelinePreflight(preflightId = "preflight", pipelineId = "pipe", deviceId = "device",
                surveyProject = SurveyProjectSummary(project.surveyProjectId, project.label, project.revision),
                surveySection = SurveySectionSummary(section.surveySectionId, project.surveyProjectId, section.label, section.revision),
                contextSnapshot = context, policy = policy, sourceRevision = "source", configRevision = "config",
                checkedAt = "checked", checkedAtEpochMillis = System.currentTimeMillis(),
                checks = PipelinePreflightChecks(storage = PreflightStorageCheck("READY", "data", "")), ready = true)
        }
        fun run(id: String): PipelineRun {
            val checked = checked()
            return PipelineRun(runId = id, logId = "log", pipelineId = "pipe", deviceId = "device", state = "RUNNING",
                startedAt = "started", contextSnapshot = checked.contextSnapshot, policySnapshot = policy,
                preflightSnapshot = checked, sourceRevision = "source", configRevision = "config",
                output = PipelineRunOutput("data", "runs/$id", "output"), active = true,
                uploadContext = UploadContext(surveyProjectId = "project", surveySectionId = "section", runId = id,
                    deviceId = "device", pipelineId = "pipe", sourceRevision = "source", configSha256 = "config",
                    outputId = "output", createdAt = "created"))
        }
    }
}
