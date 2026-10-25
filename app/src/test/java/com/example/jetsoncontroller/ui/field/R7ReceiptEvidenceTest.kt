package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.TaskRun
import org.junit.Assert.*
import org.junit.Test

class R7ReceiptEvidenceTest {
    private fun entry(): TaskRun {
        val run = resultTestRun()
        return TaskRun("record", run.pipelineId, "수집", run.logId, run.startedAt,
            finishedAt = run.finishedAt, state = run.state, runId = run.runId, deviceId = run.deviceId,
            contextSnapshot = run.contextSnapshot, output = run.output)
    }

    @Test fun `recent receipt lights only evidence backed stages in its four columns`() {
        val receipt = historyReceipt(entry(), emptyList())
        assertEquals(4, receipt.rows().size)
        assertTrue(receipt.collection.done)
        assertTrue(receipt.storage.done)
        assertFalse(receipt.server.done)
        assertFalse(receipt.report.done)
    }

    @Test fun `a final marker for another run never lights storage`() {
        val entry = entry()
        val output = requireNotNull(entry.output)
        val wrong = entry.copy(output = output.copy(manifest = requireNotNull(output.manifest).copy(runId = "other")))
        assertFalse(historyReceipt(wrong, listOf(resultTestJob())).storage.done)
        val pending = entry.copy(output = output.copy(manifestState = "PENDING"))
        assertFalse(historyReceipt(pending, emptyList()).storage.done)
    }

    @Test fun `unlinked report is not evidence of a completed report and next action stays available`() {
        val chain = resultChain(resultTestRun(), listOf(resultTestJob()), true, "", "같은 네트워크", true,
            ticketLinked = false, reportEnabled = false, reportPending = false, reported = false)
        assertEquals("해당 없음", chain.receipt.report.word)
        assertFalse(chain.receipt.report.done)
        assertEquals(ResultAction.NEXT, chain.action)
        assertTrue(chain.enabled)
    }
}
