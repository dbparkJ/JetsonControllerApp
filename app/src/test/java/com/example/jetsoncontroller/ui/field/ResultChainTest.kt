package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class ResultChainTest {
    @Test fun `stored result starts with send and receipt has four ordered stages`() {
        val chain = chain()
        assertEquals(ResultAction.SEND, chain.action)
        assertTrue(chain.enabled)
        assertEquals(listOf("수집 종료", "장치 저장", "서버 수신", "GEO& Work 보고"),
            chain.receipt.rows().map { it.first.title })
        assertEquals("미확인", chain.receipt.server.word)
        assertEquals("이 휴대폰에서 보낸 기록이 없습니다.", chain.receipt.server.meta)
    }

    @Test fun `receipt uses the supplied local device word`() {
        assertEquals("이 태블릿에서 보낸 기록이 없습니다.", chain(localDeviceWord = "태블릿").receipt.server.meta)
    }

    @Test fun `unconfirmed storage blocks send even with a transfer or receipt`() {
        val run = resultTestRun()
        val pending = run.copy(output = run.output.copy(manifestState = "PENDING"))
        val chain = chain(run = pending, jobs = listOf(resultTestJob()))
        assertEquals(ResultAction.SEND, chain.action)
        assertFalse(chain.enabled)
        assertEquals("다시 확인", chain.remedy)
        assertFalse(chain.receipt.storage.done)
    }

    @Test fun `direct connection shows the exact send reason and device wifi remedy`() {
        val chain = chain(upload = false, connection = "직접 연결")
        assertEquals(ResultAction.SEND, chain.action)
        assertFalse(chain.enabled)
        assertEquals("같은 네트워크에서만 서버로 보낼 수 있습니다. 지금은 직접 연결입니다.", chain.explanation)
        assertEquals("장치 Wi-Fi 설정", chain.remedy)
        assertEquals("연결 방법", chain(upload = false, online = false).remedy)
    }

    @Test fun `active transfer is opened and completed unverified transfer is verified`() {
        for (state in listOf(UploadJobState.QUEUED, UploadJobState.SCANNING, UploadJobState.UPLOADING)) {
            val chain = chain(jobs = listOf(resultTestJob().copy(state = state, verification = null)))
            assertEquals(ResultAction.TRANSFER, chain.action)
            assertEquals("job", chain.jobId)
        }
        val sent = chain(jobs = listOf(resultTestJob().copy(verification = null)))
        assertEquals(ResultAction.VERIFY, sent.action)
        assertEquals("수신 미확인", sent.receipt.server.word)
    }

    @Test fun `verified linked result reports while device is offline`() {
        val chain = chain(jobs = listOf(resultTestJob()), online = false, upload = false)
        assertEquals(ResultAction.REPORT, chain.action)
        assertTrue(chain.enabled)
        assertTrue(chain.receipt.server.done)
        assertEquals(Tone.SUCCESS, chain.receipt.server.tone)
    }

    @Test fun `pending or unauthorized report stays the sole disabled primary`() {
        assertFalse(chain(jobs = listOf(resultTestJob()), pending = true).enabled)
        assertEquals("보고 대기", chain(jobs = listOf(resultTestJob()), pending = true).receipt.report.word)
        assertFalse(chain(jobs = listOf(resultTestJob()), reportEnabled = false).enabled)
        assertEquals(ResultAction.REPORT, chain(jobs = listOf(resultTestJob()), reportEnabled = false).action)
    }

    @Test fun `reported and unlinked results proceed to next collection`() {
        assertEquals(ResultAction.NEXT, chain(jobs = listOf(resultTestJob()), reported = true).action)
        assertEquals(ResultAction.NEXT, chain(jobs = listOf(resultTestJob()), linked = false).action)
        assertEquals("보고 완료", chain(jobs = listOf(resultTestJob()), reported = true).receipt.report.word)
    }

    @Test fun `source and complete context must match before a receipt can count`() {
        val job = resultTestJob()
        val context = job.context!!
        val wrong = listOf(
            job.copy(rootId = "other"), job.copy(relativePath = "other"),
            job.copy(context = context.copy(runId = "other")),
            job.copy(context = context.copy(deviceId = "other")),
            job.copy(context = context.copy(pipelineId = "other")),
            job.copy(context = context.copy(surveyProjectId = "other")),
            job.copy(context = context.copy(surveySectionId = "other")),
            job.copy(context = context.copy(sourceRevision = "other")),
            job.copy(context = context.copy(configSha256 = "other")),
            job.copy(context = context.copy(outputId = "other"))
        )
        wrong.forEach {
            assertEquals(ResultAction.SEND, chain(jobs = listOf(it)).action)
            assertFalse(chain(jobs = listOf(it)).receipt.server.done)
        }
    }

    @Test fun `receipt with wrong job target session or source never unlocks report`() {
        val job = resultTestJob()
        val verification = job.verification!!
        listOf(verification.copy(jobId = "other"), verification.copy(targetId = "other"),
            verification.copy(remoteSessionId = "other"), verification.copy(sourceName = "other"),
            verification.copy(targetId = null), verification.copy(remoteSessionId = null),
            verification.copy(sourceName = null)).forEach {
            val chain = chain(jobs = listOf(job.copy(verification = it)))
            assertEquals(ResultAction.VERIFY, chain.action)
            assertFalse(chain.receipt.server.done)
        }
    }

    @Test fun `mismatch requires sending again and verified attempt takes precedence`() {
        val good = resultTestJob()
        val mismatch = good.copy(verification = good.verification!!.copy(matched = false))
        assertEquals(ResultAction.SEND, chain(jobs = listOf(mismatch)).action)
        assertEquals("내용 불일치", chain(jobs = listOf(mismatch)).receipt.server.word)
        assertEquals(ResultAction.REPORT, chain(jobs = listOf(mismatch, good)).action)
    }

    @Test fun `result facts come from that run and keep valid zero output`() {
        val run = resultTestRun()
        assertEquals("12분", resultDuration(run))
        val zero = run.copy(output = run.output.copy(manifest = run.output.manifest!!.copy(fileCount = 0, bytesTotal = 0)))
        assertTrue(chain(run = zero).receipt.storage.meta!!.startsWith("0개 · 0"))
        assertFalse(runOutputStored(run.copy(output = run.output.copy(manifest = run.output.manifest!!.copy(runId = "other")))))
    }

    @Test fun `failed report exposes one retry action`() {
        val result = resultChain(resultTestRun(), listOf(resultTestJob()), true, "", "같은 네트워크", true,
            true, true, false, false, reportMessage = "서버에 연결한 뒤 다시 보고하세요.", reportFailed = true)
        assertEquals(ResultAction.REPORT, result.action)
        assertEquals("다시 보고", result.actionLabel)
        assertEquals("보고 실패", result.receipt.report.word)
    }

    private fun chain(run: PipelineRun = resultTestRun(), jobs: List<UploadJob> = emptyList(),
        upload: Boolean = true, online: Boolean = true, connection: String = "같은 네트워크",
        linked: Boolean = true, pending: Boolean = false, reported: Boolean = false,
        reportEnabled: Boolean = true, localDeviceWord: String = "휴대폰") = resultChain(
        run, jobs, upload, "서버 연결을 확인하세요.", connection, online, linked, reportEnabled, pending, reported,
        localDeviceWord = localDeviceWord
    )
}

