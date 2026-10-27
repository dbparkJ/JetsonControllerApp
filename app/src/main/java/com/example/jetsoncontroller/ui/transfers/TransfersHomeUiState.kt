package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.data.server.ServerDeviceEventIntegrationState
import com.example.jetsoncontroller.data.server.ServerDeviceEventStatus
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.data.transport.canStartServerUpload
import com.example.jetsoncontroller.data.transport.serverUploadUnavailableMessage
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.field.canonicalHistoryRunId
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.tasks.formatTaskTime
import com.example.jetsoncontroller.ui.tasks.parseInstantOrNull
import com.example.jetsoncontroller.ui.tasks.UnnamedDeviceLabel
import com.example.jetsoncontroller.ui.upload.TransferRate
import com.example.jetsoncontroller.ui.upload.UploadUiState
import com.example.jetsoncontroller.ui.upload.approximateRemainingLabel
import com.example.jetsoncontroller.ui.upload.estimateTransferRate
import com.example.jetsoncontroller.ui.upload.formatSize
import com.example.jetsoncontroller.ui.upload.transferRateLabel
import com.example.jetsoncontroller.ui.upload.transferSamplesFor
import com.example.jetsoncontroller.ui.upload.uploadProgressBytesLabel
import com.example.jetsoncontroller.ui.userFacingConnectionMessage
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Shown when a failed transfer carries no usable cause text. */
private const val DefaultTransferFailureText = "전송을 끝내지 못했습니다. 같은 네트워크인지 확인하고 다시 시도하세요."

/** Everything the 전송 root reads, gathered from the upload view model and the run history. */
internal data class TransfersHomeInput(
    val deviceName: String?,
    /** Control transport of the selected device, or null when no device is connected. */
    val transport: TransportType?,
    val upload: UploadUiState,
    val runs: List<TaskRun>,
    val developerModeEnabled: Boolean,
    val zone: ZoneId = ZoneId.systemDefault(),
    val deviceEventStatus: ServerDeviceEventStatus? = null,
    val now: Instant = Instant.now()
)

internal data class DeviceEventNoticeState(
    val text: String,
    val tone: Tone
)

internal data class ActiveTransferState(
    val jobId: String,
    val title: String,
    val subtitle: String,
    val stateLabel: String,
    val stateTone: Tone,
    /** 0..1 when the total is known, otherwise null (the bar then shows indeterminate progress). */
    val progress: Float?,
    val sentLabel: String,
    /** Null when speed cannot be computed. The screen omits the cell instead of guessing. */
    val speedLabel: String?,
    /** Null when remaining time cannot be computed. */
    val remainingLabel: String?,
    val canCancel: Boolean,
    val target: TransferActionTarget? = null
)

internal data class TransferRowState(
    val jobId: String,
    val title: String,
    val detail: String,
    val tone: Tone
)

internal data class FailedTransferState(
    val jobId: String,
    val title: String,
    val stateLabel: String,
    val cause: String
)

internal data class StoredRunTransferState(
    val runId: String,
    val title: String,
    val detail: String
)

internal data class TransfersHomeUiState(
    /** One line that says why uploads may be unavailable. Always present. */
    val explanation: String,
    val explanationTone: Tone,
    val deviceEventNotice: DeviceEventNoticeState?,
    val active: List<ActiveTransferState>,
    val failed: List<FailedTransferState>,
    val sendableRuns: List<StoredRunTransferState>,
    val queued: List<TransferRowState>,
    val awaitingVerification: List<TransferRowState>,
    val verified: List<TransferRowState>,
    /** Shown only when there is nothing to list at all. */
    val emptyText: String?
)

