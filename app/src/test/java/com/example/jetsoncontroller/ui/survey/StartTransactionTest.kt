package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.data.survey.*
import com.example.jetsoncontroller.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StartTransactionTest {
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun `manual repeat restores context without preflight or creation until the start action`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource()
        val store = StartTestPersistence()
        val vm = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        assertEquals(source.project, vm.uiState.value.selectedProject)
        assertEquals(source.section, vm.uiState.value.selectedSection)
        assertTrue(vm.uiState.value.canPreflight)
        assertEquals(0, source.preflightCalls)
        assertTrue(source.projectRequests.isEmpty())
        assertTrue(source.sectionRequests.isEmpty())

        vm.runPreflight()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canStart)
        assertTrue(source.starts.isEmpty())
        val pending = requireNotNull(vm.start())
        assertNull(vm.start()) // A second confirmation cannot issue another request.
        advanceUntilIdle()
        assertEquals(listOf(pending.clientRequestId), source.starts.map { it.clientRequestId })
        assertEquals(pending, store.read("device").pendingStart)
        assertFalse(vm.uiState.value.canStart)
    }

    @Test fun `ticket change during pending persistence rejects the wire command and keeps the draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource()
        val draft = startTestDraft().copy(serverProfileId = "profile", ticketRowId = "row", ticketRevision = "r1",
            geometryRevision = "g1", geometryHash = "hash")
        val store = StartTestPersistence(startTestLocalState().copy(preparationDraft = draft))
        val vm = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        vm.runPreflight()
        advanceUntilIdle()
        store.beforePendingSave = { source.rejectTicket = true }
        assertNotNull(vm.start())
        advanceUntilIdle()
        assertTrue(source.starts.isEmpty())
        assertNull(store.read("device").pendingStart)
        assertEquals(draft, store.read("device").preparationDraft)
        assertEquals(TicketChangedMessage, vm.uiState.value.error)
    }

    @Test fun `pending start survives process death and retries the same complete tuple`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource()
        val draft = startTestDraft().copy(serverProfileId = "profile", ticketRowId = "row", ticketRevision = "r1",
            geometryRevision = "geometry", geometryHash = "hash", projectRequestId = "project-request",
            sectionRequestId = "section-request")
        val store = StartTestPersistence(startTestLocalState().copy(preparationDraft = draft))
        val first = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        first.runPreflight()
        advanceUntilIdle()
        val pending = requireNotNull(first.start())
        advanceUntilIdle()

        val recreated = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        assertEquals(pending, recreated.uiState.value.pendingStart)
        assertFalse(recreated.uiState.value.canPreflight)
        recreated.retryPendingStart()
        advanceUntilIdle()
        assertEquals(2, source.starts.size)
        assertEquals(source.starts.first(), source.starts.last())
        assertEquals("geometry", store.read("device").pendingStart?.geometryRevision)
        assertEquals("hash", store.read("device").pendingStart?.geometryHash)
        assertEquals("project-request", store.read("device").pendingStart?.projectRequestId)
        assertEquals("section-request", store.read("device").pendingStart?.sectionRequestId)
    }

    @Test fun `device switch with an open draft cannot start on the replacement device`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource()
        val store = StartTestPersistence(startTestLocalState().copy(preparationDraft = startTestDraft()))
        val vm = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        vm.runPreflight()
        advanceUntilIdle()
        val target = requireNotNull(vm.startTarget())
        source.connection.value = SurveyDeviceConnection("other-device", false)
        advanceUntilIdle()
        assertNull(vm.start(target))
        advanceUntilIdle()
        assertTrue(source.starts.isEmpty())
        assertEquals(startTestDraft(), store.read("device").preparationDraft)
        assertNull(store.read("other-device").preparationDraft)
    }

    @Test fun `preflight creates new context once with persisted request ids and checks the created ids`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource()
        val store = StartTestPersistence()
        val vm = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        vm.createProject("새 프로젝트")
        vm.createSection("새 구간")
        advanceUntilIdle()
        val draft = requireNotNull(store.read("device").preparationDraft)
        assertNotNull(draft.projectRequestId)
        assertNotNull(draft.sectionRequestId)
        assertTrue(draft.project is PreparationSelection.CreateNew)
        assertTrue(draft.section is PreparationSelection.CreateNew)
        assertTrue(source.projectRequests.isEmpty())
        assertTrue(source.sectionRequests.isEmpty())
        source.onCreate = {
            val saved = requireNotNull(store.read("device").preparationDraft)
            assertEquals(draft.projectRequestId, saved.projectRequestId)
            assertEquals(draft.sectionRequestId, saved.sectionRequestId)
        }
        vm.runPreflight()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.canStart)
        assertEquals(listOf(draft.projectRequestId), source.projectRequests.map { it.clientRequestId })
        assertEquals(listOf(draft.sectionRequestId), source.sectionRequests.map { it.clientRequestId })
        assertEquals(listOf("created-project"), source.sectionProjectIds)
        val expectedCheck = PipelinePreflightRequest("created-project", "created-section", 1, 1, source.policy.revision)
        assertEquals(listOf("pipe" to expectedCheck), source.preflightRequests)
        assertEquals(listOf("project", "section", "preflight"), source.events)
        vm.runPreflight()
        advanceUntilIdle()
        assertEquals(listOf(draft.projectRequestId), source.projectRequests.map { it.clientRequestId })
        assertEquals(listOf(draft.sectionRequestId), source.sectionRequests.map { it.clientRequestId })
        assertEquals(listOf("pipe" to expectedCheck, "pipe" to expectedCheck), source.preflightRequests)
        val saved = requireNotNull(store.read("device").preparationDraft)
        assertEquals(draft.projectRequestId, saved.projectRequestId)
        assertEquals(draft.sectionRequestId, saved.sectionRequestId)
        assertTrue(source.starts.isEmpty())
    }

    @Test fun `preflight retries persisted creation ids after lost responses and process death`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource()
        val store = StartTestPersistence()
        val vm = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        vm.createProject("새 프로젝트")
        vm.createSection("새 구간")
        advanceUntilIdle()
        val draft = requireNotNull(store.read("device").preparationDraft)
        source.loseProjectResponse = true
        vm.runPreflight()
        advanceUntilIdle()
        assertEquals(draft, store.read("device").preparationDraft)
        assertEquals(0, source.preflightCalls)

        val recreated = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        source.loseProjectResponse = false
        source.loseSectionResponse = true
        recreated.runPreflight()
        advanceUntilIdle()
        assertEquals(0, source.preflightCalls)
        source.loseSectionResponse = false
        recreated.runPreflight()
        advanceUntilIdle()
        assertEquals(listOf(draft.projectRequestId, draft.projectRequestId), source.projectRequests.map { it.clientRequestId })
        assertEquals(listOf(draft.sectionRequestId, draft.sectionRequestId), source.sectionRequests.map { it.clientRequestId })
        assertEquals(1, source.createdProjects.size)
        assertEquals(1, source.createdSections.size)
        assertEquals(1, source.preflightCalls)
        assertEquals("created-project", source.preflightRequests.single().second.surveyProjectId)
        assertEquals("created-section", source.preflightRequests.single().second.surveySectionId)
        assertTrue(recreated.uiState.value.canStart)
        assertTrue(source.starts.isEmpty())
    }

    @Test fun `accepted start clears the draft and retains ticket and geometry for the report outbox`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource().also { it.acceptStart = true }
        val draft = startTestDraft().copy(ticketRowId = "row", ticketRevision = "r1", serverProfileId = "profile",
            geometryRevision = "g1", geometryHash = "hash")
        val store = StartTestPersistence(startTestLocalState().copy(preparationDraft = draft))
        val vm = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        vm.runPreflight()
        advanceUntilIdle()
        val pending = requireNotNull(vm.start())
        advanceUntilIdle()
        val persisted = store.read("device")
        assertNull(persisted.preparationDraft)
        assertNull(persisted.pendingStart)
        assertEquals(pending, persisted.acceptedStarts["accepted"])
        assertEquals("accepted", vm.uiState.value.unconfirmedRunId)
        assertFalse(vm.uiState.value.canStart)
    }

    @Test fun `a project choice without a section survives recreation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = StartTestSource()
        val store = StartTestPersistence(startTestLocalState().copy(selection = null))
        val vm = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        vm.selectProject(source.project)
        advanceUntilIdle()
        val recreated = SurveyRunViewModel(source, store)
        advanceUntilIdle()
        assertEquals(source.project, recreated.uiState.value.selectedProject)
        assertNull(recreated.uiState.value.selectedSection)
        assertFalse(recreated.uiState.value.canPreflight)
    }
}

