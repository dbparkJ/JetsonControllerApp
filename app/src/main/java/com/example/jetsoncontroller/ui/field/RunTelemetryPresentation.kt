package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.RunTelemetry
import com.example.jetsoncontroller.model.StopTarget
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.GeoFigure
import com.example.jetsoncontroller.ui.model.Formats
import java.time.Duration
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
    return Formats.clock(durationMillis?.takeIf { it >= 0L }?.let(Duration::ofMillis))
}

internal fun runBytesLabel(bytes: Long?): String = Formats.size(bytes)

internal enum class ActiveRunState {
    START_CHECKING, COLLECTING, DISCONNECTED, UNKNOWN, STOP_CHECKING, ENDED
}

internal fun runHasEnded(run: PipelineRun?): Boolean = run != null && !run.active &&
    (run.finishedAt != null || run.state.uppercase(Locale.ROOT) in setOf("COMPLETED", "STOPPED", "FAILED"))

internal fun activeRunState(
    run: PipelineRun?, online: Boolean, awaitingStart: Boolean, stopping: Boolean,
    stopAvailable: Boolean
): ActiveRunState = when {
    awaitingStart -> ActiveRunState.START_CHECKING
    stopping -> ActiveRunState.STOP_CHECKING
    !online -> ActiveRunState.DISCONNECTED
    runHasEnded(run) -> ActiveRunState.ENDED
    run?.active == true && stopAvailable -> ActiveRunState.COLLECTING
    else -> ActiveRunState.UNKNOWN
}

internal fun runTarget(run: PipelineRun): StopTarget = StopTarget(run.deviceId, run.pipelineId, run.runId)

internal fun StopTarget.matches(run: PipelineRun?): Boolean = run != null &&
    deviceId.equals(run.deviceId, true) && pipelineId == run.pipelineId && runId == run.runId

internal enum class RunStopStep { IDLE, CONFIRMING, STOPPING, RESULT_OPENED }
internal enum class RunStopEvent { REQUEST, CONFIRM, DISMISS, OBSERVE, FAILURE }
internal data class RunStopFlow(val step: RunStopStep = RunStopStep.IDLE, val target: StopTarget? = null)
internal data class RunStopTransition(
    val flow: RunStopFlow, val command: StopTarget? = null, val openResult: StopTarget? = null
)

/** Only an explicit confirmation arms navigation, and only matching terminal evidence completes it. */
internal fun reduceRunStop(
    flow: RunStopFlow,
    event: RunStopEvent,
    run: PipelineRun?,
    online: Boolean,
    stopAvailable: Boolean,
    awaitingStart: Boolean = false,
    stopInProgress: Boolean = false,
    requestedTarget: StopTarget? = null
): RunStopTransition {
    val allowed = online && stopAvailable && run?.active == true && !awaitingStart && !stopInProgress
    val same = flow.target?.matches(run) == true
    val idle = RunStopTransition(RunStopFlow())
    return when (event) {
        RunStopEvent.REQUEST -> if (flow.step == RunStopStep.IDLE && allowed && run != null &&
            (requestedTarget == null || requestedTarget.matches(run))) {
            RunStopTransition(RunStopFlow(RunStopStep.CONFIRMING, runTarget(run)))
        } else RunStopTransition(flow)
        RunStopEvent.CONFIRM -> if (flow.step == RunStopStep.CONFIRMING && allowed && same) {
            RunStopTransition(flow.copy(step = RunStopStep.STOPPING), command = flow.target)
        } else if (flow.step == RunStopStep.CONFIRMING && !same) idle else RunStopTransition(flow)
        RunStopEvent.DISMISS -> if (flow.step == RunStopStep.CONFIRMING) idle else RunStopTransition(flow)
        RunStopEvent.FAILURE -> if (flow.step == RunStopStep.STOPPING) idle else RunStopTransition(flow)
        RunStopEvent.OBSERVE -> when {
            flow.step == RunStopStep.CONFIRMING &&
                (!same || run?.active != true || awaitingStart || (online && !stopAvailable)) -> idle
            flow.step == RunStopStep.STOPPING && run != null && !same -> idle
            flow.step == RunStopStep.STOPPING && same && online && !awaitingStart && runHasEnded(run) ->
                RunStopTransition(flow.copy(step = RunStopStep.RESULT_OPENED), openResult = flow.target)
            else -> RunStopTransition(flow)
        }
    }
}

