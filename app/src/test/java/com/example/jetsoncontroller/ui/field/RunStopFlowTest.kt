package com.example.jetsoncontroller.ui.field

import androidx.compose.runtime.saveable.SaverScope
import com.example.jetsoncontroller.model.*
import org.junit.Assert.*
import org.junit.Test

class RunStopFlowTest {
    private val active = runStopFixture()

    @Test fun `stop needs explicit confirmation and sends only once`() {
        val requested = transition(RunStopFlow(), RunStopEvent.REQUEST)
        assertEquals(RunStopStep.CONFIRMING, requested.flow.step)
        assertNull(requested.command)
        assertNull(requested.openResult)
        val confirmed = transition(requested.flow, RunStopEvent.CONFIRM)
        assertEquals(runTarget(active), confirmed.command)
        assertEquals(RunStopStep.STOPPING, confirmed.flow.step)
        assertNull(transition(confirmed.flow, RunStopEvent.CONFIRM).command)
        assertNull(transition(confirmed.flow, RunStopEvent.REQUEST).command)
    }

    @Test fun `home stop request opens only the exact run confirmation without sending`() {
        val target = runTarget(active)
        val requested = reduceRunStop(RunStopFlow(), RunStopEvent.REQUEST, active, true, true,
            requestedTarget = target)
        assertEquals(RunStopStep.CONFIRMING, requested.flow.step)
        assertEquals(target, requested.flow.target)
        assertNull(requested.command)
        listOf(active.copy(deviceId = "other-device"), active.copy(pipelineId = "other-task"),
            active.copy(runId = "other-run")).forEach { changed ->
            val rejected = reduceRunStop(RunStopFlow(), RunStopEvent.REQUEST, changed, true, true,
                requestedTarget = target)
            assertEquals(RunStopStep.IDLE, rejected.flow.step)
            assertNull(rejected.command)
        }
        assertEquals(RunStopStep.IDLE,
            reduceRunStop(RunStopFlow(), RunStopEvent.REQUEST, active, false, true,
                requestedTarget = target).flow.step)
    }

    @Test fun `remote completion and dismissal stay on the run`() {
        val ended = active.copy(active = false, state = "STOPPED")
        assertNull(transition(RunStopFlow(), RunStopEvent.OBSERVE, ended).openResult)
        val confirmation = transition(RunStopFlow(), RunStopEvent.REQUEST).flow
        val dismissed = transition(confirmation, RunStopEvent.DISMISS).flow
        assertEquals(RunStopStep.IDLE, dismissed.step)
        assertNull(transition(dismissed, RunStopEvent.OBSERVE, ended).openResult)
        assertNull(transition(confirmation, RunStopEvent.OBSERVE, ended).openResult)
    }

    @Test fun `confirmation revalidates device run and pipeline before sending`() {
        val confirmation = transition(RunStopFlow(), RunStopEvent.REQUEST).flow
        listOf(active.copy(deviceId = "other-device"), active.copy(runId = "other-run"),
            active.copy(pipelineId = "other-task"), null).forEach { changed ->
            val result = transition(confirmation, RunStopEvent.CONFIRM, changed)
            assertNull(result.command)
            assertEquals(RunStopStep.IDLE, result.flow.step)
        }
        assertNotNull(transition(confirmation, RunStopEvent.CONFIRM, active.copy(deviceId = "DEVICE-A")).command)
    }

    @Test fun `unreachable pending busy and unavailable cannot confirm stop`() {
        val confirmation = transition(RunStopFlow(), RunStopEvent.REQUEST).flow
        assertNull(reduceRunStop(confirmation, RunStopEvent.CONFIRM, active, false, true).command)
        assertNull(reduceRunStop(confirmation, RunStopEvent.CONFIRM, active, true, false).command)
        assertNull(reduceRunStop(confirmation, RunStopEvent.CONFIRM, active, true, true, awaitingStart = true).command)
        assertNull(reduceRunStop(confirmation, RunStopEvent.CONFIRM, active, true, true, stopInProgress = true).command)
        assertEquals(RunStopStep.CONFIRMING,
            reduceRunStop(confirmation, RunStopEvent.OBSERVE, active, false, false).flow.step)
    }

    @Test fun `only matching terminal evidence opens a confirmed stop result once`() {
        val stopped = transition(transition(RunStopFlow(), RunStopEvent.REQUEST).flow, RunStopEvent.CONFIRM).flow
        assertNull(transition(stopped, RunStopEvent.OBSERVE).openResult)
        assertNull(transition(stopped, RunStopEvent.OBSERVE, null).openResult)
        assertNull(transition(stopped, RunStopEvent.OBSERVE, active.copy(active = false, state = "UNKNOWN")).openResult)
        val ended = active.copy(active = false, state = "STOPPED")
        assertNull(reduceRunStop(stopped, RunStopEvent.OBSERVE, ended, false, false).openResult)
        val result = transition(stopped, RunStopEvent.OBSERVE, ended)
        assertEquals(runTarget(active), result.openResult)
        assertEquals(RunStopStep.RESULT_OPENED, result.flow.step)
        assertNull(transition(result.flow, RunStopEvent.OBSERVE, ended).openResult)
        assertNull(transition(stopped, RunStopEvent.OBSERVE, ended.copy(runId = "newer")).openResult)
    }