internal fun startTestDraft() = PreparationDraft("device", pipelineId = "pipe",
    project = PreparationSelection.Existing("project", 1), section = PreparationSelection.Existing("section", 1))

internal fun startTestLocalState() = SurveyRunLocalState(
    selection = SurveySelection("project", 1, "section", 1), lastPipelineId = "pipe")

internal class StartTestPersistence(initial: SurveyRunLocalState = startTestLocalState()) : SurveyRunPersistence {
    private val values = mutableMapOf("device" to encode(initial))
    var beforePendingSave: (() -> Unit)? = null
    fun read(device: String) = decode(values[device])
    private fun update(device: String, change: (SurveyRunLocalState) -> SurveyRunLocalState) {
        values[device] = encode(change(read(device)))
    }
    override fun state(deviceId: String): Flow<SurveyRunLocalState> = flowOf(read(deviceId))
    override suspend fun saveSelection(deviceId: String, selection: SurveySelection?) = update(deviceId) { it.copy(selection = selection) }
    override suspend fun savePreparationDraft(deviceId: String, draft: PreparationDraft?) = update(deviceId) { it.copy(preparationDraft = draft) }
    override suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?) {
        if (pending != null) beforePendingSave?.invoke()
        update(deviceId) { it.copy(pendingStart = pending) }
    }
    override suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String) = update(deviceId) {
        it.copy(pendingStart = null, preparationDraft = null, lastRunId = runId, lastPipelineId = pipelineId,
            acceptedStarts = it.acceptedStarts + (runId to requireNotNull(it.pendingStart)))
    }
    override suspend fun saveAcknowledgedRun(deviceId: String, runId: String) = update(deviceId) { it.copy(acknowledgedRunId = runId) }
}

