package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.PipelineOutputManifest
import com.example.jetsoncontroller.model.PipelineRunOutput
import com.example.jetsoncontroller.model.SurveyContextSnapshot
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadContext
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Test

private const val RECEIPT_DEVICE_ID = "7f3a9c2e-1b4d-4e8a-9c0f-00000000abcd"

class RunReceiptCellsTest {

    @Test
    fun `result receipt without a run is unknown in every cell`() {
        val cells = pipelineRunReceiptCells(run = null, serverTone = Tone.UNKNOWN)

        assertEquals(listOf("수집 종료", "장치 저장", "서버 수신"), cells.map { it.title })
        assertEquals(listOf("미확인", "미확인", "미확인"), cells.map { it.value })
        assertEquals(listOf(Tone.UNKNOWN, Tone.UNKNOWN, Tone.UNKNOWN), cells.map { it.tone })
    }

    @Test
    fun `server cell words follow the server tone`() {
        assertEquals("수신 확인", pipelineRunReceiptCells(null, Tone.SUCCESS)[2].value)
        assertEquals("전송 중", pipelineRunReceiptCells(null, Tone.PENDING)[2].value)
        assertEquals("다시 확인", pipelineRunReceiptCells(null, Tone.WARNING)[2].value)
        assertEquals(Tone.WARNING, pipelineRunReceiptCells(null, Tone.WARNING)[2].tone)
    }

    @Test
    fun `a finished task with a final manifest shows stored and no upload is unknown, not failed`() {
        val cells = taskRunReceiptCells(
            taskRun("run-1", bytes = 1_024L),
            uploadJobs = emptyList()
        )

        assertEquals(Tone.SUCCESS, cells[0].tone)
        assertEquals("종료", cells[0].value)
        assertEquals(Tone.SUCCESS, cells[1].tone)
        assertEquals("저장 확인", cells[1].value)
        assertEquals(Tone.UNKNOWN, cells[2].tone)
        assertEquals("미확인", cells[2].value)
    }

    @Test
    fun `server confirmation counts only for the exact run id on the same device`() {
        val confirmed = upload("job-a", runId = "run-1", state = UploadJobState.COMPLETED, matched = true)
        val otherRun = upload("job-b", runId = "run-2", state = UploadJobState.COMPLETED, matched = true)

        assertEquals(
            Tone.SUCCESS,
            uploadReceiptTone("run-1", RECEIPT_DEVICE_ID, listOf(confirmed, otherRun))
        )
        assertEquals(
            Tone.UNKNOWN,
            uploadReceiptTone("run-3", RECEIPT_DEVICE_ID, listOf(confirmed, otherRun))
        )
        assertEquals(
            Tone.UNKNOWN,
            uploadReceiptTone("run-1", "another-device", listOf(confirmed))
        )
    }

    @Test
    fun `a transfer in progress for the run is pending`() {
        val uploading = upload("job-c", runId = "run-1", state = UploadJobState.UPLOADING, matched = null)

        assertEquals(Tone.PENDING, uploadReceiptTone("run-1", RECEIPT_DEVICE_ID, listOf(uploading)))
        val cells = taskRunReceiptCells(taskRun("run-1"), listOf(uploading))
        assertEquals("전송 중", cells[2].value)
    }

    @Test
    fun `a mismatched verification for the run is a warning, never an unknown failure`() {
        val mismatched = upload("job-d", runId = "run-1", state = UploadJobState.COMPLETED, matched = false)

        assertEquals(Tone.WARNING, uploadReceiptTone("run-1", RECEIPT_DEVICE_ID, listOf(mismatched)))
    }

    @Test
    fun `a run without a run id can never be confirmed by an upload`() {
        val confirmed = upload("job-e", runId = "run-1", state = UploadJobState.COMPLETED, matched = true)

        assertEquals(Tone.UNKNOWN, uploadReceiptTone(null, RECEIPT_DEVICE_ID, listOf(confirmed)))
    }

    @Test
    fun `task receipt for an interrupted run reports it as stopped and not as an error`() {
        val stopped = taskRun("run-2", state = "STOPPED", finishedAt = null)

        val cells = taskRunReceiptCells(stopped, emptyList())

        assertEquals(Tone.INFO, cells[0].tone)
        assertEquals("중지됨", cells[0].value)
    }

    private fun taskRun(
        id: String,
        state: String = "COMPLETED",
        finishedAt: String? = "2026-10-14T06:42:00Z",
        bytes: Long? = null
    ): TaskRun {
        val manifest = bytes?.let {
            PipelineOutputManifest(runId = id, generatedAt = "2026-10-14T06:42:00Z", fileCount = 3, bytesTotal = it)
        }
        return TaskRun(
            id = id,
            pipelineId = "pipe",
            label = "도로 수집",
            logId = "log-$id",
            startedAt = "2026-10-14T06:00:00Z",
            finishedAt = finishedAt,
            state = state,
            exitCode = if (state == "COMPLETED") 0 else null,
            runId = id,
            deviceId = RECEIPT_DEVICE_ID,
            contextSnapshot = SurveyContextSnapshot(
                deviceId = RECEIPT_DEVICE_ID,
                surveyProjectId = "project-1",
                surveyProjectLabel = "한빛시 도로대장",
                surveyProjectRevision = 1,
                surveySectionId = "section-3",
                surveySectionLabel = "3구간",
                surveySectionRevision = 1,
                capturedAt = "captured"
            ),
            output = PipelineRunOutput(
                rootId = "data",
                path = "runs/$id",
                outputId = "out-$id",
                manifestState = if (manifest != null) "FINAL" else "PENDING",
                manifest = manifest
            )
        )
    }

    private fun upload(
        id: String,
        runId: String,
        state: UploadJobState,
        matched: Boolean?
    ): UploadJob = UploadJob(
        id = id,
        rootId = "data",
        relativePath = "runs/$runId",
        targetId = "target",
        state = state,
        bytesTotal = 100L,
        bytesTransferred = 100L,
        filesTotal = 3,
        filesTransferred = 3,
        currentFile = null,
        errorMessage = null,
        verification = matched?.let {
            UploadVerification(jobId = id, state = if (it) "VERIFIED" else "MISMATCH", matched = it, deletionAllowed = false)
        },
        context = UploadContext(
            surveyProjectId = "project-1",
            surveySectionId = "section-3",
            runId = runId,
            deviceId = RECEIPT_DEVICE_ID,
            pipelineId = "pipe",
            sourceRevision = "source",
            configSha256 = "config",
            outputId = "out-$runId",
            createdAt = "created"
        )
    )
}
