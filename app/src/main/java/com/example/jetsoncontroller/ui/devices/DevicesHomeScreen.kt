package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.data.devices.deviceSnapshotKey
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.alerts.AlertIconButton
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.GeoLogo
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.components.statusVisuals
import com.example.jetsoncontroller.ui.connection.DeviceConnectionUi
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton

/** Navy is reserved for the next-action band of the current device (plan §시각 규칙). */
private val NextActionNavy = Color(0xFF1A2147)
private val NextActionNavyMuted = Color(0xFFC9D0E6)
private val SurfaceShape = RoundedCornerShape(12.dp)
private val ControlShape = RoundedCornerShape(8.dp)

/**
 * The 장치 root: the start destination of the app.
 *
 * It shows the current device with its one next action, the other registered devices from
 * their last known state, and at most one item in 확인할 일. With no device registered it
 * shows the first-run card instead. Every state comes from the caller; nothing here starts
 * a connection or navigates by itself.
 */
@Composable
internal fun DevicesHomeScreen(
    registeredDevices: List<RegisteredDevice>,
    selectedDeviceId: String?,
    currentDashboard: DashboardUiState,
    snapshots: Map<String, DeviceSnapshot>,
    availableDeviceIds: Set<String>,
    connectingDeviceId: String?,
    connectFailedDeviceId: String?,
    runActive: Boolean,
    attention: DeviceAttention?,
    unreadAlertCount: Int,
    nowEpochMillis: Long,
    developerModeEnabled: Boolean,
    onSectionSelected: (ControlSection) -> Unit,
    onAlertsClick: () -> Unit,
    onAddDevice: () -> Unit,
    onOtherWays: () -> Unit,
    onOpenDevice: () -> Unit,
    onReconnect: () -> Unit,
    onStartCollection: () -> Unit,
    onViewRun: () -> Unit,
    onSelectDevice: (RegisteredDevice) -> Unit,
    onAttentionClick: () -> Unit,
    onSameNetwork: () -> Unit,
    onDirectConnect: () -> Unit,
    onQrRegister: () -> Unit,
    onSaveDiagnostics: () -> Unit
) {
    val c = LocalGeoColors.current
    val current = registeredDevices.firstOrNull {
        it.deviceId.equals(selectedDeviceId, ignoreCase = true)
    } ?: registeredDevices.firstOrNull()
    val currentIsSelected = current != null &&
        current.deviceId.equals(selectedDeviceId, ignoreCase = true)
    val others = registeredDevices.filterNot {
        it.deviceId.equals(current?.deviceId, ignoreCase = true)
    }
    val online = currentIsSelected && currentDashboard.isOnline
    val statusIsCurrent = online && currentDashboard.statusFreshness == StatusFreshness.CURRENT
    val currentSnapshot = current?.let { snapshots[deviceSnapshotKey(it.deviceId)] }
    val currentLastSeen = currentSnapshot?.lastSeenAtEpochMillis?.let {
        formatSeenAt(it, nowEpochMillis)
    }
    val currentConnection = current?.let {
        deviceConnectionUi(
            isOnline = online,
            transportType = currentDashboard.transportType.takeIf { online },
            checking = it.deviceId.equals(connectingDeviceId, ignoreCase = true),
            available = it.deviceId.lowercase() in availableDeviceIds,
            connectFailed = it.deviceId.equals(connectFailedDeviceId, ignoreCase = true),
            lastSeenText = currentLastSeen
        )
    }
    val nextAction = deviceNextAction(
        hasDevice = current != null,
        online = online,
        runActive = runActive
    )
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            DevicesAppBar(
                unreadAlertCount = unreadAlertCount,
                onAlertsClick = onAlertsClick,
                onAddDevice = onAddDevice
            )
        },
        bottomBar = { ControlNavigationBar(ControlSection.DEVICES, onSectionSelected) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = GeoSpace.gutter,
                end = GeoSpace.gutter,
                top = GeoSpace.xs,
                bottom = GeoSpace.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
        ) {
            if (current == null || currentConnection == null) {
                item {
                    FirstRunCard(onAddDevice = onAddDevice, onOtherWays = onOtherWays)
                }
            } else {
                val figures = deviceFigures(
                    live = currentDashboard.status.takeIf { statusIsCurrent },
                    snapshot = currentSnapshot
                )
                val freshness = if (statusIsCurrent) {
                    formatFreshness(currentDashboard.statusAgeSeconds)
                } else {
                    null
                }
                item {
                    CurrentDeviceCard(
                        device = current,
                        connection = currentConnection,
                        lastSeenText = currentLastSeen,
                        freshness = freshness,
                        figures = figures,
                        runActive = runActive,
                        online = online,
                        nextAction = nextAction,
                        onOpen = onOpenDevice,
                        onReconnect = onReconnect,
                        onConnectMethods = { sheetOpen = true },
                        onStartCollection = onStartCollection,
                        onViewRun = onViewRun
                    )
                }
                if (others.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                            Text("다른 장치", style = MaterialTheme.typography.titleMedium)
                            others.forEach { device ->
                                val connection = deviceConnectionUi(
                                    isOnline = false,
                                    transportType = null,
                                    checking = device.deviceId.equals(connectingDeviceId, true),
                                    available = device.deviceId.lowercase() in availableDeviceIds,
                                    connectFailed = device.deviceId.equals(connectFailedDeviceId, true),
                                    lastSeenText = snapshots[deviceSnapshotKey(device.deviceId)]
                                        ?.lastSeenAtEpochMillis
                                        ?.let { formatSeenAt(it, nowEpochMillis) }
                                )
                                OtherDeviceRow(
                                    device = device,
                                    connection = connection,
                                    onClick = { onSelectDevice(device) }
                                )
                            }
                        }
                    }
                }
                attention?.let { item { AttentionRow(it, onClick = onAttentionClick) } }
            }
        }
    }

    if (sheetOpen && current != null && currentConnection != null) {
        ConnectMethodSheet(
            deviceName = current.deviceName,
            connection = currentConnection,
            lastSeenText = currentLastSeen,
            developerModeEnabled = developerModeEnabled,
            onDismiss = { sheetOpen = false },
            onSameNetwork = { sheetOpen = false; onSameNetwork() },
            onDirectConnect = { sheetOpen = false; onDirectConnect() },
            onQrRegister = { sheetOpen = false; onQrRegister() },
            onSaveDiagnostics = { sheetOpen = false; onSaveDiagnostics() }
        )
    }
}