internal class StartTestSource : SurveyRunDataSource {
    val connection = MutableStateFlow(SurveyDeviceConnection("device", true))
    override val deviceConnection: Flow<SurveyDeviceConnection> = connection
    val project = SurveyProject("project", "수원시 도로대장", 1, "created", "updated")
    val section = SurveySection("section", "project", "신월4거리", 1, "created", "updated")
    val policy = PipelineRunPolicy(pipelineId = "pipe", revision = "policy", outputRootId = "data",
        requiredSensors = listOf("camera", "gnss"), updatedAt = "updated")
    var rejectTicket = false
    var loseProjectResponse = false
    var loseSectionResponse = false
    var acceptStart = false
    var preflightCalls = 0
    var preflightGate: CompletableDeferred<Unit>? = null
    var onCreate: (() -> Unit)? = null
    val events = mutableListOf<String>()
    val preflightRequests = mutableListOf<Pair<String, PipelinePreflightRequest>>()
    val sectionProjectIds = mutableListOf<String>()
    val projectRequests = mutableListOf<SurveyLabelMutationRequest>()
    val sectionRequests = mutableListOf<SurveyLabelMutationRequest>()
    val starts = mutableListOf<ContextualStartRequest>()
    val createdProjects = mutableMapOf<String, SurveyProject>()
    val createdSections = mutableMapOf<String, SurveySection>()
    override suspend fun projects() = Result.success(listOf(project) + createdProjects.values)
    override suspend fun sections(projectId: String) = Result.success((listOf(section) + createdSections.values)
        .filter { it.surveyProjectId == projectId })
    override suspend fun createProject(request: SurveyLabelMutationRequest): Result<SurveyProject> {
        onCreate?.invoke()
        events += "project"
        projectRequests += request
        val created = createdProjects.getOrPut(requireNotNull(request.clientRequestId)) {
            project.copy(surveyProjectId = "created-project", label = request.label)
        }
        return if (loseProjectResponse) Result.failure(IOException("response lost")) else Result.success(created)
    }
    override suspend fun createSection(projectId: String, request: SurveyLabelMutationRequest): Result<SurveySection> {
        onCreate?.invoke()
        events += "section"
        sectionProjectIds += projectId
        sectionRequests += request
        val created = createdSections.getOrPut(requireNotNull(request.clientRequestId)) {
            section.copy(surveyProjectId = projectId, surveySectionId = "created-section", label = request.label)
        }
        return if (loseSectionResponse) Result.failure(IOException("response lost")) else Result.success(created)
    }
    override suspend fun policy(pipelineId: String) = Result.success(policy)
    override suspend fun savePolicy(pipelineId: String, request: UpdatePipelineRunPolicyRequest) = Result.success(policy)
    override suspend fun preflight(pipelineId: String, request: PipelinePreflightRequest): Result<PipelinePreflight> {
        preflightCalls++
        events += "preflight"
        preflightRequests += pipelineId to request
        preflightGate?.await()
        val checkedProject = (listOf(project) + createdProjects.values).single { it.surveyProjectId == request.surveyProjectId }
        val checkedSection = (listOf(section) + createdSections.values).single { it.surveySectionId == request.surveySectionId }
        return Result.success(checked(project = checkedProject, section = checkedSection))
    }
    override suspend fun contextualStart(pipelineId: String, request: ContextualStartRequest): Result<ManagedPipeline> {
        starts += request
        if (!acceptStart) return Result.failure(IOException("response lost"))
        val check = checked()
        val output = PipelineRunOutput("data", "runs/accepted", "output")
        val upload = UploadContext(surveyProjectId = "project", surveySectionId = "section", runId = "accepted",
            deviceId = "device", pipelineId = "pipe", sourceRevision = "source", configSha256 = "config",
            outputId = "output", createdAt = "created")
        return Result.success(pipelines().getOrThrow().single().copy(contextualStart = ContextualStartReceipt(
            "accepted", request.clientRequestId, "STARTED", check.contextSnapshot, check, output, upload, "/runs/accepted")))
    }
    override suspend fun pipelineRun(runId: String) = Result.failure<PipelineRun>(IOException("unknown run"))
    override suspend fun runLog(pipelineId: String, logId: String) = Result.success("")
    override suspend fun roots() = Result.success(emptyList<RemoteRoot>())
    override suspend fun pipelines() = Result.success(listOf(ManagedPipeline("pipe", "데이터 수집",
        state = PipelineState.STOPPED, entrypoint = "run.py", config = "config", virtualenv = ".venv")))
    override suspend fun revalidateTicket(draft: PreparationDraft): Result<Unit> =
        if (rejectTicket) Result.failure(IllegalStateException("changed")) else Result.success(Unit)

