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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onUploadHistoryClick: () -> Unit = {}
) {
    var directDevice by remember { mutableStateOf<RegisteredDevice?>(null) }
    directDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { directDevice = null },
            title = { Text("${device.deviceName}에 직접 연결할까요?") },
            text = { Text("장비의 공유기 연결과 서버 업로드가 중단될 수 있습니다. 휴대전화의 Wi-Fi 연결에도 영향을 줄 수 있습니다. 진행 중인 업로드를 확인한 뒤 전환해 주세요.") },
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
                            enabled = localNetworkPermissionGranted
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "장비 상태 새로고침")
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
                        connecting = connectingLanDeviceId.equals(device.deviceId, ignoreCase = true),
                        onDirectConnect = { directDevice = device },
                        onClick = when {
                            isConnected -> onOpenDashboard
                            endpoint != null -> ({ onConnectLan(endpoint) })
                            else -> ({ onReconnectDevice(device) })
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
                    title = "연결 도구 및 앱 설정",
                    modifier = Modifier.padding(horizontal = AppSpacing.screen)
                )
                ConnectionMethod(
                    icon = Icons.Default.QrCodeScanner,
                    title = "새 장비 등록",
                    description = "QR로 장비 인증 정보 저장",
                    onClick = onAddDevice
                )
                ConnectionMethod(Icons.Default.Settings, "앱 알림 설정", "장비 연결 없이 알림 설정 변경", onSettingsClick)
                ConnectionMethod(Icons.Default.History, "업로드 기록", "마지막으로 확인한 전송 기록 보기", onUploadHistoryClick)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            connecting -> "장비를 확인하고 있습니다."
                            online -> connectionStage.detail
                            available -> "같은 네트워크에서 장비를 찾았습니다."
                            else -> "인증 정보가 저장되어 있습니다. 연결을 시도해 주세요."
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
