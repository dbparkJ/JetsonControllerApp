package com.example.jetsoncontroller.ui.connection

import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.Button
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.SectionHeader
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.AppSpacing
import com.example.jetsoncontroller.ui.alerts.AlertIconButton
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionHubScreen(
    onAddDevice: () -> Unit,
    onOpenDashboard: () -> Unit,
    unreadAlertCount: Int,
    onAlertsClick: () -> Unit,
    registeredDevices: List<RegisteredDevice>,
    transportState: TransportState,
    lanEndpoints: List<DeviceEndpoint>,
    lanError: String?,
    connectingLanDeviceId: String?,
    localNetworkPermissionGranted: Boolean,
    onRequestLocalNetworkPermission: () -> Unit,
    onRefreshLan: () -> Unit,
    onConnectLan: (DeviceEndpoint) -> Unit,
    onReconnectDevice: (RegisteredDevice) -> Unit,
    onDirectConnect: (RegisteredDevice) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUploadHistoryClick: () -> Unit = {},
    onServerDataClick: () -> Unit = {}
) {
    val colors = LocalGeoColors.current
    var directDevice by remember { mutableStateOf<RegisteredDevice?>(null) }
    directDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { directDevice = null },
            title = { Text("${device.deviceName}에 직접 연결할까요?") },
            text = { Text("장비의 공유기 연결과 서버 업로드가 중단될 수 있습니다. 진행 중인 전송을 확인한 뒤 전환해 주세요.") },
            confirmButton = {
                Button(onClick = { directDevice = null; onDirectConnect(device) }) {
                    Text("직접 연결로 전환")
                }
            },
            dismissButton = { TextButton(onClick = { directDevice = null }) { Text("취소") } }
        )
    }
    val connected = transportState as? TransportState.Connected
    val endpointByDeviceId = lanEndpoints.associateBy { it.deviceId.lowercase() }
    val selected = registeredDevices.firstOrNull {
        connected?.deviceId.equals(it.deviceId, ignoreCase = true)
    } ?: registeredDevices.firstOrNull { endpointByDeviceId.containsKey(it.deviceId.lowercase()) }
        ?: registeredDevices.firstOrNull()
    val others = registeredDevices.filterNot { it.deviceId == selected?.deviceId }

    Scaffold(
        containerColor = colors.canvas,
        topBar = {
            Surface(color = colors.canvas, contentColor = colors.ink) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = GeoSpace.md, vertical = GeoSpace.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(GeoSpace.sm))
                    Text("장치 연결", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    AlertIconButton(unreadAlertCount, onAlertsClick)
                    IconButton(onClick = onAddDevice) { Icon(Icons.Default.Add, "새 장치 등록") }
                }
            }
        },
        bottomBar = {
            Surface(color = colors.surface, contentColor = colors.primary) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = GeoSpace.gutter, vertical = GeoSpace.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onServerDataClick, modifier = Modifier.weight(1f)) {
                        Text("장치 연결 없이 서버 파일 보기")
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "앱 설정")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = GeoSpace.gutter, end = GeoSpace.gutter,
                    top = GeoSpace.sm, bottom = GeoSpace.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
            ) {
                item { Text("사용할 장치를 연결하세요", style = MaterialTheme.typography.headlineMedium) }
                if (selected == null) {
                    item {
                        Surface(color = colors.surface, shape = MaterialTheme.shapes.extraLarge) {
                            Column(
                                Modifier.fillMaxWidth().padding(GeoSpace.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)
                            ) {
                                Image(painterResource(R.drawable.geo_device), "GEO& 도로관리장치", Modifier.size(180.dp))
                                Text("등록된 장치가 없습니다", style = MaterialTheme.typography.headlineSmall)
                                Button(
                                    onClick = onAddDevice,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                ) { Text("QR 코드로 장치 등록") }
                            }
                        }
                    }
                } else {
                    item {
                        val endpoint = endpointByDeviceId[selected.deviceId.lowercase()]
                        val isConnected = connected?.deviceId.equals(selected.deviceId, ignoreCase = true)
                        val connecting = connectingLanDeviceId.equals(selected.deviceId, ignoreCase = true)
                        FeaturedDeviceCard(
                            device = selected,
                            endpoint = endpoint,
                            connectedTransport = connected?.type.takeIf { isConnected },
                            connecting = connecting,
                            onConnect = when {
                                isConnected -> onOpenDashboard
                                endpoint != null -> ({ onConnectLan(endpoint) })
                                else -> ({ onReconnectDevice(selected) })
                            },
                            onDirectConnect = { directDevice = selected }
                        )
                    }
                    if (others.isNotEmpty()) {
                        item { Text("다른 장치", style = MaterialTheme.typography.titleLarge) }
                        items(others, key = { it.deviceId }) { device ->
                            val endpoint = endpointByDeviceId[device.deviceId.lowercase()]
                            val connecting = connectingLanDeviceId.equals(device.deviceId, ignoreCase = true)
                            SavedDeviceRow(
                                device = device,
                                available = endpoint != null,
                                connecting = connecting,
                                onClick = if (endpoint != null) ({ onConnectLan(endpoint) })
                                else ({ onReconnectDevice(device) })
                            )
                        }
                    }
                    item {
                        Surface(onClick = onAddDevice, color = colors.canvas, contentColor = colors.primary) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = GeoSize.minTouchTarget)
                                    .padding(horizontal = GeoSpace.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Text("QR 코드로 새 장치 등록", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                item {
                    when {
                        !localNetworkPermissionGranted -> AppBanner(
                            message = "같은 네트워크의 장치를 찾으려면 로컬 네트워크 권한이 필요합니다.",
                            tone = StatusTone.INFO,
                            actionLabel = "권한 허용",
                            onAction = onRequestLocalNetworkPermission
                        )
                        lanError != null -> AppBanner(
                            message = connectionErrorMessage(lanError),
                            tone = StatusTone.ERROR,
                            actionLabel = if (requiresQrRegistration(lanError)) "QR 재등록" else "다시 검색",
                            onAction = if (requiresQrRegistration(lanError)) onAddDevice else onRefreshLan
                        )
                    }
                }
                val unregisteredEndpoints = lanEndpoints.filter { endpoint ->
                    registeredDevices.none { it.deviceId.equals(endpoint.deviceId, ignoreCase = true) }
                }
                if (unregisteredEndpoints.isNotEmpty()) {
                    item { Text("등록 가능한 장치", style = MaterialTheme.typography.titleLarge) }
                    items(unregisteredEndpoints, key = { it.deviceId }) { endpoint ->
                        Surface(
                            onClick = onAddDevice,
                            color = colors.surface,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(GeoSpace.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
                            ) {
                                Icon(Icons.Default.Router, contentDescription = null, tint = colors.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(endpoint.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text("QR 등록 필요", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedDeviceCard(
    device: RegisteredDevice,
    endpoint: DeviceEndpoint?,
    connectedTransport: TransportType?,
    connecting: Boolean,
    onConnect: () -> Unit,
    onDirectConnect: () -> Unit
) {
    val colors = LocalGeoColors.current
    val connection = userConnectionStage(connectedTransport != null, connectedTransport)
    val available = endpoint != null || connectedTransport != null
    val label = when {
        connecting -> "연결 중"
        connectedTransport != null -> connection.label
        available -> "연결 가능"
        else -> "등록됨"
    }
    val tone = if (connectedTransport != null && connectedTransport != TransportType.BLE) {
        StatusTone.SUCCESS
    } else if (connecting) StatusTone.PENDING else StatusTone.INFO
    val statusColor = com.example.jetsoncontroller.ui.components.statusVisuals(tone).content
    Surface(color = colors.surface, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.fillMaxWidth().padding(GeoSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)
        ) {
            Image(painterResource(R.drawable.geo_device), "${device.deviceName} 장치", Modifier.size(180.dp))
            Text(device.deviceName, style = MaterialTheme.typography.headlineSmall)
            Text("● $label", style = MaterialTheme.typography.labelMedium, color = statusColor)
            Button(
                onClick = onConnect,
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                if (connecting) CircularProgressIndicator(Modifier.size(GeoSize.iconMd), strokeWidth = 2.dp)
                else Text(if (connectedTransport != null) "이 장치 열기" else "이 장치 연결")
            }
            if (!available && !connecting) {
                TextButton(onClick = onDirectConnect) { Text("연결 문제 해결") }
            }
        }
    }
}

@Composable
private fun SavedDeviceRow(
    device: RegisteredDevice,
    available: Boolean,
    connecting: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalGeoColors.current
    Surface(onClick = onClick, color = colors.surface, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(GeoSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Image(painterResource(R.drawable.geo_device), null, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                Text(device.deviceName, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        connecting -> "연결 중"
                        available -> "연결 가능"
                        else -> "등록된 장치"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "${device.deviceName} 연결")
        }
    }
}

internal fun connectionErrorMessage(message: String): String =
    if (requiresQrRegistration(message)) {
        "장치 인증 정보를 확인할 수 없습니다. QR 코드로 다시 등록하세요."
    } else {
        "장치를 찾거나 연결하지 못했습니다. 같은 네트워크인지 확인하고 다시 시도하세요."
    }

internal fun requiresQrRegistration(message: String): Boolean {
    val normalized = message.lowercase()
    return "qr" in normalized ||
        "등록되어 있지" in normalized ||
        "인증 정보를 동기화" in normalized
}

@Composable
private fun RegisteredDeviceCard(
    device: RegisteredDevice,
    endpoint: DeviceEndpoint?,
    connectedTransport: TransportType?,
    connecting: Boolean,
    onClick: (() -> Unit)?,
    onDirectConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connected = connectedTransport != null
    val available = endpoint != null
    val online = connected
    val connectionStage = userConnectionStage(online, connectedTransport)
    val badgeLabel = when {
        connecting -> "연결 준비 중"
        online -> connectionStage.label
        available -> "장비 발견됨"
        else -> "등록됨"
    }
    val badgeTone = if (online && connectedTransport != TransportType.BLE) StatusTone.SUCCESS else StatusTone.INFO

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppSpacing.large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            connecting -> "장비를 확인하고 있습니다."
                            online -> connectionStage.detail
                            available -> "같은 네트워크에서 장비를 찾았습니다."
                            else -> "인증 정보가 저장되어 있습니다. 연결을 시도해 주세요."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    StatusBadge(badgeLabel, badgeTone)
                }
            }
            Spacer(Modifier.height(AppSpacing.medium))
            Button(
                onClick = { onClick?.invoke() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !connecting && onClick != null
            ) {
                if (connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        when {
                            connected -> "장비 열기"
                            available -> "연결"
                            else -> "연결 시도"
                        }
                    )
                }
            }
            TextButton(onClick = onDirectConnect, modifier = Modifier.fillMaxWidth()) {
                Text("연결 문제 해결 · 장비에 직접 연결")
            }
        }
    }
}

internal fun formatLastSeen(lastSeenAtEpochMillis: Long, nowEpochMillis: Long): String {
    val elapsedSeconds = ((nowEpochMillis - lastSeenAtEpochMillis).coerceAtLeast(0L) / 1_000L)
    return when {
        elapsedSeconds < 60L -> "마지막 확인 · 방금"
        elapsedSeconds < 3_600L -> "마지막 확인 · ${elapsedSeconds / 60L}분 전"
        elapsedSeconds < 86_400L -> "마지막 확인 · ${elapsedSeconds / 3_600L}시간 전"
        else -> "마지막 확인 · ${elapsedSeconds / 86_400L}일 전"
    }
}

@Composable
private fun LanRegistrationRow(
    endpoint: DeviceEndpoint,
    onRegister: () -> Unit
) {
    ListItem(
        headlineContent = { Text(endpoint.displayName) },
        supportingContent = { Text("${endpoint.host}:${endpoint.port}") },
        leadingContent = { Icon(Icons.Default.Router, contentDescription = null) },
        trailingContent = {
            OutlinedButton(onClick = onRegister) { Text("QR 등록") }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun ConnectionMethod(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