@Composable
private fun DevicesAppBar(
    unreadAlertCount: Int,
    onAlertsClick: () -> Unit,
    onAddDevice: () -> Unit
) {
    val c = LocalGeoColors.current
    Surface(color = c.canvas, contentColor = c.ink) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = GeoSpace.gutter, end = GeoSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GeoLogo(Modifier.width(92.dp), backgroundColor = c.canvas)
            Spacer(Modifier.weight(1f))
            AlertIconButton(unreadAlertCount, onAlertsClick)
            IconButton(onClick = onAddDevice) {
                Icon(Icons.Default.Add, contentDescription = "장치 추가")
            }
        }
    }
}

@Composable
private fun FirstRunCard(onAddDevice: () -> Unit, onOtherWays: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(color = c.surface, contentColor = c.ink, shape = SurfaceShape, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(GeoSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Image(
                painter = painterResource(R.drawable.geo_device),
                contentDescription = "GEO& 도로관리장치",
                modifier = Modifier.size(132.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                "등록된 장치가 없습니다",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                "QR 코드를 스캔하면 장치를 이 휴대폰에 등록합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddDevice,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = ControlShape
            ) {
                Text("QR 코드로 등록", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onOtherWays) {
                Text("다른 방법")
            }
        }
    }
}

@Composable
private fun CurrentDeviceCard(
    device: RegisteredDevice,
    connection: DeviceConnectionUi,
    lastSeenText: String?,
    freshness: String?,
    figures: DeviceFigures,
    runActive: Boolean,
    online: Boolean,
    nextAction: DeviceNextAction,
    onOpen: () -> Unit,
    onReconnect: () -> Unit,
    onConnectMethods: () -> Unit,
    onStartCollection: () -> Unit,
    onViewRun: () -> Unit
) {
    val c = LocalGeoColors.current
    Surface(color = c.surface, contentColor = c.ink, shape = SurfaceShape, modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(GeoSpace.lg),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DevicePhoto(size = 72.dp)
                    Spacer(Modifier.width(GeoSpace.md))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                        Text(
                            device.deviceName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusChip(
                            text = statusChipText(connection, lastSeenText, online, freshness),
                            tone = connection.tone
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "장치 상세", tint = c.muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                    FigureCell(
                        label = "온도",
                        value = figures.temperature,
                        detail = figures.sourceNote,
                        modifier = Modifier.weight(1f)
                    )
                    FigureCell(
                        label = "저장 여유",
                        value = figures.storageFree,
                        detail = figures.storageFreeDetail ?: figures.sourceNote,
                        modifier = Modifier.weight(1f)
                    )
                    FigureCell(
                        label = "작업",
                        value = if (runActive) "진행 중" else "없음",
                        detail = if (online) null else "마지막 값 기준",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            NextActionBand(
                action = nextAction,
                onReconnect = onReconnect,
                onConnectMethods = onConnectMethods,
                onStartCollection = onStartCollection,
                onViewRun = onViewRun
            )
        }
    }
}

@Composable
private fun NextActionBand(
    action: DeviceNextAction,
    onReconnect: () -> Unit,
    onConnectMethods: () -> Unit,
    onStartCollection: () -> Unit,
    onViewRun: () -> Unit
) {
    if (action == DeviceNextAction.NONE) return
    val (headline, body) = when (action) {
        DeviceNextAction.RECONNECT ->
            "연결이 끊겨 있습니다." to "같은 네트워크에 있으면 앱이 자동으로 다시 연결합니다."
        DeviceNextAction.VIEW_RUN ->
            "수집을 진행하고 있습니다." to "수집이 끝나면 결과와 전송 상태를 확인할 수 있습니다."
        DeviceNextAction.START_COLLECTION ->
            "수집을 시작할 수 있습니다." to "장치가 연결되어 있고 진행 중인 수집이 없습니다."
        DeviceNextAction.NONE -> "" to ""
    }
    val (label, onClick) = when (action) {
        DeviceNextAction.RECONNECT -> "다시 연결" to onReconnect
        DeviceNextAction.VIEW_RUN -> "진행 보기" to onViewRun
        DeviceNextAction.START_COLLECTION -> "수집 시작" to onStartCollection
        DeviceNextAction.NONE -> "" to {}
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(NextActionNavy)
            .padding(GeoSpace.lg),
        verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
            Text(headline, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = NextActionNavyMuted)
        }
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = ControlShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = NextActionNavy)
        ) {
            Text(label, fontWeight = FontWeight.Bold)
        }
        if (action == DeviceNextAction.RECONNECT) {
            TextButton(onClick = onConnectMethods, modifier = Modifier.align(Alignment.End)) {
                Text("연결 방법", color = Color.White)
            }
        }
    }
}

@Composable
internal fun FigureCell(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier
) {
    val c = LocalGeoColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OtherDeviceRow(
    device: RegisteredDevice,
    connection: DeviceConnectionUi,
    onClick: () -> Unit
) {
    val c = LocalGeoColors.current
    Surface(
        onClick = onClick,
        color = c.surface,
        contentColor = c.ink,
        shape = SurfaceShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    ) {
        Row(
            Modifier.padding(horizontal = GeoSpace.lg, vertical = GeoSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DevicePhoto(size = 44.dp)
            Spacer(Modifier.width(GeoSpace.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    device.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${connection.label} · ${connection.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.muted)
        }
    }
}

@Composable
private fun AttentionRow(attention: DeviceAttention, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Text("확인할 일", style = MaterialTheme.typography.titleMedium)
        Surface(
            onClick = onClick,
            color = c.warningBg,
            contentColor = c.ink,
            shape = SurfaceShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        ) {
            Row(
                Modifier.padding(horizontal = GeoSpace.lg, vertical = GeoSpace.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Upload, contentDescription = null, tint = c.warning)
                Spacer(Modifier.width(GeoSpace.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(attention.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(attention.detail, style = MaterialTheme.typography.bodySmall, color = c.inkSubtle)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.warning)
            }
        }
    }
}

/** Device photo from the brand assets. The same image is used on every device row. */
@Composable
internal fun DevicePhoto(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.geo_device),
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

/** A status pill. It is the only shape that uses the full pill radius. */
@Composable
internal fun StatusChip(text: String, tone: StatusTone) {
    val visuals = statusVisuals(tone)
    Surface(
        color = visuals.container,
        contentColor = visuals.content,
        shape = RoundedCornerShape(percent = 50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The chip shows the state, then the one fact that matters for it. */
internal fun statusChipText(
    connection: DeviceConnectionUi,
    lastSeenText: String?,
    online: Boolean,
    freshness: String?
): String = when {
    online && freshness != null -> "${connection.label} · $freshness"
    !online && lastSeenText != null -> "${connection.label} · 마지막 응답 $lastSeenText"
    else -> connection.label
}
