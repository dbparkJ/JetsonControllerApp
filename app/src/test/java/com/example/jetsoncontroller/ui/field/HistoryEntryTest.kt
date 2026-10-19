package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.TaskRun
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HistoryEntryTest {
    @Test fun `legacy entry cannot acquire canonical storage or server confirmation`() {
        val legacy = TaskRun("capture/legacy.log", "capture", "데이터 수집", "legacy.log", "2026-09-16T07:36:00Z")
        assertNull(canonicalHistoryRunId(legacy))
        val receipt = historyReceipt(legacy, listOf(resultTestJob()))
        assertEquals("미확인", receipt.storage.word)
        assertFalse(receipt.server.done)
    }

    @Test fun `manifest canonical id requires context and cannot replace a different run id`() {
        val run = resultTestRun()
        val entry = TaskRun("capture/run.log", run.pipelineId, "데이터 수집", run.logId, run.startedAt,
            contextSnapshot = run.contextSnapshot, output = run.output)
        assertEquals("run", canonicalHistoryRunId(entry))
        assertNull(canonicalHistoryRunId(entry.copy(runId = "other")))
        assertNull(canonicalHistoryRunId(entry.copy(contextSnapshot = null)))
        assertNull(canonicalHistoryRunId(entry.copy(output = run.output.copy(manifestState = "PENDING"))))
    }

    @Test fun `history receipt validates source and receipt identity`() {
        val run = resultTestRun()
        val entry = TaskRun("capture/run.log", run.pipelineId, "데이터 수집", run.logId, run.startedAt,
            finishedAt = run.finishedAt, state = run.state, runId = run.runId, deviceId = run.deviceId,
            contextSnapshot = run.contextSnapshot, output = run.output)
        val job = resultTestJob()
        assertTrue(historyReceipt(entry, listOf(job)).server.done)
        assertFalse(historyReceipt(entry, listOf(job.copy(relativePath = "other"))).server.done)
        assertFalse(historyReceipt(entry, listOf(job.copy(verification = job.verification!!.copy(jobId = "other")))).server.done)
    }

    @Test fun `history day grouping uses Seoul day boundary and distinguishes years`() {
        val now = Instant.parse("2026-10-19T15:00:00Z")
        assertEquals("오늘", historyDayLabel("2026-10-19T15:00:00Z", now))
        assertEquals("어제", historyDayLabel("2026-10-19T14:59:59Z", now))
        assertEquals("9월 16일", historyDayLabel("2026-09-16T07:36:00Z", now))
        assertEquals("2025년 9월 16일", historyDayLabel("2025-09-16T07:36:00Z", now))
        assertEquals("날짜 미확인", historyDayLabel("unknown", now))
    }

    @Test fun `late saved log cannot replace another entry or reopen after dismissal`() {
        assertTrue(routeResponseIsCurrent(1, 1, 2, 2, "capture/old.log", "capture/old.log"))
        assertFalse(routeResponseIsCurrent(1, 1, 2, 3, "capture/new.log", "capture/old.log"))
        assertFalse(routeResponseIsCurrent(1, 1, 2, 3, null, "capture/old.log"))
        assertFalse(routeResponseIsCurrent(1, 2, 2, 2, "capture/old.log", "capture/old.log"))
    }
}
