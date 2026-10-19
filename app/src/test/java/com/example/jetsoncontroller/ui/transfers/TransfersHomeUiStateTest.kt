package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.data.server.ServerDeviceEventDelivery
import com.example.jetsoncontroller.data.server.ServerDeviceEventIntegration
import com.example.jetsoncontroller.data.server.ServerDeviceEventIntegrationState
import com.example.jetsoncontroller.data.server.ServerDeviceEventStatus
import com.example.jetsoncontroller.data.server.ServerDeviceEventWindow
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.PipelineOutputManifest
import com.example.jetsoncontroller.model.PipelineRunOutput
import com.google.gson.Gson
import com.example.jetsoncontroller.model.SurveyContextSnapshot
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadContext
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.upload.TransferSample
import com.example.jetsoncontroller.ui.upload.UploadUiState
import java.time.Instant
import java.time.ZoneId
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
    fun `expired GEO and Work integration shows the exact error message`() {
        val notice = deviceEventNoticeFor(status("EXPIRED", dead = 4), NOW, SEOUL)

        assertEquals(StatusTone.ERROR, notice?.tone)
        assertEquals(
            "GEO& Work 연동이 만료되었습니다. 관리자에게 연동 연장을 요청하세요.",
            notice?.text
        )
    }

    @Test
    fun `GEO and Work integration error takes priority over queue counts`() {
        val notice = deviceEventNoticeFor(status("ERROR", dead = 4, pending = 3), NOW, SEOUL)

        assertEquals(StatusTone.ERROR, notice?.tone)
        assertEquals(
            "GEO& Work 알림을 보내지 못하고 있습니다. 수신 서버 관리자에게 알려 주세요.",
            notice?.text
        )
    }

    @Test
    fun `dead delivery count takes priority over pending and uses grouped numbers`() {
        val notice = deviceEventNoticeFor(status("ACTIVE", dead = 1_234, pending = 7), NOW, SEOUL)

        assertEquals(StatusTone.WARNING, notice?.tone)
        assertEquals(
            "GEO& Work 알림 1,234건을 보내지 못했습니다. 수신 서버 관리자에게 알려 주세요.",
            notice?.text
        )
    }

    @Test
    fun `pending delivery count uses grouped numbers`() {
        val notice = deviceEventNoticeFor(status("ACTIVE", pending = 2_345), NOW, SEOUL)

        assertEquals(StatusTone.PENDING, notice?.tone)
        assertEquals("GEO& Work 알림 2,345건을 보내는 중입니다.", notice?.text)
    }

    @Test
    fun `last delivered status uses the selected zone and app date format`() {
        val notice = deviceEventNoticeFor(
            status("ACTIVE", lastDelivered = ServerDeviceEventDelivery("과열 경고", "2026-10-14T23:02:00Z")),
            NOW,
            SEOUL
        )

        assertEquals(StatusTone.SUCCESS, notice?.tone)
        assertEquals("GEO& Work에 알림을 보냈습니다 · 10월 15일 08:02 과열 경고", notice?.text)
    }

    @Test
    fun `active integration without failures or a delivery is informational`() {
        val notice = deviceEventNoticeFor(status("ACTIVE"), NOW, SEOUL)

        assertEquals(StatusTone.INFO, notice?.tone)
        assertEquals("GEO& Work 알림이 켜져 있습니다.", notice?.text)
    }

    @Test
    fun `unconfigured unknown and missing GEO and Work status hide the row`() {
        assertNull(deviceEventNoticeFor(status("NOT_CONFIGURED"), NOW, SEOUL))
        assertNull(deviceEventNoticeFor(status("A_FUTURE_STATE"), NOW, SEOUL))
        assertNull(deviceEventNoticeFor(null, NOW, SEOUL))
    }

    @Test
    fun `status DTO parses the documented JSON and maps future states to unknown`() {
        val json = """{
            "deviceId":"$TRANSFER_TEST_DEVICE_ID",
            "integration":{"state":"FUTURE_STATE","detail":null},
            "window":{"hours":24,"pending":2,"sent":3,"dedup":1,"dead":0},
            "lastDelivered":{"status":"과열 경고","at":"2026-10-14T23:02:00Z"},
            "lastFailure":null,
            "lastSeenAt":null
        }"""

        val parsed = Gson().fromJson(json, ServerDeviceEventStatus::class.java)

        assertEquals(TRANSFER_TEST_DEVICE_ID, parsed.deviceId)
        assertEquals(2, parsed.window.pending)
        assertEquals(ServerDeviceEventIntegrationState.UNKNOWN, parsed.integration.normalizedState)
        assertEquals("과열 경고", parsed.lastDelivered?.status)
    }

    private fun status(
        state: String,
        pending: Int = 0,
        dead: Int = 0,
        lastDelivered: ServerDeviceEventDelivery? = null
    ) = ServerDeviceEventStatus(
        deviceId = TRANSFER_TEST_DEVICE_ID,
        integration = ServerDeviceEventIntegration(state),
        window = ServerDeviceEventWindow(hours = 24, pending = pending, sent = 0, dedup = 0, dead = dead),
        lastDelivered = lastDelivered,
        lastFailure = null,
        lastSeenAt = null
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-18T12:00:00Z")
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }

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
            "장치에 직접 연결(Wi-Fi Direct)한 상태에서는 서버로 보낼 수 없습니다. 같은 네트워크(LAN)로 연결해 주세요.",
            state.explanation
        )
    }

    @Test
    fun `speed and remaining time are omitted until progress can be measured`() {
        val job = job("job-1", UploadJobState.UPLOADING, bytesTotal = 1_000L, bytesTransferred = 250L)

        val active = buildTransfersHomeUiState(input(queue = listOf(job)))
            .active.singleOrNull() ?: error("active transfer expected")

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
            .active.singleOrNull() ?: error("active transfer expected")

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
            .active.singleOrNull() ?: error("active transfer expected")

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
        assertEquals("10월 14일 08:02 수신 확인 · 2.0 GB · 파일 3,410개", row.detail)
    }

    @Test
    fun `a mismatched verification needs attention and is not shown as received`() {
        val verification = UploadVerification(
            jobId = "job-5",
            state = "MISMATCH",
            matched = false,
            deletionAllowed = false
        )
        val job = job("job-5", UploadJobState.COMPLETED, verification = verification)

        val state = buildTransfersHomeUiState(input(queue = listOf(job)))

        assertEquals("내용 불일치", state.failed.single().stateLabel)
        assertTrue(state.verified.isEmpty())
        assertTrue(state.awaitingVerification.isEmpty())
    }

    @Test
    fun `a finished upload without a receipt is waiting for server verification`() {
        val job = job("job-6", UploadJobState.COMPLETED, verification = null)

        val state = buildTransfersHomeUiState(input(queue = listOf(job)))
        val row = state.awaitingVerification.single()

        assertEquals(StatusTone.PENDING, row.tone)
        assertEquals("수신 확인 중", row.detail)
        assertTrue(state.verified.isEmpty())
    }

    @Test
    fun `a matched receipt for another job does not verify this transfer`() {
        val verification = UploadVerification(
            jobId = "different-job",
            state = "MATCHED",
            matched = true,
            deletionAllowed = true
        )
        val job = job("job-7", UploadJobState.COMPLETED, verification = verification)

        val state = buildTransfersHomeUiState(input(queue = listOf(job)))

        assertTrue(state.verified.isEmpty())
        assertEquals("job-7", state.awaitingVerification.single().jobId)
    }

    @Test
    fun `a matched receipt from a different source session is not accepted`() {
        val verification = UploadVerification(
            jobId = "job-8",
            targetId = "target",
            remoteSessionId = "another-session",
            sourceName = "job-8",
            state = "MATCHED",
            matched = true,
            deletionAllowed = true
        )
        val job = job("job-8", UploadJobState.COMPLETED, verification = verification)

        val state = buildTransfersHomeUiState(input(queue = listOf(job)))

        assertTrue(state.verified.isEmpty())
        assertEquals("job-8", state.awaitingVerification.single().jobId)
    }

    @Test
    fun `cancelled jobs are not shown as server received even with a matched receipt`() {
        val verification = UploadVerification(
            jobId = "job-9",
            state = "MATCHED",
            matched = true,
            deletionAllowed = true
        )
        val job = job("job-9", UploadJobState.CANCELLED, verification = verification)

        val state = buildTransfersHomeUiState(input(queue = listOf(job)))

        assertTrue(state.verified.isEmpty())
        assertTrue(state.failed.isEmpty())
        assertTrue(state.queued.isEmpty())
        assertTrue(state.awaitingVerification.isEmpty())
        assertNull(state.emptyText)
    }

    @Test
    fun `a failed job is not shown as received even if its receipt says matched`() {
        val verification = UploadVerification(
            jobId = "job-failed",
            state = "MATCHED",
            matched = true,
            deletionAllowed = true
        )
        val job = job("job-failed", UploadJobState.FAILED, verification = verification)

        val state = buildTransfersHomeUiState(input(queue = listOf(job)))

        assertEquals("실패", state.failed.single().stateLabel)
        assertTrue(state.verified.isEmpty())
    }

    @Test
    fun `server received group contains only the newest three matched jobs`() {
        val jobs = (1..4).map { index ->
            val id = "job-$index"
            job(
                id,
                UploadJobState.COMPLETED,
                verification = UploadVerification(
                    jobId = id,
                    state = "MATCHED",
                    matched = true,
                    deletionAllowed = true,
                    verifiedAt = "2026-10-${14 + index}T08:00:00Z"
                )
            )
        }

        val state = buildTransfersHomeUiState(input(queue = jobs))

        assertEquals(listOf("job-4", "job-3", "job-2"), state.verified.map { it.jobId })
    }

    @Test
    fun `stored runs without an upload job appear in the sendable collection group`() {
        val state = buildTransfersHomeUiState(input(runs = listOf(taskRun("run-sendable", stored = true))))

        assertEquals(listOf("run-sendable"), state.sendableRuns.map { it.runId })
        assertEquals("서버 수신 기록이 없습니다.", state.sendableRuns.single().detail)
    }

    @Test
    fun `a job for the same stored source prevents a duplicate sendable row`() {
        val run = taskRun("run-existing", stored = true)
        val job = job("job-existing", UploadJobState.COMPLETED, runId = "run-existing")

        val state = buildTransfersHomeUiState(input(queue = listOf(job), runs = listOf(run)))

        assertTrue(state.sendableRuns.isEmpty())
        assertEquals("job-existing", state.awaitingVerification.single().jobId)
    }

    @Test
    fun `a job for another output of the same run does not hide the stored source`() {
        val run = taskRun("run-output", stored = true)
        val job = job("job-other-output", UploadJobState.COMPLETED, runId = "run-output", outputId = "other-output")

        val state = buildTransfersHomeUiState(input(queue = listOf(job), runs = listOf(run)))

        assertEquals(listOf("run-output"), state.sendableRuns.map { it.runId })
    }

    @Test
    fun `a verified job for the exact stored source removes it from the sendable group`() {
        val run = taskRun("run-received", stored = true)
        val job = job(
            "job-received",
            UploadJobState.COMPLETED,
            runId = "run-received",
            verification = UploadVerification(
                jobId = "job-received",
                state = "MATCHED",
                matched = true,
                deletionAllowed = true
            )
        )

        val state = buildTransfersHomeUiState(input(queue = listOf(job), runs = listOf(run)))

        assertTrue(state.sendableRuns.isEmpty())
        assertEquals(listOf("job-received"), state.verified.map { it.jobId })
    }

    @Test
    fun `queued jobs are listed and the active job is not repeated`() {
        val active = job("job-1", UploadJobState.UPLOADING, bytesTotal = 100L, bytesTransferred = 10L)
        val waiting = job("job-2", UploadJobState.QUEUED)

        val state = buildTransfersHomeUiState(input(queue = listOf(active, waiting), current = active))

        assertEquals("job-1", state.active.single().jobId)
        assertEquals(listOf("job-2"), state.queued.map { it.jobId })
        assertEquals("대기 중", state.queued.single().detail)
    }

    @Test
    fun `scanning transfer uses the file checking state word`() {
        val scanning = job("job-scanning", UploadJobState.SCANNING)

        val active = buildTransfersHomeUiState(input(queue = listOf(scanning))).active.single()

        assertEquals("파일 확인 중", active.stateLabel)
    }

    @Test
    fun `the transfer title uses the survey label of the run it belongs to`() {
        val run = taskRun("run-3")
        val job = job("job-7", UploadJobState.UPLOADING, bytesTotal = 100L, bytesTransferred = 10L, runId = "run-3")

        val active = buildTransfersHomeUiState(input(queue = listOf(job), runs = listOf(run)))
            .active.singleOrNull() ?: error("active transfer expected")

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
        runId: String? = null,
        outputId: String? = null
    ) = UploadJob(
        id = id,
        rootId = "data",
        relativePath = "runs/${runId ?: id}",
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
                outputId = outputId ?: "out-$it",
                createdAt = "created"
            )
        }
    )

    private fun taskRun(id: String, stored: Boolean = false) = TaskRun(
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
        ),
        output = if (stored) PipelineRunOutput(
            rootId = "data",
            path = "runs/$id",
            outputId = "out-$id",
            manifestState = "FINAL",
            manifest = PipelineOutputManifest(runId = id, generatedAt = "2026-10-14T06:42:00Z")
        ) else null
    )
}
