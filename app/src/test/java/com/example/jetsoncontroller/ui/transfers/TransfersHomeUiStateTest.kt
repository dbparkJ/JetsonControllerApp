package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.SurveyContextSnapshot
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadContext
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.upload.TransferSample
import com.example.jetsoncontroller.ui.upload.UploadUiState
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TRANSFER_TEST_DEVICE_ID = "7f3a9c2e-1b4d-4e8a-9c0f-00000000abcd"
private const val MIB = 1_048_576L

class TransfersHomeUiStateTest {

    @Test
    fun `explanation says uploads need the same network when the phone is on LAN`() {
        val state = buildTransfersHomeUiState(input(transport = TransportType.LAN))

        assertEquals("장치가 휴대폰과 같은 네트워크에 있을 때 서버로 보낼 수 있습니다.", state.explanation)
        assertEquals(StatusTone.INFO, state.explanationTone)
    }

    @Test
    fun `explanation carries the transport policy reason over Wi-Fi Direct`() {
        val state = buildTransfersHomeUiState(input(transport = TransportType.WIFI_DIRECT))

        assertEquals(
            "Wi-Fi Direct 연결에서는 서버 업로드를 시작할 수 없습니다. LAN으로 연결해 주세요.",
            state.explanation
        )
    }

    @Test
    fun `speed and remaining time are omitted until progress can be measured`() {
        val job = job("job-1", UploadJobState.UPLOADING, bytesTotal = 1_000L, bytesTransferred = 250L)

        val active = buildTransfersHomeUiState(input(queue = listOf(job)))
            .active ?: error("active transfer expected")

        assertNull(active.speedLabel)
        assertNull(active.remainingLabel)
        assertEquals(0.25f, active.progress ?: -1f, 0.0001f)
        assertEquals("250 B / 1000 B", active.sentLabel)
        assertEquals("보내는 중", active.stateLabel)
    }

    @Test
    fun `speed and remaining time come from observed progress`() {
        val job = job(
            "job-1",
            UploadJobState.UPLOADING,
            bytesTotal = 637_534_208L,
            bytesTransferred = 8L * MIB
        )
        val samples = listOf(
            TransferSample("job-1", bytesTransferred = 0L, observedAtMillis = 0L),
            TransferSample("job-1", bytesTransferred = 8L * MIB, observedAtMillis = 8_000L)
        )

        val active = buildTransfersHomeUiState(input(queue = listOf(job), samples = samples))
            .active ?: error("active transfer expected")

        assertEquals("1.0 MB/s", active.speedLabel)
        assertEquals("약 10분", active.remainingLabel)
    }

    @Test
    fun `samples of another job never change the active speed`() {
        val job = job("job-1", UploadJobState.UPLOADING, bytesTotal = 10L * MIB, bytesTransferred = 1L * MIB)
        val samples = listOf(
            TransferSample("job-old", 0L, 0L),
            TransferSample("job-old", 8L * MIB, 8_000L)
        )

        val active = buildTransfersHomeUiState(input(queue = listOf(job), samples = samples))
            .active ?: error("active transfer expected")

        assertNull(active.speedLabel)
        assertNull(active.remainingLabel)
    }

    @Test
    fun `a failed transfer shows a sanitized cause outside developer mode`() {
        val raw = "java.net.SocketTimeoutException: failed to connect to /192.168.0.5:8080"
        val job = job("job-2", UploadJobState.FAILED, errorMessage = raw)

        val failed = buildTransfersHomeUiState(input(queue = listOf(job))).failed.single()

        assertFalse(failed.cause.contains("192.168"))
        assertFalse(failed.cause.contains("Exception"))
        assertEquals(raw, buildTransfersHomeUiState(input(queue = listOf(job), developerModeEnabled = true))
            .failed.single().cause)
    }

    @Test
    fun `a failed transfer without a message uses the neutral cause text`() {
        val job = job("job-3", UploadJobState.FAILED, errorMessage = null)

        val failed = buildTransfersHomeUiState(input(queue = listOf(job))).failed.single()

        assertEquals("전송을 끝내지 못했습니다. 같은 네트워크인지 확인하고 다시 시도하세요.", failed.cause)
    }

    @Test
    fun `a confirmed upload shows its receipt time, size and file count`() {
        val verification = UploadVerification(
            jobId = "job-4",
            state = "VERIFIED",
            matched = true,
            deletionAllowed = false,
            bytesTotal = 2L * 1024L * 1024L * 1024L,
            filesTotal = 3410,
            verifiedAt = "2026-10-14T08:02:00Z"
        )
        val job = job("job-4", UploadJobState.COMPLETED, verification = verification)

        val row = buildTransfersHomeUiState(input(queue = listOf(job))).verified.single()

        assertEquals(StatusTone.SUCCESS, row.tone)
        assertEquals("10월 14일 08:02 수신 · 2.0 GB · 파일 3,410개", row.detail)
    }

