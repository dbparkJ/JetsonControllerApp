package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.connection.DeviceConnectionUi
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.shape.CircleShape

/** GeoAppShell owns the system insets; this is only the measured title row. */
@Composable
internal fun DeviceTitleBar(
    title: String,
    onBack: (() -> Unit)? = null,
    root: Boolean = false,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = LocalGeoColors.current
    Row(modifier.fillMaxWidth().background(c.topBar).heightIn(min = if (root) 64.dp else 56.dp)
        .padding(start = if (onBack == null) LocalGeoWindow.current.gutterDp.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { GeoIconButton(GeoIcons.back, "뒤로", it, chrome = true) }
        Text(title, Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else 12.dp)
            .semantics { heading() }, style = if (root) LocalGeoType.current.heading else LocalGeoType.current.title, color = c.onTopBar)
        actions()
    }
}

@Composable
internal fun DevicePhoto(size: Dp) {
    Image(painterResource(R.drawable.geo_device), null, Modifier.size(size), contentScale = ContentScale.Fit)
}

@Composable
internal fun DeviceFigureGrid(figures: List<GeoFigure>) {
    if (figures.none { it.label == "메모리" }) {
        GeoFigureGrid(figures)
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = figureGridColumns(maxWidth.value, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            figures.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { figure ->
                        DeviceFigureCell(figure, Modifier.weight(1f))
                    }
                    if (columns == 2 && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DeviceFigureCell(figure: GeoFigure, modifier: Modifier) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val shown = deviceFigurePresentation(figure)
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (shown.warning) Icon(GeoIcons.warning, null, Modifier.size(16.dp), tint = c.warning)
            Text(figure.label, color = c.textSecondary, style = type.caption)
        }
        Spacer(Modifier.height(14.dp))
        Text(shown.value, Modifier.heightIn(min = 32.dp), color = if (shown.unknown) c.neutral else c.text,
            style = if (shown.unknown) type.title else type.figure)
        Text(shown.detail.orEmpty(), Modifier.heightIn(min = 20.dp),
            color = if (shown.warning) c.warning else c.textSecondary, style = type.caption)
        shown.staleDetail?.let { Text(it, color = c.textSecondary, style = type.caption) }
    }
}

@Composable
internal fun DeviceAlertButton(count: Int, onClick: () -> Unit) {
    Box {
        GeoIconButton(GeoIcons.bell, "알림 ${count}건", onClick, chrome = true)
        if (count > 0) Text(if (count > 99) "99+" else count.toString(),
            Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp)
                .clearAndSetSemantics {}
                .background(LocalGeoColors.current.attention, CircleShape).padding(horizontal = 5.dp),
            style = LocalGeoType.current.count, color = LocalGeoColors.current.onAttention)
    }
}

@Composable
internal fun rememberDeviceOverlayHost(selectedId: String?, open: Boolean, close: () -> Unit): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    var previous by remember { mutableStateOf(selectedId) }
    val latestOpen by rememberUpdatedState(open)
    val latestClose by rememberUpdatedState(close)
    LaunchedEffect(selectedId) {
        if (previous != selectedId) {
            previous = selectedId
            if (latestOpen) {
                latestClose()
                host.showGeoSnackbar("장치를 바꿔서 열려 있던 확인 창을 닫았습니다.")
            }
        }
    }
    return host
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DeviceIdentity(name: String, connection: DeviceConnectionUi, meta: String?, detail: Boolean = false,
    onClick: (() -> Unit)? = null) {
    val target = if (onClick == null) Modifier else Modifier.contentTarget(onClick = onClick)
    Row(Modifier.fillMaxWidth().then(target).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DevicePhoto(56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = if (detail) LocalGeoType.current.boardTitle else LocalGeoType.current.deviceName,
                    color = LocalGeoColors.current.text)
                GeoBadge(connection.label, connection.tone, Modifier.align(Alignment.CenterVertically))
            }
            meta?.let { Text(it, style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary) }
        }
        if (onClick != null) Icon(GeoIcons.chevronRight, null, Modifier.size(20.dp), tint = LocalGeoColors.current.textSubtle)
    }
}