internal fun buildTransfersHomeUiState(input: TransfersHomeInput): TransfersHomeUiState {
    val upload = input.upload
    val jobs = (listOfNotNull(upload.currentJob) + upload.queue).distinctBy { it.id }
        .filter { it.context == null || upload.deviceId == null || it.context.deviceId.equals(upload.deviceId, true) }
    val deviceName = input.deviceName?.takeIf(String::isNotBlank) ?: UnnamedDeviceLabel
    val active = jobs.filter { isInFlight(it.state) }
        .map { job -> activeTransfer(job, upload, input, deviceName) }
    val failed = jobs
        .filter { job ->
            job.state != UploadJobState.CANCELLED &&
                !isInFlight(job.state) &&
                (job.state == UploadJobState.FAILED || verifiedFor(job)?.matched == false)
        }
        .map { job ->
            val mismatched = job.state != UploadJobState.FAILED && verifiedFor(job)?.matched == false
            FailedTransferState(
                jobId = job.id,
                title = transferTitle(job, input.runs),
                stateLabel = if (mismatched) "내용 불일치" else "실패",
                cause = if (mismatched) {
                    "서버에 받은 내용이 보낸 내용과 일치하지 않습니다. 다시 보내 주세요."
                } else {
                    userFacingConnectionMessage(job.errorMessage, input.developerModeEnabled)
                        ?: DefaultTransferFailureText
                }
            )
        }
    val sendableRuns = input.runs
        .distinctBy {
            Triple(it.deviceId?.lowercase(Locale.US), canonicalHistoryRunId(it), it.output?.outputId)
        }
        .filter(::isStoredRun)
        .filter { upload.deviceId == null || it.deviceId.equals(upload.deviceId, true) }
        .filter { run ->
            jobs.none { job -> job.state != UploadJobState.CANCELLED && jobMatchesRunSource(job, run) }
        }
        .map { run ->
            StoredRunTransferState(
                runId = canonicalHistoryRunId(run).orEmpty(),
                title = transferTitle(run),
                detail = "서버 수신 기록이 없습니다."
            )
        }
    val queued = jobs
        .filter { it.state == UploadJobState.QUEUED && verifiedFor(it)?.matched != false }
        .map { job ->
            TransferRowState(
                jobId = job.id,
                title = transferTitle(job, input.runs),
                detail = transferRowMeta(job, input),
                tone = Tone.UNKNOWN
            )
        }
    val awaitingVerification = jobs
        .filter { job ->
            job.state == UploadJobState.COMPLETED && verifiedFor(job) == null
        }
        .map { job ->
            TransferRowState(
                jobId = job.id,
                title = transferTitle(job, input.runs),
                detail = transferRowMeta(job, input),
                tone = Tone.PENDING
            )
        }
    val verified = jobs
        .filter { job -> job.state == UploadJobState.COMPLETED && verifiedFor(job)?.matched == true }
        .sortedByDescending { job ->
            val verification = verifiedFor(job)
            parseInstantOrNull(verification?.verifiedAt ?: job.verifiedAt)?.toEpochMilli() ?: Long.MIN_VALUE
        }
        .take(3)
        .mapNotNull { job -> verifiedRow(job, input) }
    val (explanation, explanationTone) = explanationFor(input.transport)
    return TransfersHomeUiState(
        explanation = explanation,
        explanationTone = explanationTone,
        deviceEventNotice = deviceEventNoticeFor(input.deviceEventStatus, input.now, input.zone),
        active = active,
        failed = failed,
        sendableRuns = sendableRuns,
        queued = queued,
        awaitingVerification = awaitingVerification,
        verified = verified,
        emptyText = if (jobs.isEmpty() && sendableRuns.isEmpty()) {
            "보낼 파일을 고르면 여기에서 진행을 확인할 수 있습니다."
        } else {
            null
        }
    )
}

/** Read-only GEO& Work delivery state, with integration failures taking precedence over counts. */
internal fun deviceEventNoticeFor(
    status: ServerDeviceEventStatus?,
    now: Instant,
    zone: ZoneId
): DeviceEventNoticeState? {
    status ?: return null
    return when (status.integration.normalizedState) {
        ServerDeviceEventIntegrationState.EXPIRED -> DeviceEventNoticeState(
            "GEO& Work 연동이 만료되었습니다. 관리자에게 연동 연장을 요청하세요.",
            Tone.ERROR
        )
        ServerDeviceEventIntegrationState.ERROR -> DeviceEventNoticeState(
            "GEO& Work 알림을 보내지 못하고 있습니다. 수신 서버 관리자에게 알려 주세요.",
            Tone.ERROR
        )
        ServerDeviceEventIntegrationState.ACTIVE -> when {
            status.window.dead > 0 -> DeviceEventNoticeState(
                "GEO& Work 알림 ${String.format(Locale.US, "%,d", status.window.dead)}건을 보내지 못했습니다. 수신 서버 관리자에게 알려 주세요.",
                Tone.WARNING
            )
            status.window.pending > 0 -> DeviceEventNoticeState(
                "GEO& Work 알림 ${String.format(Locale.US, "%,d", status.window.pending)}건을 보내는 중입니다.",
                Tone.PENDING
            )
            status.lastDelivered != null -> {
                val deliveredAt = parseInstantOrNull(status.lastDelivered.at) ?: return null
                val deliveredTime = Formats.dateTime(
                    deliveredAt,
                    now,
                    zone
                )
                DeviceEventNoticeState(
                    "GEO& Work에 알림을 보냈습니다 · $deliveredTime ${status.lastDelivered.status}",
                    Tone.SUCCESS
                )
            }
            else -> DeviceEventNoticeState(
                "GEO& Work 알림이 켜져 있습니다.",
                Tone.INFO
            )
        }
        ServerDeviceEventIntegrationState.NOT_CONFIGURED,
        ServerDeviceEventIntegrationState.UNKNOWN -> null
    }
}

private fun isInFlight(state: UploadJobState): Boolean =
    state == UploadJobState.SCANNING || state == UploadJobState.UPLOADING

/** Accept a receipt only when it belongs to this job and every supplied source key agrees. */
private fun verifiedFor(job: UploadJob): UploadVerification? {
    return matchingTransferVerification(job)
}

private fun isStoredRun(run: TaskRun): Boolean {
    if (run.active || run.finishedAt == null) return false
    val output = run.output ?: return false
    return canonicalHistoryRunId(run) != null && output.rootId.isNotBlank() &&
        output.path.isNotBlank() && output.outputId.isNotBlank()
}

