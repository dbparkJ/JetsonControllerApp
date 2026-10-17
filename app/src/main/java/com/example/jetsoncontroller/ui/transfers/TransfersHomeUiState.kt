package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.data.transport.canStartServerUpload
import com.example.jetsoncontroller.data.transport.serverUploadUnavailableMessage
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.tasks.UnnamedDeviceLabel
import com.example.jetsoncontroller.ui.tasks.formatTaskTime
import com.example.jetsoncontroller.ui.upload.TransferRate
import com.example.jetsoncontroller.ui.upload.UploadUiState
import com.example.jetsoncontroller.ui.upload.approximateRemainingLabel
import com.example.jetsoncontroller.ui.upload.estimateTransferRate
import com.example.jetsoncontroller.ui.upload.formatSize
import com.example.jetsoncontroller.ui.upload.transferRateLabel
import com.example.jetsoncontroller.ui.upload.transferSamplesFor
import com.example.jetsoncontroller.ui.upload.uploadProgressBytesLabel
import com.example.jetsoncontroller.ui.userFacingConnectionMessage
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
    val zone: ZoneId = ZoneId.systemDefault()
)

internal data class ActiveTransferState(
    val jobId: String,
    val title: String,
    val subtitle: String,
    val stateLabel: String,
    val stateTone: StatusTone,
    /** 0..1 when the total is known, otherwise null (the bar then shows indeterminate progress). */
    val progress: Float?,
    val sentLabel: String,
    /** Null when speed cannot be computed. The screen omits the cell instead of guessing. */
    val speedLabel: String?,
    /** Null when remaining time cannot be computed. */
    val remainingLabel: String?,
    val canCancel: Boolean
)

internal data class TransferRowState(
    val jobId: String,
    val title: String,
    val detail: String,
    val tone: StatusTone
)

internal data class FailedTransferState(
    val jobId: String,
    val title: String,
    val cause: String
)

internal data class TransfersHomeUiState(
    /** One line that says why uploads may be unavailable. Always present. */
    val explanation: String,
    val explanationTone: StatusTone,
    val active: ActiveTransferState?,
    val queued: List<TransferRowState>,
    val failed: List<FailedTransferState>,
    val verified: List<TransferRowState>,
    /** Shown only when there is nothing to list at all. */
    val emptyText: String?
)

internal fun buildTransfersHomeUiState(input: TransfersHomeInput): TransfersHomeUiState {
    val upload = input.upload
    val jobs = (listOfNotNull(upload.currentJob) + upload.queue).distinctBy { it.id }
    val activeJob = jobs.firstOrNull { it.id == upload.currentJob?.id && isInFlight(it.state) }
        ?: jobs.firstOrNull { isInFlight(it.state) }
    val deviceName = input.deviceName?.takeIf(String::isNotBlank) ?: UnnamedDeviceLabel
    val active = activeJob?.let { job -> activeTransfer(job, upload, input, deviceName) }
    val queued = jobs
        .filter { it.id != activeJob?.id && (it.state == UploadJobState.QUEUED || isInFlight(it.state)) }
        .map { job ->
            TransferRowState(
                jobId = job.id,
                title = transferTitle(job, input.runs),
                detail = if (job.state == UploadJobState.QUEUED) "보내기 순서를 기다리는 중입니다." else "보내는 중입니다.",
                tone = StatusTone.PENDING
            )
        }
    val failed = jobs
        .filter { it.state == UploadJobState.FAILED }
        .map { job ->
            FailedTransferState(
                jobId = job.id,
                title = transferTitle(job, input.runs),
                cause = userFacingConnectionMessage(job.errorMessage, input.developerModeEnabled)
                    ?: DefaultTransferFailureText
            )
        }
    val verified = jobs.mapNotNull { job -> verifiedRow(job, input) }
    val (explanation, explanationTone) = explanationFor(input.transport)
    return TransfersHomeUiState(
        explanation = explanation,
        explanationTone = explanationTone,
        active = active,
        queued = queued,
        failed = failed,
        verified = verified,
        emptyText = if (jobs.isEmpty()) {
            "보낸 파일이 없습니다. 보낼 파일을 고르면 여기에서 진행을 확인할 수 있습니다."
        } else {
            null
        }
    )
}

private fun isInFlight(state: UploadJobState): Boolean =
    state == UploadJobState.SCANNING || state == UploadJobState.UPLOADING

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
        stateLabel = if (job.state == UploadJobState.UPLOADING) "보내는 중" else "검사 중",
        stateTone = if (job.state == UploadJobState.UPLOADING) StatusTone.INFO else StatusTone.PENDING,
        progress = if (total != null && transferred != null) {
            (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        },
        sentLabel = uploadProgressBytesLabel(transferred, job.bytesTotal),
        speedLabel = rate.bytesPerSecond?.let(::transferRateLabel),
        remainingLabel = rate.remainingSeconds?.let(::approximateRemainingLabel),
        canCancel = job.state == UploadJobState.QUEUED || isInFlight(job.state)
    )
}

private fun verifiedRow(job: UploadJob, input: TransfersHomeInput): TransferRowState? {
    val verification = job.verification
    return when {
        verification?.matched == true -> {
            val received = formatTaskTime(verification.verifiedAt ?: job.verifiedAt, input.zone)
            val size = (verification.bytesTotal ?: job.bytesTotal)?.let { formatSize(it) }
            val files = verification.filesTotal?.let { "파일 ${String.format(Locale.US, "%,d", it)}개" }
            TransferRowState(
                jobId = job.id,
                title = transferTitle(job, input.runs),
                detail = listOfNotNull(received?.let { "$it 수신" }, size, files).joinToString(" · ")
                    .ifBlank { "서버 수신을 확인했습니다." },
                tone = StatusTone.SUCCESS
            )
        }
        verification?.matched == false -> TransferRowState(
            jobId = job.id,
            title = transferTitle(job, input.runs),
            detail = "서버에 받은 내용이 보낸 내용과 일치하지 않습니다. 다시 보내 주세요.",
            tone = StatusTone.WARNING
        )
        job.state == UploadJobState.COMPLETED -> TransferRowState(
            jobId = job.id,
            title = transferTitle(job, input.runs),
            detail = "보내기는 끝났고 서버 수신을 확인하고 있습니다.",
            tone = StatusTone.PENDING
        )
        else -> null
    }
}

/** Why uploads may be unavailable, in one line. Only same-network uploads are allowed. */
private fun explanationFor(transport: TransportType?): Pair<String, StatusTone> {
    if (canStartServerUpload(transport)) {
        return "장치가 휴대폰과 같은 네트워크에 있을 때 서버로 보낼 수 있습니다." to StatusTone.INFO
    }
    val reason = serverUploadUnavailableMessage(transport).takeIf(String::isNotBlank)
        ?: "장치를 같은 네트워크에 연결한 뒤 서버로 보낼 수 있습니다."
    return reason to StatusTone.INFO
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