@Composable
internal fun DeviceAttentionRow(title: String, meta: String, onOpen: () -> Unit, onHide: () -> Unit, divider: Boolean = true) {
    val c = LocalGeoColors.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalGeoType.current.button
    val actionWidth = maxOf(48.dp,
        with(density) { measurer.measure("숨기기", style, softWrap = false).size.width.toDp() } + 24.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (divider) Modifier.contentDivider() else Modifier)) {
        val body: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier.contentTarget(onClick = onOpen).heightIn(min = 72.dp).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(GeoIcons.warning, null, Modifier.size(20.dp), tint = c.warning)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = c.text, style = LocalGeoType.current.titleSm)
                    Text(meta, color = c.textSecondary, style = LocalGeoType.current.caption)
                }
            }
        }
        if (density.fontScale >= 1.5f || actionWidth > maxWidth * .45f) {
            Column(Modifier.fillMaxWidth()) {
                body(Modifier.fillMaxWidth())
                GeoButton("숨기기", onHide, Modifier.width(actionWidth).align(Alignment.End), GeoButtonStyle.Tertiary, edge = GeoButtonEdge.End)
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            body(Modifier.weight(1f))
            GeoButton("숨기기", onHide, Modifier.width(actionWidth), GeoButtonStyle.Tertiary, edge = GeoButtonEdge.End)
        }
    }
}

/** Disabled navigation keeps the row and its status badge, with no filled control background. */
@Composable
internal fun DeviceNavigationRow(
    title: String,
    onClick: () -> Unit,
    meta: String? = null,
    value: String? = null,
    badge: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    divider: Boolean = true
) {
    if (enabled) {
        GeoRow(title, onClick, meta = meta, value = value, badge = badge, divider = divider)
        return
    }
    require(!disabledReason.isNullOrBlank())
    val c = LocalGeoColors.current
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (divider) Modifier.contentDivider() else Modifier).semantics(mergeDescendants = true) { disabled() }) {
        val stacked = LocalDensity.current.fontScale >= 1.5f || maxWidth < 320.dp
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = c.disabledText, style = LocalGeoType.current.titleSm)
                Text(disabledReason, color = c.disabledText, style = LocalGeoType.current.caption)
                if (stacked) badge?.invoke()
            }
            if (!stacked) badge?.let {
                Box(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * .45f)) { it() }
            }
        }
    }
}

@Composable
internal fun DeviceConnectionChoice(title: String, icon: ImageVector, meta: String, onClick: () -> Unit,
    enabled: Boolean = true, disabledReason: String? = null) {
    require(enabled || !disabledReason.isNullOrBlank())
    val c = LocalGeoColors.current
    val target = if (enabled) Modifier.contentTarget(onClick = onClick) else Modifier.semantics { disabled() }
    Row(Modifier.fillMaxWidth().then(target).contentDivider().semantics(mergeDescendants = true) {}
        .heightIn(min = 72.dp).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) c.textSecondary else c.disabledText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = LocalGeoType.current.titleSm, color = if (enabled) c.text else c.disabledText)
            Text(if (enabled) meta else disabledReason.orEmpty(), style = LocalGeoType.current.caption,
                color = if (enabled) c.textSecondary else c.disabledText)
        }
    }
}

@Composable
internal fun DevicePowerButtons(enabled: Boolean, onReboot: () -> Unit, onShutdown: () -> Unit) {
    val measure = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalGeoType.current.button
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val half = (constraints.maxWidth - with(density) { 8.dp.roundToPx() }) / 2
        val fits = listOf("재부팅", "전원 종료").all {
            measure.measure(it, style, softWrap = false).size.width + with(density) { 32.dp.roundToPx() } <= half
        }
        if (fits) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GeoButton("재부팅", onReboot, Modifier.weight(1f), GeoButtonStyle.DangerOutline, enabled, actionArea = true)
            GeoButton("전원 종료", onShutdown, Modifier.weight(1f), GeoButtonStyle.DangerOutline, enabled, actionArea = true)
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GeoButton("재부팅", onReboot, style = GeoButtonStyle.DangerOutline, enabled = enabled, actionArea = true)
            GeoButton("전원 종료", onShutdown, style = GeoButtonStyle.DangerOutline, enabled = enabled, actionArea = true)
        }
    }
}

internal fun statusChipText(connection: DeviceConnectionUi, lastSeenText: String?, online: Boolean, freshness: String?): String = when {
    online && freshness != null -> "${connection.label} · $freshness"
    !online && lastSeenText != null -> "${connection.label} · 마지막 응답 $lastSeenText"
    else -> connection.label
}
