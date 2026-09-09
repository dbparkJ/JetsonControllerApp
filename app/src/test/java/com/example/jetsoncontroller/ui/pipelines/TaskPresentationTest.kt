package com.example.jetsoncontroller.ui.pipelines

import com.example.jetsoncontroller.model.PipelineState
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
    @Test fun observationReconcilesOnlyTheRequestedTask() {
        val requests = mapOf("a" to "start", "b" to "stop", "c" to "unknown")
        assertEquals(requests, reconcileTaskRequests(requests, mapOf("a" to PipelineState.STARTING, "b" to PipelineState.RUNNING)))
        assertEquals(mapOf("c" to "unknown"), reconcileTaskRequests(requests,
            mapOf("a" to PipelineState.RUNNING, "b" to PipelineState.STOPPED)))
    }
}
