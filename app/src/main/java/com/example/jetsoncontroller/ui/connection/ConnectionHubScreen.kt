package com.example.jetsoncontroller.ui.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionHubScreen(
    onBleClick: () -> Unit,
    onAddDevice: () -> Unit,
    onOpenDashboard: () -> Unit,
    onWifiDirectClick: () -> Unit,
    unreadAlertCount: Int,
    onAlertsClick: () -> Unit,
    registeredDevices: List<RegisteredDevice>,
    transportState: TransportState,
    lanEndpoints: List<DeviceEndpoint>,
    lastSeenAtEpochMillis: Map<String, Long>,
    lanDiscovering: Boolean,
    lanError: String?,
    connectingLanDeviceId: String?,
    localNetworkPermissionGranted: Boolean,
    onRequestLocalNetworkPermission: () -> Unit,
    onRefreshLan: () -> Unit,
    onConnectLan: (DeviceEndpoint) -> Unit
) {
    val connected = transportState as? TransportState.Connected
    val endpointByDeviceId = lanEndpoints.associateBy { it.deviceId.lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Jetson Controller")
                        Text(
                            "내 장비",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    AlertIconButton(unreadAlertCount, onAlertsClick)
                    IconButton(onClick = onAddDevice) {
                        Icon(Icons.Default.Add, contentDescription = "새 장비 등록")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = AppSpacing.section)
        ) {
            item {
                SectionHeader(
                    title = "등록된 장비",
                    modifier = Modifier.padding(
                        start = AppSpacing.screen,
                        top = AppSpacing.large,
                        end = AppSpacing.small
                    ),
                    trailing = {
                        IconButton(
                            onClick = onRefreshLan,
                            enabled = localNetworkPermissionGranted && !lanDiscovering
                        ) {
                            if (lanDiscovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "장비 상태 새로고침")
                            }
                        }
                    }
                )
                Spacer(Modifier.height(AppSpacing.small))
            }

            if (registeredDevices.isEmpty()) {
                item {
                    EmptyState(
                        title = "등록된 Jetson이 없습니다",
                        message = "첫 장비를 등록하면 연결 상태와 작업을 이곳에서 확인할 수 있습니다.",
                        actionLabel = "장비 등록",
                        onAction = onAddDevice
                    )
                }
            } else {
                items(registeredDevices, key = { it.deviceId }) { device ->
                    val endpoint = endpointByDeviceId[device.deviceId.lowercase()]
                    val isConnected = connected?.deviceId.equals(device.deviceId, ignoreCase = true)
                    RegisteredDeviceCard(
                        device = device,
                        endpoint = endpoint,
                        connectedTransport = if (isConnected) connected?.type else null,
                        lastSeenAtEpochMillis = lastSeenAtEpochMillis[device.deviceId.lowercase()],
                        connecting = connectingLanDeviceId.equals(device.deviceId, ignoreCase = true),
                        onClick = when {
                            isConnected -> onOpenDashboard
                            endpoint != null -> ({ onConnectLan(endpoint) })
                            else -> onBleClick
                        },
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.screen,
                            vertical = AppSpacing.xSmall
                        )
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = AppSpacing.screen)) {
                    if (!localNetworkPermissionGranted) {
                        AppBanner(
                            message = "같은 네트워크의 장비를 찾으려면 로컬 네트워크 권한이 필요합니다.",
                            tone = StatusTone.INFO,
                            actionLabel = "권한 허용",
                            onAction = onRequestLocalNetworkPermission,
                            modifier = Modifier.padding(top = AppSpacing.medium)
                        )
                    } else if (lanError != null) {
                        val requiresRegistration = requiresQrRegistration(lanError)
                        AppBanner(
                            message = lanError,
                            tone = StatusTone.ERROR,
                            actionLabel = if (requiresRegistration) "QR 재등록" else "다시 검색",
                            onAction = if (requiresRegistration) onAddDevice else onRefreshLan,
                            modifier = Modifier.padding(top = AppSpacing.medium)
                        )
                    }
                }
            }

            val unregisteredEndpoints = lanEndpoints.filter { endpoint ->
                registeredDevices.none { it.deviceId.equals(endpoint.deviceId, ignoreCase = true) }
            }
            if (unregisteredEndpoints.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(AppSpacing.section))
                    SectionHeader(
                        title = "등록 가능한 장비",
                        modifier = Modifier.padding(horizontal = AppSpacing.screen)
                    )
                }
                items(unregisteredEndpoints, key = { it.deviceId }) { endpoint ->
                    LanRegistrationRow(endpoint = endpoint, onRegister = onAddDevice)
                }
            }

            item {
                Spacer(Modifier.height(AppSpacing.section))
                SectionHeader(
                    title = "연결 도구",
                    modifier = Modifier.padding(horizontal = AppSpacing.screen)
                )
                ConnectionMethod(
                    icon = Icons.Default.QrCodeScanner,
                    title = "새 장비 등록",
                    description = "QR로 장비 인증 정보 저장",
                    onClick = onAddDevice
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = AppSpacing.screen))
                ConnectionMethod(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth 장비 찾기",
                    description = "등록 장비 재연결 또는 주변 장비 확인",
                    onClick = onBleClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = AppSpacing.screen))
                ConnectionMethod(
                    icon = Icons.Default.WifiTethering,
                    title = "Wi-Fi Direct",
                    description = "공유기 없이 Jetson에 직접 연결",
                    onClick = onWifiDirectClick
                )
            }
        }
    }
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
    lastSeenAtEpochMillis: Long?,
    connecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connected = connectedTransport != null
    val available = endpoint != null
    val badgeLabel = when {
        connected -> "온라인"
        available -> "연결 가능"
        else -> "오프라인"
    }
    val badgeTone = when {
        connected -> StatusTone.SUCCESS
        available -> StatusTone.INFO
        else -> StatusTone.WARNING
    }

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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            connectedTransport != null -> {
                                "${transportDisplayName(connectedTransport)}로 연결됨"
                            }
                            endpoint != null -> "LAN에서 방금 확인"
                            lastSeenAtEpochMillis != null -> formatLastSeen(
                                lastSeenAtEpochMillis,
                                System.currentTimeMillis()
                            )
                            else -> "최근 연결 정보 없음"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusBadge(badgeLabel, badgeTone)
            }
            Spacer(Modifier.height(AppSpacing.medium))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !connecting
            ) {
                if (connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        when {
                            connected -> "대시보드 열기"
                            available -> "연결"
                            else -> "Bluetooth로 찾기"
                        }
                    )
                }
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

private fun transportDisplayName(transportType: TransportType): String = when (transportType) {
    TransportType.LAN -> "LAN"
    TransportType.WIFI_DIRECT -> "Wi-Fi Direct"
    TransportType.BLE -> "Bluetooth"
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
