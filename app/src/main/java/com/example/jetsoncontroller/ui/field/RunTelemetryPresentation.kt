package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.RunTelemetry
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