internal fun resultTestRun(): PipelineRun {
    val snapshot = SurveyContextSnapshot(deviceId = "device", surveyProjectId = "project", surveyProjectLabel = "신월4거리",
        surveyProjectRevision = 1, surveySectionId = "section", surveySectionLabel = "북측 보조 구간",
        surveySectionRevision = 1, capturedAt = "2026-10-19T07:24:00Z")
    val policy = PipelineRunPolicy(pipelineId = "capture", revision = "policy", outputRootId = "recordings", updatedAt = "updated")
    return PipelineRun(runId = "run", logId = "run.log", pipelineId = "capture", deviceId = "device",
        state = "COMPLETED", startedAt = "2026-10-19T07:24:00Z", finishedAt = "2026-10-19T07:36:00Z", exitCode = 0,
        contextSnapshot = snapshot, policySnapshot = policy, preflightSnapshot = mock(PipelinePreflight::class.java),
        sourceRevision = "source", configRevision = "config",
        output = PipelineRunOutput("recordings", "20261019_1636", "output", "FINAL",
            PipelineOutputManifest(runId = "run", generatedAt = "generated", fileCount = 1204, bytesTotal = 1_200_000_000)),
        uploadContext = UploadContext(surveyProjectId = "project", surveySectionId = "section", runId = "run",
            deviceId = "device", pipelineId = "capture", sourceRevision = "source", configSha256 = "config",
            outputId = "output", createdAt = "created"))
}

internal fun resultTestJob(): UploadJob = UploadJob(id = "job", rootId = "recordings", relativePath = "20261019_1636",
    targetId = "server", state = UploadJobState.COMPLETED, bytesTotal = 1_200_000_000, bytesTransferred = 1_200_000_000,
    filesTotal = 1204, filesTransferred = 1204, currentFile = null, errorMessage = null,
    remoteSessionId = "session", sourceName = "20261019_1636", context = resultTestRun().uploadContext,
    verification = UploadVerification(jobId = "job", targetId = "server", remoteSessionId = "session",
        sourceName = "20261019_1636", state = "VERIFIED", matched = true, deletionAllowed = false))
