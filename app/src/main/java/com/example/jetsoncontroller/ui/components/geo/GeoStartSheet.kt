package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Tone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.geoCardShadow

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
    openerFocusRequester: FocusRequester? = null,
    optionalChecks: List<GeoStartCheck> = emptyList(),
    mapPreview: (@Composable () -> Unit)? = null
) {
    val rules = startSheetRules(state)
    val t = LocalGeoColors.current
    CompositionLocalProvider(LocalGeoColors provides t.copy(surface = t.canvas)) {
    GeoSheet(
        title = "${state.sectionName} 수집을 시작할까요?",
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        kind = GeoSheetKind.FORM,
        openerFocusRequester = openerFocusRequester,
        footer = {
          CompositionLocalProvider(LocalGeoColors provides t) {
            if (rules.showRecheck) {
                GeoButton("다시 점검", onRecheck, Modifier.fillMaxWidth(),
                    style = GeoButtonStyle.Secondary, icon = GeoIcons.refresh)
                Spacer(Modifier.height(8.dp))
            }
            rules.blockingReason?.let {
                Text(it, color = t.textSecondary, style = LocalGeoType.current.caption)
                Spacer(Modifier.height(8.dp))
            }
            GeoSheetFooter(
                GeoOverlayAction(rules.primaryLabel, { if (rules.canStart) onStart() }, enabled = rules.canStart, busy = rules.busy),
                GeoOverlayAction("취소", onDismissRequest)
            )
          }
        }
    ) {
      CompositionLocalProvider(LocalGeoColors provides t) {
        mapPreview?.let {
            it()
            Spacer(Modifier.height(16.dp))
        }
        if (state.ticketLinked) {
            Text("${state.sectionName} · ${state.projectName} · ${state.deviceName}", color = t.textSecondary, style = LocalGeoType.current.supporting)
            Spacer(Modifier.height(12.dp))
        }
        GeoPropertyList(listOf("장치", "프로젝트", "구간")) {
            GeoFactRow("장치", state.deviceName)
            GeoFactRow("프로젝트", state.projectName)
            GeoFactRow("구간", state.sectionName)
        }
        Spacer(Modifier.height(8.dp))
        if (state.insideZone) {
            StartZoneBadge(state.zoneLine)
        } else {
            Text(state.zoneLine, color = t.text, style = LocalGeoType.current.body)
        }
        GeoSection("시작 전 점검") {
            val failures = state.checks.filter { it.phase == GeoStartPhase.FAILED }
            failures.forEach { StartFailureCard(it, Tone.ERROR) }
            optionalChecks.filter { it.phase == GeoStartPhase.FAILED }.forEach { StartFailureCard(it, Tone.WARNING) }
            if (state.checks.isNotEmpty() || optionalChecks.isNotEmpty()) Column(
                Modifier.fillMaxWidth().geoCardShadow().clip(GeoShape.card).background(t.surface).padding(16.dp)) {
                GeoRowList(GeoRowTrailing.Status) {
                    // Keep the check order stable as individual results arrive.
                    (state.checks + optionalChecks).forEachIndexed { index, check ->
                        val tone = when (check.phase) {
                            GeoStartPhase.CHECKING -> Tone.PENDING
                            GeoStartPhase.PASSED -> Tone.SUCCESS
                            GeoStartPhase.FAILED -> if (check in optionalChecks) Tone.WARNING else Tone.ERROR
                        }
                        GeoPreparationStatusRow(check.label, check.value, when (check.phase) {
                            GeoStartPhase.CHECKING -> "확인 중"
                            GeoStartPhase.PASSED -> "확인됨"
                            GeoStartPhase.FAILED -> "확인 필요"
                        }, tone, divider = index < state.checks.size + optionalChecks.size - 1)
                    }
                }
            }
        }
        if (state.ticketLinked) {
            Spacer(Modifier.height(12.dp))
            Text("GEO& Work 상태가 '진행 중'으로 바뀝니다.", color = t.text, style = LocalGeoType.current.body)
        }
        Spacer(Modifier.height(4.dp))
        Text("시작하면 이 수집의 프로젝트와 구간을 바꿀 수 없습니다.", color = t.text, style = LocalGeoType.current.body)
      }
    }
    }
}

@Composable
private fun StartZoneBadge(label: String) {
    GeoBadge(label, Tone.SUCCESS, variant = GeoBadgeVariant.INLINE)
}

@Composable
private fun StartFailureCard(check: GeoStartCheck, tone: Tone) {
    val colors = LocalGeoColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).background(colors.soft(tone), GeoShape.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(GeoIcons.warning, null, Modifier.size(24.dp), tint = colors.tone(tone))
            Text(check.label, color = colors.tone(tone), style = LocalGeoType.current.titleSm)
        }
        Text(check.reason.orEmpty(), color = colors.text, style = LocalGeoType.current.body)
        check.value?.let { Text(it, color = colors.tone(tone), style = LocalGeoType.current.titleSm) }
        check.remedy?.let { remedy ->
            GeoButton(remedy.label, remedy.onClick, style = GeoButtonStyle.Tertiary,
                edge = GeoButtonEdge.Start, enabled = remedy.enabled, busy = remedy.busy,
                disabledReason = remedy.disabledReason)
        }
    }
}

/** The read-only form of the R7 row; only an existing remedy creates a tap target. */
@Composable
fun GeoPreparationStatusRow(
    title: String,
    detail: String?,
    status: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    icon: ImageVector = preparationRowIcon(title),
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    numericValue: Boolean = false
) {
    if (onClick != null) {
        GeoRow(title, onClick, modifier, meta = detail,
            leading = { GeoIconTile(icon) }, badge = { GeoBadge(status, tone) }, divider = divider)
        return
    }
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val density = LocalDensity.current
    val firstLine = with(density) { type.titleSm.lineHeight.toDp() }
    BoxWithConstraints(modifier.fillMaxWidth().then(if (divider) Modifier.contentDivider() else Modifier)) {
        val stacked = density.fontScale >= 1.5f || maxWidth < 280.dp
        val statusCell: @Composable () -> Unit = {
            Box(Modifier.width(72.dp).heightIn(min = firstLine), contentAlignment = Alignment.TopEnd) {
                androidx.compose.runtime.CompositionLocalProvider(LocalGeoRowStatus provides true) {
                    if (numericValue) Text(status, style = type.supporting.copy(fontFeatureSettings = "tnum"),
                        color = colors.text, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    else GeoBadge(status, tone)
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(vertical = 16.dp)
            .semantics(mergeDescendants = true) {}) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                GeoIconTile(icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = type.titleSm, color = colors.text)
                    detail?.takeIf { it != status }?.let {
                        Text(it, style = type.supporting, color = colors.textSecondary)
                    }
                }
                if (!stacked) statusCell()
                Spacer(Modifier.width(24.dp))
            }
            if (stacked) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.width(40.dp))
                    Spacer(Modifier.weight(1f))
                    statusCell()
                    Spacer(Modifier.width(24.dp))
                }
            }
        }
    }
}

private fun preparationRowIcon(title: String): ImageVector = when (title) {
    "연결" -> GeoIcons.wifi
    "저장 공간" -> GeoIcons.folder
    "위치 품질" -> GeoIcons.myLocation
    "카메라" -> GeoIcons.camera
    "보정 정보 중계" -> GeoIcons.link
    "시간", "장치 시간" -> GeoIcons.pending
    else -> GeoIcons.tabDevice
}
