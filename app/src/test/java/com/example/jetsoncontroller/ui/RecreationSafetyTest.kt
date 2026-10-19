package com.example.jetsoncontroller.ui

import androidx.lifecycle.SavedStateHandle
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.data.survey.SurveyRunLocalState
import com.example.jetsoncontroller.data.survey.SurveyRunPersistence
import com.example.jetsoncontroller.data.survey.decode
import com.example.jetsoncontroller.data.survey.encode
import com.example.jetsoncontroller.model.PreparationDraft
import com.example.jetsoncontroller.model.PreparationSelection
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.model.SurveyLabelMutationRequest
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.PipelinePreflight
import com.example.jetsoncontroller.model.PipelinePreflightRequest
import com.example.jetsoncontroller.model.PipelineRunPolicy
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.ContextualStartRequest
import com.example.jetsoncontroller.model.UpdatePipelineRunPolicyRequest
import com.example.jetsoncontroller.ui.pairing.PairingPhase
import com.example.jetsoncontroller.ui.pairing.PairingRecreationState
import com.example.jetsoncontroller.ui.pairing.restorePairingRecreationState
import com.example.jetsoncontroller.ui.pairing.savePairingRecreationState
import com.example.jetsoncontroller.ui.pairing.shouldConnectPairingCandidate
import com.example.jetsoncontroller.ui.survey.SurveyDeviceConnection
import com.example.jetsoncontroller.ui.survey.SurveyRecreationState
import com.example.jetsoncontroller.ui.survey.SurveyRunDataSource
import com.example.jetsoncontroller.ui.survey.SurveyRunViewModel
import com.example.jetsoncontroller.ui.survey.restoreSurveyRecreationState
import com.example.jetsoncontroller.ui.survey.saveSurveyRecreationState
import com.example.jetsoncontroller.model.PairingInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecreationSafetyTest {

    @Test
    fun `restored pairing presentation never auto connects a scanned candidate`() {
        val originalHandle = SavedStateHandle()
        val original = PairingRecreationState(
            phase = PairingPhase.SEARCHING,
            pairingInfo = PairingInfo(1, "device-id", "secret"),
            observeRepositoryState = true
        )
        savePairingRecreationState(originalHandle, original)

        val restored = restorePairingRecreationState(copyHandle(originalHandle))

        assertEquals(original, restored)
        assertFalse(shouldConnectPairingCandidate(restored.phase, userStartedAttempt = false))
        assertTrue(shouldConnectPairingCandidate(restored.phase, userStartedAttempt = true))
    }

    @Test
    fun `survey recreation keeps route draft and pending request without sending another start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val draft = PreparationDraft(
                deviceId = "device-id",
                serverProfileId = "profile-id",
                ticketRowId = "ticket-row",
                ticketRevision = "r4",
                pipelineId = "pipeline-id",
                project = PreparationSelection.CreateFromTicket("Project"),
                section = PreparationSelection.CreateNew("Section"),
                projectRequestId = "project-create-request",
                sectionRequestId = "section-create-request",
                geometryRevision = "geometry-r2",
                geometryHash = "geometry-hash"
            )
            val pending = PendingContextualStart(
                pipelineId = "pipeline-id",
                surveyProjectId = "project-id",
                surveyProjectRevision = 2,
                surveySectionId = "section-id",
                surveySectionRevision = 3,
                policyRevision = "policy-r5",
                preflightId = "preflight-id",
                clientRequestId = "start-request-id",
                deviceId = "device-id",
                serverProfileId = "profile-id",
                ticketRowId = "ticket-row",
                ticketRevision = "r4",
                geometryRevision = "geometry-r2",
                geometryHash = "geometry-hash"
            )
            val persistence = MemoryPersistence(
                SurveyRunLocalState(
                    pendingStart = pending,
                    lastPipelineId = "pipeline-id",
                    preparationDraft = draft
                )
            )
            val source = NoAutoStartSource()
            source.startRequests += ContextualStartRequest(
                "project-id", "section-id", 2, 3, "policy-r5", "preflight-id", "start-request-id"
            )
            val firstHandle = SavedStateHandle()
            val first = SurveyRunViewModel(source, persistence, savedStateHandle = firstHandle)
            advanceUntilIdle()

            assertEquals(draft, first.uiState.value.preparationDraft)
            assertEquals(pending, first.uiState.value.pendingStart)
            assertEquals("start-request-id", restoreSurveyRecreationState(firstHandle).pendingStartRequestId)

            val restoredHandle = copyHandle(firstHandle)
            val recreated = SurveyRunViewModel(source, persistence, savedStateHandle = restoredHandle)
            advanceUntilIdle()

            assertEquals("pipeline-id", recreated.uiState.value.pipelineId)
            assertEquals(draft, recreated.uiState.value.preparationDraft)
            assertEquals(pending, recreated.uiState.value.pendingStart)
            assertEquals(
                SurveyRecreationState("device-id", "pipeline-id", "", "start-request-id"),
                restoreSurveyRecreationState(restoredHandle)
            )
            assertEquals(listOf("start-request-id"), source.startRequests.map { it.clientRequestId })
            assertFalse(restoredHandle.keys().any { it.contains("preparationDraft") })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun copyHandle(handle: SavedStateHandle): SavedStateHandle = SavedStateHandle(
        handle.keys().associateWith { key -> handle.get<Any?>(key) }
    )

    private class MemoryPersistence(initial: SurveyRunLocalState) : SurveyRunPersistence {
        private var encoded = encode(initial)

        override fun state(deviceId: String): Flow<SurveyRunLocalState> = flowOf(decode(encoded))

        override suspend fun saveSelection(deviceId: String, selection: com.example.jetsoncontroller.data.survey.SurveySelection?) =
            update { it.copy(selection = selection) }

        override suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?) =
            update { it.copy(pendingStart = pending) }

        override suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String) = update {
            it.copy(pendingStart = null, preparationDraft = null, lastPipelineId = pipelineId, lastRunId = runId)
        }

        override suspend fun saveAcknowledgedRun(deviceId: String, runId: String) = update {
            it.copy(acknowledgedRunId = runId)
        }

        override suspend fun savePreparationDraft(deviceId: String, draft: PreparationDraft?) = update {
            it.copy(preparationDraft = draft)
        }

        override suspend fun saveUnconfirmedRun(deviceId: String, runId: String?) = update {
            it.copy(unconfirmedRunId = runId)
        }

        private fun update(transform: (SurveyRunLocalState) -> SurveyRunLocalState) {
            encoded = encode(transform(decode(encoded)))
        }
    }

    private class NoAutoStartSource : SurveyRunDataSource {
        override val deviceConnection: Flow<SurveyDeviceConnection> =
            flowOf(SurveyDeviceConnection("device-id", online = true))
        val startRequests = mutableListOf<ContextualStartRequest>()

        override suspend fun projects(): Result<List<SurveyProject>> = Result.success(emptyList())
        override suspend fun createProject(request: SurveyLabelMutationRequest): Result<SurveyProject> =
            Result.failure(UnsupportedOperationException())
        override suspend fun sections(projectId: String): Result<List<SurveySection>> = Result.success(emptyList())
        override suspend fun createSection(
            projectId: String,
            request: SurveyLabelMutationRequest
        ): Result<SurveySection> = Result.failure(UnsupportedOperationException())
        override suspend fun policy(pipelineId: String): Result<PipelineRunPolicy> =
            Result.failure(UnsupportedOperationException())
        override suspend fun savePolicy(
            pipelineId: String,
            request: UpdatePipelineRunPolicyRequest
        ): Result<PipelineRunPolicy> = Result.failure(UnsupportedOperationException())
        override suspend fun preflight(
            pipelineId: String,
            request: PipelinePreflightRequest
        ): Result<PipelinePreflight> = Result.failure(UnsupportedOperationException())
        override suspend fun contextualStart(
            pipelineId: String,
            request: ContextualStartRequest
        ): Result<ManagedPipeline> {
            startRequests += request
            return Result.failure(UnsupportedOperationException())
        }
        override suspend fun pipelineRun(runId: String): Result<PipelineRun> =
            Result.failure(UnsupportedOperationException())
        override suspend fun runLog(pipelineId: String, logId: String): Result<String> =
            Result.failure(UnsupportedOperationException())
        override suspend fun pipelines(): Result<List<ManagedPipeline>> = Result.success(emptyList())
        override suspend fun roots(): Result<List<RemoteRoot>> = Result.success(emptyList())
    }
}
