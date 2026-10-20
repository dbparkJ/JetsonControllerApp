package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Tone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

enum class GeoStartPhase { CHECKING, PASSED, FAILED }

data class GeoStartCheck(
    val label: String,
    val phase: GeoStartPhase,
    val value: String? = null,
    val reason: String? = null,
    val remedy: GeoOverlayAction? = null
) {
    init { require(phase != GeoStartPhase.FAILED || !reason.isNullOrBlank()) }
}

data class GeoStartSheetState(
    val sectionName: String,
    val projectName: String,
    val deviceName: String,
    val zoneLine: String,
    val phase: GeoStartPhase,
    val checks: List<GeoStartCheck>,
    val ticketLinked: Boolean = false,
    val ticketChanged: Boolean = false,
    val insideZone: Boolean = false
)

data class GeoStartSheetRules(
    val canStart: Boolean,
    val busy: Boolean,
    val primaryLabel: String,
    val blockingReason: String?,
    val showRecheck: Boolean
)

fun startSheetRules(state: GeoStartSheetState): GeoStartSheetRules {
    val failed = state.phase == GeoStartPhase.FAILED || state.checks.any { it.phase == GeoStartPhase.FAILED }
    val allPassed = state.checks.isNotEmpty() && state.checks.all { it.phase == GeoStartPhase.PASSED }
    val busy = !state.ticketChanged && (state.phase == GeoStartPhase.CHECKING || (!failed && !allPassed))
    val reason = when {
        state.ticketChanged -> "GEO& Work에서 이 구간이 바뀌었습니다. 다시 확인하세요."
        failed -> "점검을 통과해야 시작할 수 있습니다."
        else -> null
    }
    return GeoStartSheetRules(
        canStart = state.phase == GeoStartPhase.PASSED && allPassed && !state.ticketChanged,
        busy = busy,
        primaryLabel = if (busy) "점검하는 중" else "수집 시작",
        blockingReason = reason,
        showRecheck = !busy && (failed || state.ticketChanged)
    )
}

@Composable
fun GeoStartSheet(
    state: GeoStartSheetState,
    onStart: () -> Unit,
    onRecheck: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    openerFocusRequester: FocusRequester? = null
) {
    val rules = startSheetRules(state)
    val t = LocalGeoColors.current
    GeoSheet(
        title = "${state.sectionName} 수집을 시작할까요?",
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        kind = GeoSheetKind.FORM,
        openerFocusRequester = openerFocusRequester,
        footer = {
            rules.blockingReason?.let {
                Text(it, color = t.textSecondary, style = LocalGeoType.current.body)
                Spacer(Modifier.height(8.dp))
            }
            GeoSheetFooter(
                GeoOverlayAction(rules.primaryLabel, { if (rules.canStart) onStart() }, enabled = rules.canStart, busy = rules.busy),
                GeoOverlayAction("취소", onDismissRequest)
            )
        }
    ) {
        if (state.ticketLinked) {
            Text("${state.sectionName} · ${state.projectName} · ${state.deviceName}", color = t.textSecondary, style = LocalGeoType.current.body)
        } else {
            StartFact("장치", state.deviceName)
            StartFact("프로젝트", state.projectName)
            StartFact("구간", state.sectionName)
        }
        Spacer(Modifier.height(8.dp))
        if (state.insideZone) {
            StartZoneBadge(state.zoneLine)
        } else {
            Text(state.zoneLine, color = t.text, style = LocalGeoType.current.body)
        }
        Spacer(Modifier.height(16.dp))
        Text("시작 전 점검", color = t.text, style = LocalGeoType.current.titleSm, modifier = Modifier.semantics { heading() })
        // Never sort by result: a failure stays under the same check as results arrive.
        state.checks.forEach { StartCheckRow(it) }
        if (rules.showRecheck) GeoOverlayButton(GeoOverlayAction("다시 점검", onRecheck))
        if (state.ticketLinked) {
            Spacer(Modifier.height(12.dp))
            Text("GEO& Work 상태가 '진행 중'으로 바뀝니다.", color = t.text, style = LocalGeoType.current.body)
        }
        Spacer(Modifier.height(4.dp))
        Text("시작하면 이 수집의 프로젝트와 구간을 바꿀 수 없습니다.", color = t.text, style = LocalGeoType.current.body)
    }
}

@Composable
private fun StartZoneBadge(label: String) {
    GeoBadge(label, Tone.SUCCESS)
}

@Composable
private fun StartFact(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = LocalGeoColors.current.textSecondary, style = LocalGeoType.current.body)
        Text(value, color = LocalGeoColors.current.text, style = LocalGeoType.current.body)
    }
}

@Composable
private fun StartCheckRow(check: GeoStartCheck) {
    val t = LocalGeoColors.current
    val tone = when (check.phase) {
        GeoStartPhase.CHECKING -> Tone.PENDING
        GeoStartPhase.PASSED -> Tone.SUCCESS
        GeoStartPhase.FAILED -> Tone.ERROR
    }
    val value = when (check.phase) {
        GeoStartPhase.CHECKING -> "확인 중"
        GeoStartPhase.PASSED -> check.value ?: "완료"
        GeoStartPhase.FAILED -> check.value ?: "실패"
    }
    val measure = rememberTextMeasurer()
    val titleStyle = LocalGeoType.current.titleSm
    val bodyStyle = LocalGeoType.current.body
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GeoToneIcon(tone, Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val needed = measure.measure(check.label, titleStyle, softWrap = false, maxLines = 1).size.width +
                    measure.measure(value, bodyStyle, softWrap = false, maxLines = 1).size.width + with(density) { 12.dp.roundToPx() }
                if (needed <= constraints.maxWidth) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(check.label, Modifier.weight(1f), color = t.text, style = titleStyle)
                        Text(value, color = t.tone(tone), style = bodyStyle)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(check.label, color = t.text, style = titleStyle)
                        Text(value, color = t.tone(tone), style = bodyStyle)
                    }
                }
            }
            if (check.phase == GeoStartPhase.FAILED) {
                Text(check.reason.orEmpty(), color = t.textSecondary, style = bodyStyle)
                check.remedy?.let { remedy ->
                    GeoOverlayButton(remedy)
                    if (!remedy.enabled && remedy.disabledReason != null) Text(remedy.disabledReason, color = t.textSecondary, style = bodyStyle)
                }
            }
        }
    }
}
