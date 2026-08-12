package com.example.jetsoncontroller.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.MetricCard
import com.example.jetsoncontroller.ui.components.SectionHeader

private enum class PowerAction { REBOOT, SHUTDOWN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onDisconnect: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    onStorageClick: () -> Unit,
    onNetworkSettingsClick: () -> Unit,
    onWifiDirectClick: () -> Unit,
    onUploadHistoryClick: () -> Unit,
    onPipelinesClick: () -> Unit,
    onDismissOperationMessage: () -> Unit,
    onBack: () -> Unit
) {
    var pendingPowerAction by remember { mutableStateOf<PowerAction?>(null) }

    pendingPowerAction?.let { action ->
        val rebooting = action == PowerAction.REBOOT
        AlertDialog(
            onDismissRequest = { pendingPowerAction = null },
            icon = {
                Icon(
                    if (rebooting) Icons.Default.RestartAlt else Icons.Default.PowerSettingsNew,
                    contentDescription = null
                )
            },
            title = {
                Text(if (rebooting) "Jetson을 재부팅할까요?" else "Jetson을 종료할까요?")
            },
            text = {
                Text(
                    if (rebooting) {
                        "실행 중인 수집 작업이 중단되고 연결이 잠시 끊어집니다."
                    } else {
                        "실행 중인 작업이 중단됩니다. 다시 사용하려면 Jetson 전원을 직접 켜야 합니다."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingPowerAction = null
                    if (rebooting) onReboot() else onShutdown()
                }) {
                    Text(if (rebooting) "재부팅" else "종료")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPowerAction = null }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.deviceName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            transportLabel(state.transportType, state.endpoint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Default.LinkOff, contentDescription = "연결 해제")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.operationInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.operationMessage?.let { message ->
                Surface(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    onClick = onDismissOperationMessage,
                    shape = MaterialTheme.shapes.medium
                ) {
                    InlineMessage(message = message, isError = state.operationIsError)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                ConnectionSummary(state)
                Spacer(Modifier.height(24.dp))
                SectionHeader("시스템 상태")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard("CPU", "${state.status.cpuPercent}%", Modifier.weight(1f))
                    MetricCard("GPU", "${state.status.gpuPercent}%", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard("온도", "${state.status.temperatureC} C", Modifier.weight(1f))
                    MetricCard("저장 공간", "${state.status.storagePercent}%", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("메모리", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${formatMemory(state.status.ramUsedMb)} / ${formatMemory(state.status.ramTotalMb)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        val ramProgress = if (state.status.ramTotalMb > 0) {
                            state.status.ramUsedMb.toFloat() / state.status.ramTotalMb
                        } else 0f
                        LinearProgressIndicator(
                            progress = { ramProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                SectionHeader("작업")
            }

            DashboardAction(
                icon = Icons.Default.Wifi,
                title = "Wi-Fi 설정",
                description = "Jetson을 사용할 공유기에 연결",
                enabled = state.capabilities.wifiProvisioning,
                onClick = onNetworkSettingsClick
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 20.dp))
            DashboardAction(
                icon = Icons.Default.Wifi,
                title = "Wi-Fi Direct",
                description = if (state.transportType == TransportType.WIFI_DIRECT) {
                    "직접 연결 상태 확인"
                } else {
                    "공유기 없이 Jetson 제어망 연결"
                },
                enabled = true,
                onClick = onWifiDirectClick
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 20.dp))
            DashboardAction(
                icon = Icons.Default.FolderOpen,
                title = "저장소 탐색",
                description = if (state.transportType == TransportType.BLE) {
                    "LAN 또는 Wi-Fi Direct 연결이 필요합니다"
                } else {
                    "수집 파일과 폴더 확인"
                },
                enabled = state.capabilities.fileBrowsing && state.transportType != TransportType.BLE,
                onClick = onStorageClick
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 20.dp))
            DashboardAction(
                icon = Icons.Default.CloudUpload,
                title = "업로드 기록",
                description = if (state.transportType == TransportType.BLE) {
                    "LAN 또는 Wi-Fi Direct 연결이 필요합니다"
                } else {
                    "외부 서버 전송 상태와 결과"
                },
                enabled = state.capabilities.uploads && state.transportType != TransportType.BLE,
                onClick = onUploadHistoryClick
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 20.dp))
            DashboardAction(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                title = "자동 실행 작업",
                description = if (state.transportType == TransportType.BLE) {
                    "LAN 또는 Wi-Fi Direct 연결이 필요합니다"
                } else {
                    "Python 파이프라인 실행과 부팅 설정"
                },
                enabled = state.capabilities.pipelines && state.transportType != TransportType.BLE,
                onClick = onPipelinesClick
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(28.dp))
                SectionHeader("장비 관리")
                Spacer(Modifier.height(10.dp))
                if (!state.capabilities.powerCommandsEnabled) {
                    InlineMessage(
                        message = "Jetson에서 전원 명령이 비활성화되어 있습니다.",
                        isError = false
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { pendingPowerAction = PowerAction.REBOOT },
                        modifier = Modifier.weight(1f),
                        enabled = state.capabilities.powerCommandsEnabled && !state.operationInProgress
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Text("재부팅", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = { pendingPowerAction = PowerAction.SHUTDOWN },
                        modifier = Modifier.weight(1f),
                        enabled = state.capabilities.powerCommandsEnabled && !state.operationInProgress
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Text("종료", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ConnectionSummary(state: DashboardUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("온라인", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "상태 정보가 자동으로 갱신됩니다",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when (state.transportType) {
                        TransportType.LAN -> "LAN"
                        TransportType.WIFI_DIRECT -> "Wi-Fi Direct"
                        TransportType.BLE -> "Bluetooth"
                        null -> "연결 확인"
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun DashboardAction(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    ListItem(
        headlineContent = {
            Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        },
        supportingContent = {
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

private fun transportLabel(type: TransportType?, endpoint: String?): String {
    val label = when (type) {
        TransportType.LAN -> "LAN"
        TransportType.WIFI_DIRECT -> "Wi-Fi Direct"
        TransportType.BLE -> "Bluetooth"
        null -> "연결 확인 중"
    }
    return if (endpoint.isNullOrBlank()) label else "$label · $endpoint"
}

private fun formatMemory(megabytes: Int): String = when {
    megabytes >= 1024 -> "%.1f GB".format(megabytes / 1024f)
    else -> "$megabytes MB"
}
