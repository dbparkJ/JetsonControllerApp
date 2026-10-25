package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.model.UploadContext
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.components.geo.GeoReceiptItem
import com.example.jetsoncontroller.ui.components.geo.GeoReceiptState
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.field.resultTestRun
import com.example.jetsoncontroller.ui.field.resultTestJob
import org.junit.Assert.*
import org.junit.Test

class UploadReceiptPresentationTest {
    @Test fun `source identity and completed upload do not prove collection storage or server receipt`() {
        val state = uploadReceiptState(job(), null)
        assertFalse(state.collection.done)
        assertFalse(state.storage.done)
        assertFalse(state.server.done)
        assertFalse(state.report.done)
        assertEquals("미확인", state.collection.word)
        assertEquals("보고 전", state.report.word)
    }

    @Test fun `only a receipt for this completed transfer marks server reception done`() {
        assertTrue(uploadReceiptState(job(), receipt()).server.done)
        assertFalse(uploadReceiptState(job(), receipt().copy(jobId = "other")).server.done)
        assertFalse(uploadReceiptState(job(), receipt().copy(targetId = "other")).server.done)
        assertFalse(uploadReceiptState(job(), receipt().copy(remoteSessionId = "other")).server.done)
        assertFalse(uploadReceiptState(job(), receipt().copy(sourceName = "other")).server.done)
        assertFalse(uploadReceiptState(job(), receipt().copy(matched = false)).server.done)
        assertFalse(uploadReceiptState(job().copy(state = UploadJobState.UPLOADING), receipt()).server.done)
    }

    @Test fun `source evidence and report evidence remain independent of status color`() {
        val source = GeoReceiptState(
            collection = GeoReceiptItem("종료", Tone.SUCCESS, "종료 근거", done = true),
            storage = GeoReceiptItem("저장 확인", Tone.SUCCESS, "저장 근거", done = false))
        val pending = uploadReceiptState(job(), receipt(), source, reportPending = true)
        assertEquals(source.collection, pending.collection)
        assertEquals(source.storage, pending.storage)
        assertFalse(pending.storage.done)
        assertTrue(pending.server.done)
        assertFalse(pending.report.done)
        assertEquals("보고 대기", pending.report.word)
        val failed = uploadReceiptState(job(), receipt(), reportFailed = true, reportMessage = "보고 실패 근거")
        assertFalse(failed.report.done)
        assertEquals("보고 실패 근거", failed.report.meta)
        assertTrue(uploadReceiptState(job(), receipt(), reported = true).report.done)
    }

    @Test fun `source stages require matching device run pipeline context revision and output`() {
        val run = resultTestRun()
        val job = resultTestJob()
        val source = requireNotNull(uploadSourceReceipt(run, job, run.deviceId))
        assertTrue(source.collection.done)
        assertTrue(source.storage.done)
        assertNotNull(source.collection.meta)
        assertNotNull(source.storage.meta)
        assertNull(uploadSourceReceipt(null, job, run.deviceId))
        assertNull(uploadSourceReceipt(run, null, run.deviceId))
        assertNull(uploadSourceReceipt(run, job, "other-device"))
        val context = requireNotNull(job.context)
        listOf(job.copy(context = null), job.copy(rootId = "other-root"),
            job.copy(relativePath = "other-path"),
            job.copy(context = context.copy(runId = "other-run")),
            job.copy(context = context.copy(deviceId = "other-device")),
            job.copy(context = context.copy(pipelineId = "other-pipeline")),
            job.copy(context = context.copy(surveyProjectId = "other-project")),
            job.copy(context = context.copy(surveySectionId = "other-section")),
            job.copy(context = context.copy(sourceRevision = "other-revision")),
            job.copy(context = context.copy(configSha256 = "other-config")),
            job.copy(context = context.copy(outputId = "other-output"))).forEach {
                assertNull(uploadSourceReceipt(run, it, run.deviceId))
            }
    }

    @Test fun `matching upload does not prove missing termination or final manifest`() {
        val run = resultTestRun()
        val job = resultTestJob()
        val unknown = run.copy(active = true, finishedAt = null,
            output = run.output.copy(manifestState = "PENDING"))
        val source = requireNotNull(uploadSourceReceipt(unknown, job, run.deviceId))
        assertFalse(source.collection.done)
        assertFalse(source.storage.done)
        val wrongManifest = run.copy(output = run.output.copy(
            manifest = requireNotNull(run.output.manifest).copy(runId = "other-run")))
        assertFalse(requireNotNull(uploadSourceReceipt(wrongManifest, job, run.deviceId)).storage.done)
    }

    private fun job() = UploadJob("job", "data", "capture", "server", UploadJobState.COMPLETED,
        120L, 120L, 2, 2, null, null, sourceName = "capture", remoteSessionId = "session",
        context = UploadContext(surveyProjectId = "project", surveySectionId = "section", runId = "run",
            deviceId = "device", pipelineId = "pipeline", sourceRevision = "revision", configSha256 = "hash",
            outputId = "output", createdAt = "2026-10-26T00:00:00Z"))

    private fun receipt() = UploadVerification("job", "server", "session", "capture", "MATCHED", true,
        true, bytesTotal = 120L, filesTotal = 2)
}