    fun checked(now: Long = System.currentTimeMillis(), project: SurveyProject = this.project,
        section: SurveySection = this.section): PipelinePreflight {
        val context = SurveyContextSnapshot(deviceId = "device", surveyProjectId = project.surveyProjectId,
            surveyProjectLabel = project.label, surveyProjectRevision = 1, surveySectionId = section.surveySectionId,
            surveySectionLabel = section.label, surveySectionRevision = 1, capturedAt = "captured")
        return PipelinePreflight(preflightId = "preflight", pipelineId = "pipe", deviceId = "device",
            surveyProject = SurveyProjectSummary(project.surveyProjectId, project.label, 1),
            surveySection = SurveySectionSummary(section.surveySectionId, project.surveyProjectId, section.label, 1),
            contextSnapshot = context, policy = policy, sourceRevision = "source", configRevision = "config",
            checkedAt = "checked", checkedAtEpochMillis = now,
            checks = PipelinePreflightChecks(PreflightCheck("READY"),
                PreflightStorageCheck("READY", "data", "", 512_000_000_000, 20_000_000_000),
                listOf(PreflightSensorCheck("camera", "REQUIRED", "ACTIVE"), PreflightSensorCheck("gnss", "REQUIRED", "ACTIVE"))),
            ready = true)
    }
}