    @Test
    fun `a mismatched verification is a warning and not an unknown failure`() {
        val verification = UploadVerification(
            jobId = "job-5",
            state = "MISMATCH",
            matched = false,
            deletionAllowed = false
        )
        val job = job("job-5", UploadJobState.COMPLETED, verification = verification)

        val row = buildTransfersHomeUiState(input(queue = listOf(job))).verified.single()

        assertEquals(StatusTone.WARNING, row.tone)
    }

    @Test
    fun `a finished upload waiting for server confirmation is pending`() {
        val job = job("job-6", UploadJobState.COMPLETED, verification = null)

        val row = buildTransfersHomeUiState(input(queue = listOf(job))).verified.single()

        assertEquals(StatusTone.PENDING, row.tone)
    }

    @Test
    fun `queued jobs are listed and the active job is not repeated`() {
        val active = job("job-1", UploadJobState.UPLOADING, bytesTotal = 100L, bytesTransferred = 10L)
        val waiting = job("job-2", UploadJobState.QUEUED)

        val state = buildTransfersHomeUiState(input(queue = listOf(active, waiting), current = active))

        assertEquals("job-1", state.active?.jobId)
        assertEquals(listOf("job-2"), state.queued.map { it.jobId })
        assertEquals("보내기 순서를 기다리는 중입니다.", state.queued.single().detail)
    }

    @Test
    fun `the transfer title uses the survey label of the run it belongs to`() {
        val run = taskRun("run-3")
        val job = job("job-7", UploadJobState.UPLOADING, bytesTotal = 100L, bytesTransferred = 10L, runId = "run-3")

        val active = buildTransfersHomeUiState(input(queue = listOf(job), runs = listOf(run)))
            .active ?: error("active transfer expected")

        assertEquals("한빛시 도로대장 · 3구간", active.title)
        assertEquals("MMS-4DE0 → 수신 서버", active.subtitle)
    }

    @Test
    fun `the empty text appears only when there is nothing to list`() {
        assertEquals(
            "보낸 파일이 없습니다. 보낼 파일을 고르면 여기에서 진행을 확인할 수 있습니다.",
            buildTransfersHomeUiState(input()).emptyText
        )
        assertNull(buildTransfersHomeUiState(input(queue = listOf(job("job-8", UploadJobState.QUEUED)))).emptyText)
    }

    private fun input(
        transport: TransportType? = TransportType.LAN,
        queue: List<UploadJob> = emptyList(),
        current: UploadJob? = null,
        samples: List<TransferSample> = emptyList(),
        runs: List<TaskRun> = emptyList(),
        developerModeEnabled: Boolean = false
    ) = TransfersHomeInput(
        deviceName = "MMS-4DE0",
        transport = transport,
        upload = UploadUiState(
            deviceId = TRANSFER_TEST_DEVICE_ID,
            controlAvailable = transport != null,
            queue = queue,
            currentJob = current ?: queue.firstOrNull { it.state == UploadJobState.UPLOADING },
            transferSamples = samples
        ),
        runs = runs,
        developerModeEnabled = developerModeEnabled,
        zone = ZoneOffset.UTC
    )

    private fun job(
        id: String,
        state: UploadJobState,
        bytesTotal: Long? = 1_000L,
        bytesTransferred: Long? = 0L,
        errorMessage: String? = null,
        verification: UploadVerification? = null,
        runId: String? = null
    ) = UploadJob(
        id = id,
        rootId = "data",
        relativePath = "runs/$id",
        targetId = "target",
        state = state,
        bytesTotal = bytesTotal,
        bytesTransferred = bytesTransferred,
        filesTotal = null,
        filesTransferred = null,
        currentFile = null,
        errorMessage = errorMessage,
        verification = verification,
        context = runId?.let {
            UploadContext(
                surveyProjectId = "project-1",
                surveySectionId = "section-3",
                runId = it,
                deviceId = TRANSFER_TEST_DEVICE_ID,
                pipelineId = "pipe",
                sourceRevision = "source",
                configSha256 = "config",
                outputId = "out",
                createdAt = "created"
            )
        }
    )

    private fun taskRun(id: String) = TaskRun(
        id = id,
        pipelineId = "pipe",
        label = "도로 수집",
        logId = "log-$id",
        startedAt = "2026-10-14T06:00:00Z",
        finishedAt = "2026-10-14T06:42:00Z",
        state = "COMPLETED",
        exitCode = 0,
        runId = id,
        deviceId = TRANSFER_TEST_DEVICE_ID,
        contextSnapshot = SurveyContextSnapshot(
            deviceId = TRANSFER_TEST_DEVICE_ID,
            surveyProjectId = "project-1",
            surveyProjectLabel = "한빛시 도로대장",
            surveyProjectRevision = 1,
            surveySectionId = "section-3",
            surveySectionLabel = "3구간",
            surveySectionRevision = 1,
            capturedAt = "captured"
        )
    )
}
