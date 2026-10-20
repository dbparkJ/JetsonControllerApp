package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.field.resultTestRun
import org.junit.Assert.*
import org.junit.Test

class WorkHistoryNavigationTest {
    private val result = resultTestRun()
    private val entry = TaskRun("capture/legacy log.log", result.pipelineId, "데이터 수집", result.logId,
        result.startedAt, runId = result.runId, deviceId = result.deviceId)

    @Test fun `a legacy id with no canonical manifest opens history facts`() {
        assertEquals("history_result/capture%2Flegacy%20log.log?deviceId=device",
            workHistoryResultRoute(entry, "device"))
        assertTrue(workHistoryResultRoute(entry.copy(runId = null), "device").startsWith("history_result/"))
    }

    @Test fun `only a final manifest with matching context opens the run result`() {
        val canonical = entry.copy(contextSnapshot = result.contextSnapshot, output = result.output)
        assertTrue(workHistoryResultRoute(canonical, "device").startsWith("run_result/run?"))
        assertTrue(workHistoryResultRoute(canonical, "device").contains("historyId=capture%2Flegacy%20log.log"))
        assertTrue(workHistoryResultRoute(canonical.copy(contextSnapshot = null), "device").startsWith("history_result/"))
        assertTrue(workHistoryResultRoute(canonical.copy(runId = "different"), "device").startsWith("history_result/"))
        assertTrue(workHistoryResultRoute(canonical.copy(output = result.output.copy(manifestState = "PENDING")),
            "device").startsWith("history_result/"))
    }
}
