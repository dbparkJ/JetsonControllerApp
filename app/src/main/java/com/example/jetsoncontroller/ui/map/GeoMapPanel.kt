package com.example.jetsoncontroller.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.field.runQualityPresentation
import com.example.jetsoncontroller.ui.zone.runPresentation
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeoMapPanel(
    mode: GeoMapMode, data: GeoMapData, gnss: GnssSensorStatus,
    expanded: Boolean, onToggle: () -> Unit,
    phoneShown: Boolean, onPhoneShown: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    runElapsed: String? = null,
    onStopRequest: (() -> Unit)? = null,
    stopEnabled: Boolean = true,
    stopBusy: Boolean = false,
    stopError: String? = null,
    stopOpener: androidx.compose.ui.focus.FocusRequester? = null,
    phoneMessage: String? = null,
    phoneRemedy: Pair<String, () -> Unit>? = null,
    developerMode: Boolean = false,
    onPeekHeight: (Int) -> Unit = {},
    deviceName: String = "장치"
) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val density = LocalDensity.current
    var qualityDetails by remember { mutableStateOf(false) }
    val qualityFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val presentation = runQualityPresentation(data.quality)
    val stop: @Composable () -> Unit = {
        if (mode == GeoMapMode.RUN && onStopRequest != null) {
            GeoButton("수집 종료", onStopRequest, Modifier.fillMaxWidth().then(if (stopOpener != null) Modifier.focusRequester(stopOpener) else Modifier), GeoButtonStyle.DangerOutline,
                enabled = stopEnabled, busy = stopBusy, busyLabel = "종료 확인 중", actionArea = true,
                disabledReason = if (!stopEnabled) "수집 상태와 연결을 다시 확인하세요." else null)
        }
    }
    if (qualityDetails) {
        GeoSheet("위치 품질", { qualityDetails = false }, kind = GeoSheetKind.INFO, openerFocusRequester = qualityFocus) {
            if (presentation.slices.isEmpty()) SelectionContainer { GeoInlineNote(presentation.detail) }
            GeoPropertyList(presentation.slices.map { it.label }) {
                presentation.slices.forEach { GeoPropertyRow(it.label, mapObservationDuration(it.durationMillis), numeric = true) }
            }
            SelectionContainer {
                Column { presentation.problems.forEach { GeoInlineNote(it) } }
            }
            if (data.geometry?.hasRange == true) {
                GeoSection("수집 구역", first = true, modifier = Modifier.padding(top = 24.dp)) {
                    val caption = mapResultCaption(data.zone)
                    if (caption.isNotEmpty()) SelectionContainer { Text(caption, style = type.body, color = c.text) }
                    if (data.zone.phoneObserved) Text("구역 밖 기록은 관찰한 시간 기준입니다.", style = type.caption, color = c.textSecondary)
                }
            }
        }
    }
    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().onSizeChanged {
            if (!expanded) onPeekHeight(with(density) { it.height.toDp().value.toInt() })
        }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(deviceName, Modifier.weight(1f), style = type.sectionTitle, color = c.text)
                GeoIconButton(if (expanded) GeoIcons.collapsePanel else GeoIcons.expandPanel,
                    if (expanded) "패널 접기" else "패널 펼치기", onToggle,
                    Modifier.semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" })
            }
            val badge = zoneBadge(mode, data)
            if (badge != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GeoBadge(badge.first, badge.second, variant = GeoBadgeVariant.INLINE)
                    Text(presentation.headline, style = type.body, color = c.text)
                }
            } else if (mode == GeoMapMode.ZONE) {
                Text(mapPositionSentence(data), style = type.body, color = c.text)
            } else Text(presentation.headline, style = type.body, color = c.text)
            data.geometry?.summary?.takeIf { it.isNotEmpty() }?.let { Text(it, style = type.caption, color = c.textSecondary) }
            if (mode == GeoMapMode.RUN) {
                GeoStatusStrip(listOfNotNull(data.zone.runPresentation(deviceName).strip))
                Text("수집 중 · ${runElapsed ?: "미확인"}", style = type.body, color = c.text)
                stopError?.let { GeoInlineNote(it) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    val details: @Composable () -> Unit = {
        Column {
            GeoPropertyList(listOf("좌표", "마지막 위치")) {
                GeoPropertyRow("좌표", Formats.coordinate(data.device?.latitude, data.device?.longitude))
                GeoPropertyRow("마지막 위치", mapSampleTime(gnss.lastSampleAtEpochMillis))
            }
            GeoRowList(GeoRowTrailing.None) { GeoSwitchRow("휴대폰 위치 표시", phoneShown, onPhoneShown) }
            phoneMessage?.let { GeoInlineNote(it) }
            phoneRemedy?.let { GeoButton(it.first, it.second, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start) }
            if (data.geometryChanged) GeoInlineNote("수집 구역이 바뀌었습니다. 이 수집은 시작할 때의 구역으로 표시합니다.")
            data.geometry?.let {
                if (it.state == GeoGeometryState.RANGE_PENDING || it.state == GeoGeometryState.INVALID) GeoInlineNote(mapPositionSentence(data))
            }
            GeoSection("범례", first = true, modifier = Modifier.padding(top = 24.dp)) { GeoMapLegend() }
            GeoSection("장치 위치") {
                GeoFactRow("위성", gnss.satellites?.let { "${it}개" } ?: "미확인")
                GeoFactRow("고도", gnss.altitudeM?.takeIf { it.isFinite() }?.let { "${String.format(Locale.US, "%.1f", it)} m" } ?: "미확인")
                SelectionContainer { Text(presentation.detail, style = type.caption, color = c.textSecondary) }
                GeoRowList(GeoRowTrailing.None) {
                    GeoRow("위치 품질 자세히", { qualityDetails = true }, modifier = Modifier.focusRequester(qualityFocus),
                        leading = { GeoIconTile(GeoIcons.myLocation) }, divider = false)
                }
            }
            if (data.geometry?.hasRange == true && mode == GeoMapMode.ROUTE) {
                val caption = mapResultCaption(data.zone)
                if (caption.isNotEmpty()) GeoInlineNote(caption)
                if (data.zone.phoneObserved) Text("관찰한 시간 기준", style = type.caption, color = c.textSecondary)
            }
            GeoTechInfo(listOfNotNull(data.geometry?.invalidReason?.let { GeoTechnicalValue("수집 구역", it) }),
                developerMode, com.example.jetsoncontroller.ui.dev.rememberTechnicalInfo().value)
            Spacer(Modifier.height(16.dp))
        }
    }
    Column(modifier.background(c.surface).semantics { paneTitle = "지도 패널" }
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).geoContainScroll().verticalScroll(rememberScrollState())) { header(); details() }
        stop()
    }
}