    @Test fun `saved stop resumes observation without repeating the command`() {
        val stopped = transition(transition(RunStopFlow(), RunStopEvent.REQUEST).flow, RunStopEvent.CONFIRM).flow
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(RunStopFlowSaver) { scope.save(stopped) }!!
        val restored = RunStopFlowSaver.restore(saved)!!
        assertEquals(stopped, restored)
        val observation = transition(restored, RunStopEvent.OBSERVE)
        assertNull(observation.command)
        val result = transition(observation.flow, RunStopEvent.OBSERVE, active.copy(active = false, state = "COMPLETED"))
        assertEquals(runTarget(active), result.openResult)
    }

    @Test fun `failed stop permits a fresh explicit retry and no later automatic result`() {
        val stopped = transition(transition(RunStopFlow(), RunStopEvent.REQUEST).flow, RunStopEvent.CONFIRM).flow
        val failed = transition(stopped, RunStopEvent.FAILURE).flow
        assertEquals(RunStopStep.IDLE, failed.step)
        assertNull(transition(failed, RunStopEvent.OBSERVE, active.copy(active = false, state = "STOPPED")).openResult)
        assertEquals(RunStopStep.CONFIRMING, transition(failed, RunStopEvent.REQUEST).flow.step)
    }

    @Test fun `all P4 states have deterministic precedence`() {
        assertEquals(ActiveRunState.START_CHECKING, activeRunState(active, false, true, true, false))
        assertEquals(ActiveRunState.STOP_CHECKING, activeRunState(active, false, false, true, false))
        assertEquals(ActiveRunState.DISCONNECTED, activeRunState(active, false, false, false, false))
        assertEquals(ActiveRunState.COLLECTING, activeRunState(active, true, false, false, true))
        assertEquals(ActiveRunState.UNKNOWN, activeRunState(active, true, false, false, false))
        assertEquals(ActiveRunState.UNKNOWN, activeRunState(null, true, false, false, false))
        assertEquals(ActiveRunState.ENDED, activeRunState(active.copy(active = false, state = "STOPPED"), true, false, false, false))
    }

    private fun transition(flow: RunStopFlow, event: RunStopEvent, run: PipelineRun? = active) =
        reduceRunStop(flow, event, run, online = true, stopAvailable = true)
}

internal fun runStopFixture(): PipelineRun {
    val context = SurveyContextSnapshot(deviceId = "device-a", surveyProjectId = "project", surveyProjectLabel = "수원시 도로대장",
        surveyProjectRevision = 1, surveySectionId = "section", surveySectionLabel = "신월4거리 북측 보조 구간",
        surveySectionRevision = 1, capturedAt = "2026-10-20T00:00:00Z")
    val policy = PipelineRunPolicy(pipelineId = "capture", revision = "policy", outputRootId = "data", updatedAt = "now")
    val preflight = PipelinePreflight(preflightId = "preflight", pipelineId = "capture", deviceId = "device-a",
        surveyProject = SurveyProjectSummary("project", "수원시 도로대장", 1),
        surveySection = SurveySectionSummary("section", "project", "신월4거리 북측 보조 구간", 1),
        contextSnapshot = context, policy = policy, sourceRevision = "source", configRevision = "config",
        checkedAt = "now", checkedAtEpochMillis = 10_000L,
        checks = PipelinePreflightChecks(storage = PreflightStorageCheck(rootId = "data", path = "run-1")))
    return PipelineRun(runId = "run-1", logId = "log-1", pipelineId = "capture", deviceId = "device-a",
        state = "RUNNING", startedAt = "2026-10-20T00:00:00Z", active = true,
        contextSnapshot = context, policySnapshot = policy, preflightSnapshot = preflight,
        sourceRevision = "source", configRevision = "config", output = PipelineRunOutput("data", "run-1", "output"),
        uploadContext = UploadContext(surveyProjectId = "project", surveySectionId = "section", runId = "run-1",
            deviceId = "device-a", pipelineId = "capture", sourceRevision = "source", configSha256 = "config",
            outputId = "output", createdAt = "now"),
        telemetry = RunTelemetry(runId = "run-1", outputId = "output", sourceRevision = "source",
            observedAtEpochMillis = 10_000L, bytesObservedAtEpochMillis = 10_000L,
            durationMillis = 754_000L, collectedBytes = 1_200_000_000L, collectionBytesState = "OBSERVED"),
        quality = RunQuality(rtkFixRatio = 0.98, rtkObservedDurationMillis = 754_000L))
}
