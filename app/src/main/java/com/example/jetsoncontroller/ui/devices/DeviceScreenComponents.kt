package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.geoCardShadow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.connection.DeviceConnectionUi
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
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

/** One R7 surface for lists and actual warning or danger groups. */
@Composable
internal fun DeviceSurface(
    modifier: Modifier = Modifier,
    tone: com.example.jetsoncontroller.ui.model.Tone? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalGeoColors.current
    val fill = when (tone) {
        com.example.jetsoncontroller.ui.model.Tone.WARNING -> c.warningSoft
        com.example.jetsoncontroller.ui.model.Tone.ERROR -> c.dangerSoft
        else -> c.surface
    }
    Column(modifier.fillMaxWidth().geoCardShadow().clip(GeoShape.card)
        .background(fill).padding(contentPadding), content = content)
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
internal fun DeviceAttentionRow(title: String, meta: String, onOpen: () -> Unit, onHide: () -> Unit, divider: Boolean = true) {
    GeoActionRow(title, "숨기기", onOpen, onHide, meta = meta,
        leading = { GeoIconTile(GeoIcons.warning, tone = com.example.jetsoncontroller.ui.model.Tone.WARNING, transparent = true) },
        tone = com.example.jetsoncontroller.ui.model.Tone.WARNING,
        divider = divider)
}

@Composable
internal fun DeviceNavigationRow(
    title: String,
    onClick: () -> Unit,
    meta: String? = null,
    value: String? = null,
    badge: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    divider: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    GeoRow(title, onClick, meta = meta, value = value, badge = badge, leading = leading,
        enabled = enabled, disabledReason = disabledReason, divider = divider)
}

@Composable
internal fun DeviceConnectionChoice(title: String, icon: ImageVector, meta: String, onClick: () -> Unit,
    enabled: Boolean = true, disabledReason: String? = null) {
    GeoRow(title, onClick, meta = meta, leading = { GeoIconTile(icon) },
        enabled = enabled, disabledReason = disabledReason)
}

@Composable
internal fun DevicePowerButtons(enabled: Boolean, onReboot: () -> Unit, onShutdown: () -> Unit) {
    val c = LocalGeoColors.current
    CompositionLocalProvider(LocalGeoColors provides c.copy(dangerSoft = c.surface)) {
        GeoButtonActions(listOf(
            GeoButtonAction("재부팅", onReboot, GeoButtonStyle.DangerOutline, enabled),
            GeoButtonAction("전원 종료", onShutdown, GeoButtonStyle.DangerOutline, enabled)
        ))
    }
}

internal fun statusChipText(connection: DeviceConnectionUi, lastSeenText: String?, online: Boolean, freshness: String?): String = when {
    online && freshness != null -> "${connection.label} · $freshness"
    !online && lastSeenText != null -> "${connection.label} · 마지막 응답 $lastSeenText"
    else -> connection.label
}
