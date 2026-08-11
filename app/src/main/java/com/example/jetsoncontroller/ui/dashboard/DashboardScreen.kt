package com.example.jetsoncontroller.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.ui.components.MetricCard

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onDisconnect: () -> Unit,
    onStartSystem: () -> Unit,
    onStopSystem: () -> Unit,
    onRestartServices: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    onStorageClick: () -> Unit,
    onNetworkSettingsClick: () -> Unit,
    onUploadHistoryClick: () -> Unit
) {

    val deviceName =
        when (
            val connection =
                state.connectionState
        ) {

            is ConnectionState.Ready ->
                connection.deviceName

            is ConnectionState.Connected ->
                connection.deviceName

            is ConnectionState.Connecting ->
                connection.deviceName

            else ->
                "Jetson"
        }

    Scaffold(
        containerColor =
            MaterialTheme
                .colorScheme
                .background
    ) {
            paddingValues ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .padding(
                        horizontal = 22.dp
                    )
                    .verticalScroll(
                        rememberScrollState()
                    )
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = deviceName,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    if (
                        state.connectionState
                        is ConnectionState.Ready ||
                        state.transportType != null
                    )
                        "● 연결됨"
                    else
                        "연결 상태 확인 중",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            TransportBadge(state.transportType)

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = "시스템 상태",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                MetricCard(
                    title = "CPU",
                    value =
                        "${state.status.cpuPercent}%",
                    modifier =
                        Modifier.weight(1f)
                )

                MetricCard(
                    title = "GPU",
                    value =
                        "${state.status.gpuPercent}%",
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                MetricCard(
                    title = "TEMP",
                    value =
                        "${state.status.temperatureC}°C",
                    modifier =
                        Modifier.weight(1f)
                )

                MetricCard(
                    title = "STORAGE",
                    value =
                        "${state.status.storagePercent}%",
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = "서비스",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            ServiceRow(
                name = "Camera",
                running =
                    state.status
                        .cameraRunning
            )

            ServiceRow(
                name = "LiDAR",
                running =
                    state.status
                        .lidarRunning
            )

            ServiceRow(
                name = "GNSS",
                running =
                    state.status
                        .gnssRunning
            )

            ServiceRow(
                name = "MMS",
                running =
                    state.status
                        .mmsRunning
            )

            Spacer(modifier = Modifier.height(32.dp))

            DashboardNavSection(
                title = "네트워크",
                icon = Icons.Default.Wifi,
                label = "공유기 연결 설정",
                onClick = onNetworkSettingsClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            DashboardNavSection(
                title = "데이터",
                icon = Icons.Default.Storage,
                label = "수집 데이터 탐색",
                onClick = onStorageClick,
                enabled = state.transportType != TransportType.BLE
            )

            Spacer(modifier = Modifier.height(12.dp))

            DashboardNavSection(
                title = "업로드",
                icon = Icons.Default.CloudUpload,
                label = "업로드 관리",
                onClick = onUploadHistoryClick,
                enabled = state.transportType != TransportType.BLE
            )

            Spacer(
                modifier =
                    Modifier.height(
                        32.dp
                    )
            )

            Button(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onStartSystem
            ) {

                Text("전체 시스템 시작")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onStopSystem
            ) {

                Text("전체 시스템 중지")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onRestartServices
            ) {

                Text("서비스 재시작")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        30.dp
                    )
            )

            HorizontalDivider()

            Spacer(
                modifier =
                    Modifier.height(
                        22.dp
                    )
            )

            Text(
                text = "장비 관리",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onReboot
            ) {
                Text("Jetson 재부팅")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onShutdown
            ) {
                Text("Jetson 종료")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onDisconnect
            ) {
                Text("연결 해제")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        40.dp
                    )
            )
        }
    }
}


@Composable
private fun ServiceRow(
    name: String,
    running: Boolean
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 10.dp
                ),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            text = name,
            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )

        Text(
            text =
                if (running)
                    "● Running"
                else
                    "○ Stopped",
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}

@Composable
private fun TransportBadge(type: TransportType?) {
    val (label, color) = when (type) {
        TransportType.LAN -> "LAN" to MaterialTheme.colorScheme.primary
        TransportType.WIFI_DIRECT -> "Wi-Fi Direct" to MaterialTheme.colorScheme.secondary
        TransportType.BLE -> "Bluetooth" to MaterialTheme.colorScheme.tertiary
        null -> "연결 확인 중" to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardNavSection(
    title: String,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.4f
    
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline.copy(alpha = alpha))
        }
    }
}