/** Match a job to a run only when its run, device, output and stored path all identify that source. */
private fun jobMatchesRunSource(job: UploadJob, run: TaskRun): Boolean {
    val runId = canonicalHistoryRunId(run) ?: return false
    val output = run.output ?: return false
    val context = job.context ?: return false
    return context.runId == runId &&
        context.deviceId.equals(run.deviceId, ignoreCase = true) &&
        context.pipelineId == run.pipelineId &&
        context.surveyProjectId == run.contextSnapshot?.surveyProjectId &&
        context.surveySectionId == run.contextSnapshot?.surveySectionId &&
        context.outputId == output.outputId &&
        job.rootId == output.rootId &&
        job.relativePath.trim('/') == output.path.trim('/')
}

private fun transferTitle(run: TaskRun): String =
    run.contextSnapshot?.let { "${it.surveyProjectLabel} · ${it.surveySectionLabel}" }
        ?: run.label.takeIf(String::isNotBlank)
        ?: "수집 결과"

private fun activeTransfer(
    job: UploadJob,
    upload: UploadUiState,
    input: TransfersHomeInput,
    deviceName: String
): ActiveTransferState {
    val rate: TransferRate = estimateTransferRate(
        samples = transferSamplesFor(job.id, upload.transferSamples),
        bytesTotal = job.bytesTotal,
        bytesTransferred = job.bytesTransferred,
        backendBytesPerSecond = job.throughputBytesPerSecond,
        backendEtaSeconds = job.etaSeconds
    )
    val total = job.bytesTotal?.takeIf { it > 0L }
    val transferred = job.bytesTransferred
    val files = job.filesTotal?.let { " · 파일 ${String.format(Locale.US, "%,d", it)}개" }.orEmpty()
    return ActiveTransferState(
        jobId = job.id,
        title = transferTitle(job, input.runs),
        subtitle = "$deviceName → 수신 서버$files",
        stateLabel = if (job.state == UploadJobState.UPLOADING) "보내는 중" else "파일 확인 중",
        stateTone = if (job.state == UploadJobState.UPLOADING) Tone.INFO else Tone.PENDING,
        progress = if (total != null && transferred != null) {
            (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        },
        sentLabel = uploadProgressBytesLabel(transferred, job.bytesTotal),
        speedLabel = rate.bytesPerSecond?.let(::transferRateLabel),
        remainingLabel = rate.remainingSeconds?.let(::approximateRemainingLabel),
        canCancel = job.state == UploadJobState.QUEUED || isInFlight(job.state),
        target = upload.deviceId?.let { transferActionTarget(it, job) }
    )
}

private fun verifiedRow(job: UploadJob, input: TransfersHomeInput): TransferRowState? {
    val verification = verifiedFor(job)?.takeIf { it.matched } ?: return null
    val received = formatTaskTime(verification.verifiedAt ?: job.verifiedAt, input.zone)
    val size = (verification.bytesTotal ?: job.bytesTotal)?.let { formatSize(it) }
    val files = verification.filesTotal?.let { "파일 ${String.format(Locale.US, "%,d", it)}개" }
    return TransferRowState(
        jobId = job.id,
        title = transferTitle(job, input.runs),
        detail = listOfNotNull(received?.let { "$it 수신 확인" }, size, files).joinToString(" · ")
            .ifBlank { "수신 확인" },
        tone = Tone.SUCCESS
    )
}

private fun transferRowMeta(job: UploadJob, input: TransfersHomeInput): String = listOfNotNull(
    input.upload.targets.firstOrNull { it.id == job.targetId }?.label,
    job.bytesTotal?.let(::formatSize),
    job.filesTotal?.let { "파일 ${String.format(Locale.US, "%,d", it)}개" }
).joinToString(" · ").ifBlank { "받는 서버에서 확인할 자료" }

/** Why uploads may be unavailable, in one line. Only same-network uploads are allowed. */
private fun explanationFor(transport: TransportType?): Pair<String, Tone> {
    if (canStartServerUpload(transport)) {
        return "장치가 휴대폰과 같은 네트워크에 있을 때 서버로 보낼 수 있습니다." to Tone.INFO
    }
    val reason = serverUploadUnavailableMessage(transport).takeIf(String::isNotBlank)
        ?: "장치를 같은 네트워크에 연결한 뒤 서버로 보낼 수 있습니다."
    return reason to Tone.INFO
}

/** The run's survey label when the job belongs to a run we know; otherwise the file or folder name. */
internal fun transferTitle(job: UploadJob, runs: List<TaskRun>): String {
    val run = job.context?.runId?.let { id -> runs.firstOrNull { it.runId == id } }
    run?.contextSnapshot?.let { return "${it.surveyProjectLabel} · ${it.surveySectionLabel}" }
    return job.sourceName?.takeIf(String::isNotBlank)
        ?: job.folderName?.takeIf(String::isNotBlank)
        ?: job.relativePath.substringAfterLast('/').takeIf(String::isNotBlank)
        ?: "업로드 작업"
}
