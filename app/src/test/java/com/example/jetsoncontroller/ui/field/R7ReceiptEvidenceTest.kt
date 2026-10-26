package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.components.geo.GeoReceiptStep
import com.example.jetsoncontroller.ui.components.geo.receiptTone
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.*
import org.junit.Test

class R7ReceiptEvidenceTest {
    @Test fun `result receipt uses the same successful stop and abnormal warning policy`() {
        val run = resultTestRun()
        assertEquals(Tone.SUCCESS, pipelineRunReceiptCells(run.copy(state = "STOPPED", exitCode = 143), Tone.UNKNOWN)[0].tone)
        assertEquals(Tone.WARNING, pipelineRunReceiptCells(run.copy(state = "FAILED", exitCode = null), Tone.UNKNOWN)[0].tone)
        assertEquals(Tone.WARNING, pipelineRunReceiptCells(run.copy(state = "COMPLETED", exitCode = 1), Tone.UNKNOWN)[0].tone)
    }

    @Test fun `finished abnormal collection remains a warning in the recent receipt`() {
        val failed = entry().copy(state = "FAILED", exitCode = 1)
        val item = historyReceipt(failed, emptyList()).collection
        assertTrue(item.done)
        assertEquals("비정상 종료", item.word)
        assertEquals(Tone.WARNING, receiptTone(GeoReceiptStep.COLLECTION, item))
    }

    @Test fun `normal and user stopped collections both use success`() {
        listOf(entry().copy(state = "COMPLETED", exitCode = 0),
            entry().copy(state = "STOPPED", exitCode = 143)).forEach { run ->
            val item = historyReceipt(run, emptyList()).collection
            assertTrue(item.done)
            assertEquals(Tone.SUCCESS, receiptTone(GeoReceiptStep.COLLECTION, item))
        }
    }

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
