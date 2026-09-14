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
                        sectionB.surveySectionId, sectionB.revision)
                )
            )
        )
        val viewModel = SurveyRunViewModel(source, persistence)
        advanceUntilIdle()
        viewModel.open("pipe", "수집")
        advanceUntilIdle()

        source.connection.value = SurveyDeviceConnection("device-a", true)
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
        assertEquals("시작 결과 미확인 · 저장된 요청 ID 재사용", startReadinessLabel(state))
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
        viewModel.open("pipe", "수집")
        source.connection.value = SurveyDeviceConnection("device", true)
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

    private class FakePersistence(
        private val oldGate: CompletableDeferred<Unit>,
        private val states: Map<String, SurveyRunLocalState>
    ) : SurveyRunPersistence {
        var savedPending: PendingContextualStart? = null
        override fun state(deviceId: String): Flow<SurveyRunLocalState> = if (deviceId == "device-a") flow {
            withContext(NonCancellable) { oldGate.await() }
            emit(states.getValue(deviceId))
        } else flowOf(states[deviceId] ?: SurveyRunLocalState())
        override suspend fun saveSelection(deviceId: String, selection: SurveySelection?) = Unit
        override suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?) {
            savedPending = pending
        }
        override suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String) = Unit
    }

    private class FakeSource(
        project: SurveyProject,
        section: SurveySection,
        private val configuredPolicy: PipelineRunPolicy? = null,
        private val configuredPreflight: PipelinePreflight? = null,
        private val startFailure: Throwable? = null
    ) : SurveyRunDataSource {
        val connection = MutableStateFlow(SurveyDeviceConnection(null, false))
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
        override suspend fun pipelineRun(runId: String) = Result.failure<PipelineRun>(UnsupportedOperationException())
        override suspend fun pipelines() = Result.success(listOf(ManagedPipeline(
            id = "pipe", label = "수집", entrypoint = "run.py", config = "config", virtualenv = ".venv"
        )))
        override suspend fun roots() = Result.success(emptyList<RemoteRoot>())
    }
}