internal fun activeRunFigures(
    run: PipelineRun?, durationMillis: Long?, elapsedFallback: String?, state: ActiveRunState,
    telemetryAgeMillis: Long?, outsideCount: Int?
): List<GeoFigure> {
    val telemetry = verifiedRunTelemetry(run)
    val hidden = state == ActiveRunState.START_CHECKING
    val stale = state in setOf(ActiveRunState.DISCONNECTED, ActiveRunState.UNKNOWN) ||
        (run?.active == true && (telemetryAgeMillis == null || telemetryAgeMillis > RunTelemetryFreshMillis))
    val elapsed = if (telemetry != null) durationMillis?.let(::runDurationLabel) else elapsedFallback
    val quality = run?.quality
    val ratio = quality?.rtkFixRatio?.takeIf {
        it.isFinite() && it in 0.0..1.0 && (quality.rtkObservedDurationMillis ?: 0L) > 0L
    }
    val bytesStale = telemetry?.collectionBytesState.equals("STALE", true) ||
        (telemetry != null && telemetryAgeMillis != null && telemetryAgeMillis +
            (telemetry.observedAtEpochMillis - telemetry.bytesObservedAtEpochMillis).coerceAtLeast(0L) > RunTelemetryFreshMillis)
    fun figure(label: String, value: String?, old: Boolean = stale) =
        GeoFigure(label, value, valid = !hidden && value != null && value !in setOf("미확인", "—", "확인 중"), stale = old)
    return listOf(
        figure("경과 시간", elapsed),
        figure("수집 용량", telemetry?.collectedBytes?.let(::runBytesLabel), stale || bytesStale),
        figure("정밀 위치", ratio?.let { Formats.percent(it * 100.0) }),
        figure("구역 밖 기록", outsideCount?.takeIf { it >= 0 }?.let { Formats.occurrenceCount(it.toLong()) })
    )
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
    val tone: Tone
)

/** Receipt for a run the phone already holds in full (result screen). */
internal fun pipelineRunReceiptCells(
    run: PipelineRun?,
    serverTone: Tone
): List<RunReceiptCell> {
    val finished = when {
        run == null -> Tone.UNKNOWN
        run.active || run.finishedAt == null -> Tone.PENDING
        run.exitCode != null && run.exitCode != 0 -> Tone.WARNING
        else -> Tone.SUCCESS
    }
    val manifestState = run?.output?.manifestState.orEmpty()
    val stored = runOutputStored(run)
    val storedTone = when {
        stored -> Tone.SUCCESS
        manifestState.equals("PENDING", true) || manifestState.equals("RUNNING", true) -> Tone.PENDING
        manifestState.equals("FAILED", true) || manifestState.equals("ERROR", true) -> Tone.WARNING
        else -> Tone.UNKNOWN
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
        run.isActiveRun() -> Tone.PENDING
        run.state == "STOPPED" -> Tone.INFO
        run.state == "FAILED" || (run.exitCode != null && run.exitCode != 0) -> Tone.WARNING
        run.finishedAt != null || run.state == "COMPLETED" -> Tone.SUCCESS
        else -> Tone.UNKNOWN
    }
    val storedTone = when {
        canonicalHistoryRunId(run) != null -> Tone.SUCCESS
        run.output?.manifestState.equals("PENDING", true) ||
            run.output?.manifestState.equals("RUNNING", true) -> Tone.PENDING
        run.output?.manifestState.equals("FAILED", true) ||
            run.output?.manifestState.equals("ERROR", true) -> Tone.WARNING
        else -> Tone.UNKNOWN
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
internal fun uploadReceiptTone(runId: String?, deviceId: String?, jobs: List<UploadJob>): Tone {
    if (runId.isNullOrBlank()) return Tone.UNKNOWN
    val exact = jobs.filter { job ->
        job.context?.runId == runId &&
            (deviceId == null || job.context?.deviceId.equals(deviceId, ignoreCase = true))
    }
    if (exact.isEmpty()) return Tone.UNKNOWN
    return when {
        exact.any { it.verification?.matched == true } -> Tone.SUCCESS
        exact.any { it.state in setOf(UploadJobState.QUEUED, UploadJobState.SCANNING, UploadJobState.UPLOADING) } ->
            Tone.PENDING
        exact.any { it.state == UploadJobState.COMPLETED && it.verification?.matched == null } ->
            Tone.PENDING
        else -> Tone.WARNING
    }
}

private fun finishedValue(tone: Tone): String = when (tone) {
    Tone.SUCCESS -> "종료"
    Tone.PENDING -> "종료 확인 중"
    Tone.WARNING, Tone.ERROR -> "다시 확인"
    else -> "미확인"
}

private fun taskFinishedValue(tone: Tone): String = when (tone) {
    Tone.SUCCESS -> "종료"
    Tone.PENDING -> "진행 중"
    Tone.INFO -> "중지됨"
    Tone.WARNING, Tone.ERROR -> "비정상 종료"
    else -> "미확인"
}

private fun storedValue(tone: Tone): String = when (tone) {
    Tone.SUCCESS -> "저장 확인"
    Tone.PENDING -> "확인 중"
    Tone.WARNING, Tone.ERROR -> "다시 확인"
    else -> "미확인"
}

private fun serverValue(tone: Tone): String = when (tone) {
    Tone.SUCCESS -> "수신 확인"
    Tone.PENDING -> "전송 중"
    Tone.WARNING, Tone.ERROR -> "다시 확인"
    else -> "미확인"
}
