package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
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
internal fun DeviceIdentity(name: String, connection: DeviceConnectionUi, meta: String?, detail: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DevicePhoto(56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = if (detail) LocalGeoType.current.boardTitle else LocalGeoType.current.titleSm,
                color = LocalGeoColors.current.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GeoBadge(connection.label, connection.tone)
                meta?.let { Text(it, style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary) }
            }
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
