package com.example.jetsoncontroller.ui.pipelines

import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.ManagedPipeline
import org.junit.Assert.*
import org.junit.Test

class TaskPresentationTest {
    @Test fun staleOrDisconnectedRunningIsNeverLive() {
        assertFalse(tasksAreFresh(false, 1000, 1001))
        assertFalse(tasksAreFresh(true, null, 1001))
        assertFalse(tasksAreFresh(true, 1000, 16001))
        assertFalse(tasksAreFresh(true, 1000, 999))
        assertTrue(tasksAreFresh(true, 1000, 16000))
        assertTrue(taskStateLabel(PipelineState.RUNNING, false).startsWith("현재 상태 미확인"))
    }
    @Test fun commandReceiptDoesNotBecomeRunningOrStopped() {
        assertEquals("시작 요청 중 · 실행 확인 대기", taskStateLabel(PipelineState.RUNNING, true, "start"))
        assertEquals("중지 요청 중 · 종료 확인 대기", taskStateLabel(PipelineState.STOPPED, true, "stop"))
        assertEquals("시작 요청 중", taskStateLabel(PipelineState.STARTING, true))
    }
    @Test fun runningNeedsActiveRunIdentityBeforeUiConfirmsIt() {
        val base = ManagedPipeline("capture", "Capture", state = PipelineState.RUNNING,
            entrypoint = "run.py", config = "config.yaml", virtualenv = "venv")
        assertEquals("실행 보고 · 실행 ID 확인 필요", taskStateLabel(base, true))
        assertTrue(taskStateLabel(base.copy(activeRunId = "capture/run-1"), true).startsWith("실행 중"))
    }
    @Test fun observationReconcilesOnlyTheRequestedTask() {
        val requests = mapOf("a" to "start", "b" to "stop", "c" to "unknown")
        assertEquals(requests, reconcileTaskRequests(requests, mapOf("a" to PipelineState.STARTING, "b" to PipelineState.RUNNING)))
        assertEquals(mapOf("c" to "unknown"), reconcileTaskRequests(requests,
            mapOf("a" to PipelineState.RUNNING, "b" to PipelineState.STOPPED)))
    }
}