@Composable
private fun GeoMapLegend() {
    val type = LocalGeoType.current
    val c = LocalGeoColors.current
    val entries = listOf("수집 구역" to MapBlue, "경로" to MapBlue, "영역" to MapBlue, "지점" to MapBlue,
        "정밀 위치" to MapPrecise, "대략 위치" to MapApproximate, "위치 미확인" to MapUnknown,
        "관찰하지 못한 시간" to MapGap, "장치" to MapDevice)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val columns = mapLegendColumns(maxWidth.value, LocalDensity.current.fontScale)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.withIndex().chunked(columns).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (index, entry) ->
                    val (label, color) = entry
                    Row(Modifier.weight(1f).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Canvas(Modifier.size(24.dp)) {
                            val ink = Color(android.graphics.Color.parseColor(color))
                            when (index) {
                                3, 8 -> {
                                    drawCircle(Color.White, 8.dp.toPx()); drawCircle(ink, if (index == 3) 5.dp.toPx() else 7.dp.toPx())
                                }
                                7 -> drawCircle(ink, 4.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                                else -> {
                                    val start = Offset(2.dp.toPx(), size.height / 2); val end = Offset(size.width - 2.dp.toPx(), size.height / 2)
                                    drawLine(Color.White, start, end, 7.dp.toPx(), StrokeCap.Round)
                                    val dash = when (index) { 0 -> floatArrayOf(6.dp.toPx(), 4.dp.toPx()); 5 -> floatArrayOf(8.dp.toPx(), 4.dp.toPx()); 6 -> floatArrayOf(2.dp.toPx(), 4.dp.toPx()); else -> null }
                                    drawLine(ink, start, end, if (index < 4) 2.dp.toPx() else 4.dp.toPx(),
                                        if (dash == null) StrokeCap.Round else StrokeCap.Butt, pathEffect = dash?.let { PathEffect.dashPathEffect(it) })
                                }
                            }
                        }
                        Text(label, color = c.text, style = type.body, modifier = Modifier.weight(1f))
                    }
                }
                if (pair.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
    }
}

internal fun mapSampleTime(epochMillis: Long?): String = epochMillis?.let {
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREA).withZone(ZoneId.of("Asia/Seoul")).format(Instant.ofEpochMilli(it))
} ?: "미확인"

internal fun mapObservationDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    return when {
        seconds < 60 -> "${seconds}초"
        seconds % 60 == 0L -> "${seconds / 60}분"
        else -> "${seconds / 60}분 ${seconds % 60}초"
    }
}
