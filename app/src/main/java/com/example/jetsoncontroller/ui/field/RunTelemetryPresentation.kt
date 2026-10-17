package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.RunTelemetry
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.ui.components.StatusTone
import java.util.Locale

internal const val RunTelemetryFreshMillis = 15_000L

internal fun verifiedRunTelemetry(run: PipelineRun?): RunTelemetry? {
    val telemetry = run?.telemetry ?: return null
    return telemetry.takeIf {
        it.schemaVersion == 1 &&
            it.runId == run.runId &&
            it.outputId == run.output.outputId &&
            it.sourceRevision == run.sourceRevision &&
            it.observedAtEpochMillis > 0L &&
            it.bytesObservedAtEpochMillis > 0L &&
            (it.durationMillis == null || it.durationMillis >= 0L) &&
            (it.collectedBytes == null || it.collectedBytes >= 0L) &&
            (it.collectedFileCount == null || it.collectedFileCount >= 0)
    }
}

internal fun smoothedDurationMillis(
    baseDurationMillis: Long?,
    receivedAtElapsedRealtime: Long,
    nowElapsedRealtime: Long,
    mayAdvance: Boolean,
    freshForMillis: Long = RunTelemetryFreshMillis
): Long? {
    val base = baseDurationMillis?.takeIf { it >= 0L } ?: return null
    if (!mayAdvance) return base
    val age = (nowElapsedRealtime - receivedAtElapsedRealtime).coerceAtLeast(0L)
    return base + age.coerceAtMost(freshForMillis)
}

internal fun runDurationLabel(durationMillis: Long?): String {
    val totalSeconds = durationMillis?.takeIf { it >= 0L }?.div(1_000L) ?: return "—"
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    else "%02d:%02d".format(Locale.US, minutes, seconds)
}

