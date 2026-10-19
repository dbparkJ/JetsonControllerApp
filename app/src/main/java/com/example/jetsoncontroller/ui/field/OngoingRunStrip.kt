package com.example.jetsoncontroller.ui.field

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.StopTarget
import com.example.jetsoncontroller.ui.components.geo.GeoOverlayAction
import com.example.jetsoncontroller.ui.components.geo.GeoStatusStrip
import com.example.jetsoncontroller.ui.components.geo.GeoStatusStripState
import com.example.jetsoncontroller.ui.components.geo.GeoStripKind

/** Supplies a candidate to the shared strip arbiter; the work root uses its Focus block. */
internal fun ongoingRunStripState(
    deviceId: String?,
    deviceName: String,
    run: PipelineRun?,
    onOpenRun: (StopTarget) -> Unit,
    elapsedLabel: String? = null,
    workRoot: Boolean = false,
    awaitingStart: Boolean = false
): GeoStatusStripState? {
    if (workRoot || awaitingStart || run?.active != true || deviceName.isBlank() ||
        !run.deviceId.equals(deviceId, true)) return null
    val target = runTarget(run)
    val elapsed = elapsedLabel ?: verifiedRunTelemetry(run)?.durationMillis?.let(::runDurationLabel)
    return GeoStatusStripState(
        kind = GeoStripKind.COLLECTING,
        deviceName = deviceName,
        message = listOfNotNull("${run.contextSnapshot.surveySectionLabel} 수집 중", elapsed).joinToString(" · "),
        actions = listOf(GeoOverlayAction("진행 보기", { onOpenRun(target) }))
    )
}

/** Roots and details pass their own device target and all other candidates here. */
@Composable
internal fun OngoingRunStrip(
    deviceId: String?,
    deviceName: String,
    run: PipelineRun?,
    onOpenRun: (StopTarget) -> Unit,
    modifier: Modifier = Modifier,
    candidates: List<GeoStatusStripState> = emptyList(),
    elapsedLabel: String? = null,
    workRoot: Boolean = false,
    awaitingStart: Boolean = false
) {
    GeoStatusStrip(candidates + listOfNotNull(ongoingRunStripState(
        deviceId, deviceName, run, onOpenRun, elapsedLabel, workRoot, awaitingStart
    )), modifier)
}
