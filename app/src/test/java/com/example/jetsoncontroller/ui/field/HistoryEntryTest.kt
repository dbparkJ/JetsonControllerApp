package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.TaskRun
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HistoryEntryTest {
    @Test fun `history titles prefer folder names and daily meta never repeats a run id`() {
        val result = resultTestRun()
        val entry = TaskRun("capture/run.log", result.pipelineId, "데이터 수집", result.logId, result.startedAt,
            finishedAt = result.finishedAt, runId = result.runId, contextSnapshot = result.contextSnapshot,
            output = result.output)
        assertEquals("20261019_1636", historyRecordTitle(entry))
        val identifiedFolder = entry.copy(output = result.output.copy(path = "recordings/${result.runId}/"))
        assertEquals("북측 보조 구간", historyRecordTitle(identifiedFolder))
        assertFalse(historyRecordMeta(identifiedFolder).contains(result.runId))
        assertFalse(historyRecordMeta(entry).contains("20261019_1636"))
        assertTrue(historyRecordMeta(entry).contains("12분"))
        assertTrue(historyRecordMeta(entry).contains("1.2"))
        assertNull(historyFolderLabel("recordings/${result.runId}", result.runId))
        assertNull(historyFolderLabel("/", result.runId))
        assertEquals("데이터 수집", historyRecordTitle(entry.copy(output = null, contextSnapshot = null)))
    }

    @Test fun `folder names ignore surrounding whitespace and trailing separators`() {
        val run = resultTestRun()
        val entry = TaskRun("capture/run.log", run.pipelineId, "데이터 수집", run.logId, run.startedAt,
            runId = run.runId, contextSnapshot = run.contextSnapshot, output = run.output)
        listOf(" recordings/20261019_1636/// ", "recordings\\20261019_1636\\").forEach { path ->
            assertEquals("20261019_1636", historyRecordTitle(entry.copy(output = run.output.copy(path = path))))
        }
        assertNull(historyFolderLabel("recordings/ ${run.runId} /", run.runId))
    }

    @Test fun `daily titles never fall back to a run id in a section or task label`() {
        val run = resultTestRun()
        val entry = TaskRun("capture/run.log", run.pipelineId, run.runId, run.logId, run.startedAt,
            runId = run.runId, contextSnapshot = run.contextSnapshot.copy(surveySectionLabel = " ${run.runId} "),
            output = run.output.copy(path = "recordings/${run.runId}"))
        assertEquals("수집 기록", historyRecordTitle(entry))
        assertFalse(historyRecordMeta(entry).contains(run.runId))
        assertEquals("데이터 수집", historyRecordTitle(entry.copy(label = "데이터 수집")))
        assertEquals("20261019_1636", historyRecordTitle(entry.copy(output = run.output)))
    }

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