internal fun runBytesLabel(bytes: Long?): String = when {
    bytes == null || bytes < 0L -> "확인 중"
    bytes >= 1_073_741_824L -> "%.1f GB".format(Locale.US, bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(Locale.US, bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(Locale.US, bytes / 1_024.0)
    else -> "$bytes B"
}

internal fun telemetryFreshnessLabel(ageMillis: Long?, bytesState: String?): String = when {
    ageMillis == null -> "미확인"
    ageMillis < 1_500L -> "방금"
    ageMillis < 60_000L -> "${ageMillis / 1_000L}초 전"
    else -> "${ageMillis / 60_000L}분 전"
}.let { label ->
    if (bytesState.equals("STALE", ignoreCase = true)) "$label · 이전 용량" else label
}

internal fun telemetryObservationLabel(telemetry: RunTelemetry, receiptAgeMillis: Long): String {
    val status = telemetryFreshnessLabel(receiptAgeMillis, null)
    val sampleLag = (telemetry.observedAtEpochMillis - telemetry.bytesObservedAtEpochMillis).coerceAtLeast(0L)
    val bytesAge = receiptAgeMillis.coerceAtLeast(0L) + sampleLag
    return if (sampleLag >= 1_500L || telemetry.collectionBytesState.equals("STALE", true)) {
        "$status · 용량 ${telemetryFreshnessLabel(bytesAge, telemetry.collectionBytesState)}"
    } else status
}

/**
 * One cell of the three-part run receipt: 수집 종료 → 장치 저장 → 서버 수신.
 * [tone] drives the icon and color; [value] is the short state word shown under [title].
 */
internal data class RunReceiptCell(
    val title: String,
    val value: String,
    val tone: StatusTone
)

/** Receipt for a run the phone already holds in full (result screen). */
internal fun pipelineRunReceiptCells(
    run: PipelineRun?,
    serverTone: StatusTone
): List<RunReceiptCell> {
    val finished = when {
        run == null -> StatusTone.UNKNOWN
        run.active || run.finishedAt == null -> StatusTone.PENDING
        run.exitCode != null && run.exitCode != 0 -> StatusTone.WARNING
        else -> StatusTone.SUCCESS
    }
    val manifestState = run?.output?.manifestState.orEmpty()
    val stored = runOutputStored(run)
    val storedTone = when {
        stored -> StatusTone.SUCCESS
        manifestState.equals("PENDING", true) || manifestState.equals("RUNNING", true) -> StatusTone.PENDING
        manifestState.equals("FAILED", true) || manifestState.equals("ERROR", true) -> StatusTone.WARNING
        else -> StatusTone.UNKNOWN
    }
    return listOf(
        RunReceiptCell("수집 종료", finishedValue(finished), finished),
        RunReceiptCell("장치 저장", storedValue(storedTone), storedTone),
        RunReceiptCell("서버 수신", serverValue(serverTone), serverTone)
    )
}

/** Receipt for a history row: only the task summary and the upload jobs are available. */
internal fun taskRunReceiptCells(run: TaskRun, uploadJobs: List<UploadJob>): List<RunReceiptCell> {
    val finished = when {
        run.isActiveRun() -> StatusTone.PENDING
        run.state == "STOPPED" -> StatusTone.INFO
        run.state == "FAILED" || (run.exitCode != null && run.exitCode != 0) -> StatusTone.WARNING
        run.finishedAt != null || run.state == "COMPLETED" -> StatusTone.SUCCESS
        else -> StatusTone.UNKNOWN
    }
    val storedTone = when {
        canonicalHistoryRunId(run) != null -> StatusTone.SUCCESS
        run.output?.manifestState.equals("PENDING", true) ||
            run.output?.manifestState.equals("RUNNING", true) -> StatusTone.PENDING
        run.output?.manifestState.equals("FAILED", true) ||
            run.output?.manifestState.equals("ERROR", true) -> StatusTone.WARNING
        else -> StatusTone.UNKNOWN
    }
    val serverTone = uploadReceiptTone(run.runId, run.deviceId, uploadJobs)
    return listOf(
        RunReceiptCell("수집 종료", taskFinishedValue(finished), finished),
        RunReceiptCell("장치 저장", storedValue(storedTone), storedTone),
        RunReceiptCell("서버 수신", serverValue(serverTone), serverTone)
    )
}

/**
 * Server receipt tone for one run. Confirmation needs a job whose context carries the same run
 * ID (and device); anything else stays unknown or waiting, and a missing match is never read as
 * a failure. Mirrors the ladder used by the result screen (serverReceiptPresentation).
 */
internal fun uploadReceiptTone(runId: String?, deviceId: String?, jobs: List<UploadJob>): StatusTone {
    if (runId.isNullOrBlank()) return StatusTone.UNKNOWN
    val exact = jobs.filter { job ->
        job.context?.runId == runId &&
            (deviceId == null || job.context?.deviceId.equals(deviceId, ignoreCase = true))
    }
    if (exact.isEmpty()) return StatusTone.UNKNOWN
    return when {
        exact.any { it.verification?.matched == true } -> StatusTone.SUCCESS
        exact.any { it.state in setOf(UploadJobState.QUEUED, UploadJobState.SCANNING, UploadJobState.UPLOADING) } ->
            StatusTone.PENDING
        exact.any { it.state == UploadJobState.COMPLETED && it.verification?.matched == null } ->
            StatusTone.PENDING
        else -> StatusTone.WARNING
    }
}

private fun finishedValue(tone: StatusTone): String = when (tone) {
    StatusTone.SUCCESS -> "종료"
    StatusTone.PENDING -> "종료 확인 중"
    StatusTone.WARNING, StatusTone.ERROR -> "다시 확인"
    else -> "미확인"
}

private fun taskFinishedValue(tone: StatusTone): String = when (tone) {
    StatusTone.SUCCESS -> "종료"
    StatusTone.PENDING -> "진행 중"
    StatusTone.INFO -> "중지됨"
    StatusTone.WARNING, StatusTone.ERROR -> "비정상 종료"
    else -> "미확인"
}

private fun storedValue(tone: StatusTone): String = when (tone) {
    StatusTone.SUCCESS -> "저장 확인"
    StatusTone.PENDING -> "확인 중"
    StatusTone.WARNING, StatusTone.ERROR -> "다시 확인"
    else -> "미확인"
}

private fun serverValue(tone: StatusTone): String = when (tone) {
    StatusTone.SUCCESS -> "수신 확인"
    StatusTone.PENDING -> "전송 중"
    StatusTone.WARNING, StatusTone.ERROR -> "다시 확인"
    else -> "미확인"
}
