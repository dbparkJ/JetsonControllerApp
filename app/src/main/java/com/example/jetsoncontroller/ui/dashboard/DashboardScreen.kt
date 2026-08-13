package com.example.jetsoncontroller.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.MetricCard
import com.example.jetsoncontroller.ui.components.SectionHeader
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.AppSpacing

private enum class PowerAction { REBOOT, SHUTDOWN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    pipelines: List<ManagedPipeline>,
    uploads: List<UploadJob>,
    onDisconnect: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    onStorageClick: () -> Unit,
    onNetworkSettingsClick: () -> Unit,
    onWifiDirectClick: () -> Unit,
    onUploadQueueClick: () -> Unit,
    onPipelinesClick: () -> Unit,
    onSectionSelected: (ControlSection) -> Unit,
    onDismissOperationMessage: () -> Unit,
    onBack: () -> Unit
) {
    var pendingPowerAction by remember { mutableStateOf<PowerAction?>(null) }
    val health = assessDashboardHealth(
        status = state.status,
        freshness = state.statusFreshness,
        pipelines = pipelines,
        uploads = uploads
    )

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
            title = { Text(if (rebooting) "Jetson을 재부팅할까요?" else "Jetson을 종료할까요?") },
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
                        Text(state.deviceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "내 장비로 이동")
                    }
                },
                actions = {
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Default.LinkOff, contentDescription = "연결 해제")
                    }
                }
            )
        },
        bottomBar = { ControlNavigationBar(ControlSection.OVERVIEW, onSectionSelected) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = AppSpacing.section)
        ) {
            if (state.operationInProgress) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            state.operationMessage?.let { message ->
                item {
                    AppBanner(
                        message = message,
                        tone = if (state.operationIsError) StatusTone.ERROR else StatusTone.SUCCESS,
                        onDismiss = onDismissOperationMessage,
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.screen,
                            vertical = AppSpacing.medium
                        )
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = AppSpacing.screen)) {
                    Spacer(Modifier.height(AppSpacing.medium))
                    HealthOverview(state = state, health = health)
                    if (state.statusFreshness == StatusFreshness.STALE) {
                        Spacer(Modifier.height(AppSpacing.medium))
                        AppBanner(
                            message = "마지막 상태 응답 이후 ${state.statusAgeSeconds ?: 0}초가 지났습니다. 표시된 수치는 최신 값이 아닐 수 있습니다.",
                            tone = StatusTone.WARNING
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.section))
                    SectionHeader("진행 중인 작업")
                    Spacer(Modifier.height(AppSpacing.small))
                    ActiveWork(
                        pipelines = pipelines,
                        uploads = uploads,
                        onPipelinesClick = onPipelinesClick,
                        onUploadQueueClick = onUploadQueueClick
                    )
                    Spacer(Modifier.height(AppSpacing.section))
                    SectionHeader(
                        title = if (state.statusFreshness == StatusFreshness.STALE) {
                            "마지막 시스템 지표"
                        } else {
                            "시스템 지표"
                        },
                        trailing = {
                            Text(
                                statusAgeLabel(state),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Spacer(Modifier.height(AppSpacing.small))
                    MetricsGrid(state)
                    Spacer(Modifier.height(AppSpacing.section))
                    SectionHeader("빠른 작업")
                }
            }

            item {
                DashboardAction(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi 설정",
                    description = "Jetson을 사용할 공유기에 연결",
                    enabled = state.capabilities.wifiProvisioning,
                    onClick = onNetworkSettingsClick
                )
                DashboardDivider()
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
                DashboardDivider()
                DashboardAction(
                    icon = Icons.Default.FolderOpen,
                    title = "저장소 탐색",
                    description = transportRequirementDescription(
                        state.transportType,
                        "수집 파일과 폴더 확인"
                    ),
                    enabled = state.capabilities.fileBrowsing && state.transportType != TransportType.BLE,
                    onClick = onStorageClick
                )
                DashboardDivider()
                DashboardAction(
                    icon = Icons.Default.CloudUpload,
                    title = "전송 큐",
                    description = transportRequirementDescription(
                        state.transportType,
                        "대기 및 진행 중인 서버 전송"
                    ),
                    enabled = state.capabilities.uploads && state.transportType != TransportType.BLE,
                    onClick = onUploadQueueClick
                )
                DashboardDivider()
                DashboardAction(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = "자동 실행 작업",
                    description = transportRequirementDescription(
                        state.transportType,
                        "Python 파이프라인 실행과 부팅 설정"
                    ),
                    enabled = state.capabilities.pipelines && state.transportType != TransportType.BLE,
                    onClick = onPipelinesClick
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = AppSpacing.screen)) {
                    Spacer(Modifier.height(AppSpacing.section))
                    SectionHeader("관리자 작업")
                    Spacer(Modifier.height(AppSpacing.small))
                    Text(
                        "재부팅과 종료는 실행 중인 수집 작업에 영향을 줍니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppSpacing.medium))
                    if (!state.capabilities.powerCommandsEnabled) {
                        AppBanner(
                            message = "Jetson에서 전원 명령이 비활성화되어 있습니다.",
                            tone = StatusTone.INFO
                        )
                        Spacer(Modifier.height(AppSpacing.medium))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                    ) {
                        OutlinedButton(
                            onClick = { pendingPowerAction = PowerAction.REBOOT },
                            modifier = Modifier.weight(1f),
                            enabled = state.capabilities.powerCommandsEnabled && !state.operationInProgress
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Text("재부팅", modifier = Modifier.padding(start = AppSpacing.small))
                        }
                        OutlinedButton(
                            onClick = { pendingPowerAction = PowerAction.SHUTDOWN },
                            modifier = Modifier.weight(1f),
                            enabled = state.capabilities.powerCommandsEnabled && !state.operationInProgress
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Text("종료", modifier = Modifier.padding(start = AppSpacing.small))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthOverview(
    state: DashboardUiState,
    health: DashboardHealth
) {
    val tone = when (health.level) {
        DashboardHealthLevel.HEALTHY -> StatusTone.SUCCESS
        DashboardHealthLevel.ATTENTION -> StatusTone.WARNING
        DashboardHealthLevel.UNKNOWN -> StatusTone.INFO
    }
    val icon = when (health.level) {
        DashboardHealthLevel.HEALTHY -> Icons.Default.CheckCircle
        DashboardHealthLevel.ATTENTION -> Icons.Default.WarningAmber
        DashboardHealthLevel.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
    }
    val container = when (tone) {
        StatusTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        StatusTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (tone) {
        StatusTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(AppSpacing.large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        health.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(health.detail, style = MaterialTheme.typography.bodyMedium)
                }
                StatusBadge(
                    label = when (state.transportType) {
                        TransportType.LAN -> "LAN"
                        TransportType.WIFI_DIRECT -> "Wi-Fi Direct"
                        TransportType.BLE -> "Bluetooth"
                        null -> "확인 중"
                    },
                    tone = tone
                )
            }
            if (health.issues.size > 1) {
                Spacer(Modifier.height(AppSpacing.medium))
                health.issues.drop(1).forEach { issue ->
                    Text("• $issue", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ActiveWork(
    pipelines: List<ManagedPipeline>,
    uploads: List<UploadJob>,
    onPipelinesClick: () -> Unit,
    onUploadQueueClick: () -> Unit
) {
    val activePipelines = pipelines.filter {
        it.state in setOf(PipelineState.RUNNING, PipelineState.STARTING, PipelineState.RETRYING)
    }
    val activeUploads = uploads.filter {
        it.state in setOf(UploadJobState.QUEUED, UploadJobState.SCANNING, UploadJobState.UPLOADING)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            if (activePipelines.isEmpty() && activeUploads.isEmpty()) {
                Text(
                    "현재 진행 중인 작업이 없습니다.",
                    modifier = Modifier.padding(AppSpacing.large),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (activePipelines.isNotEmpty()) {
                WorkRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = activePipelines.first().label,
                    description = if (activePipelines.size == 1) {
                        "파이프라인 실행 중"
                    } else {
                        "파이프라인 ${activePipelines.size}개 실행 중"
                    },
                    onClick = onPipelinesClick
                )
            }
            if (activePipelines.isNotEmpty() && activeUploads.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(start = 60.dp))
            }
            if (activeUploads.isNotEmpty()) {
                WorkRow(
                    icon = Icons.Default.CloudUpload,
                    title = activeUploads.first().currentFile ?: activeUploads.first().relativePath,
                    description = if (activeUploads.size == 1) {
                        "서버 업로드 ${uploadProgress(activeUploads.first())}"
                    } else {
                        "전송 대기열 ${activeUploads.size}개"
                    },
                    onClick = onUploadQueueClick
                )
            }
        }
    }
}

@Composable
private fun WorkRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(AppSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun MetricsGrid(state: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        MetricCard("CPU", "${state.status.cpuPercent}%", Modifier.weight(1f))
        MetricCard("GPU", "${state.status.gpuPercent}%", Modifier.weight(1f))
    }
    Spacer(Modifier.height(AppSpacing.small))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        MetricCard("온도", "${state.status.temperatureC} C", Modifier.weight(1f))
        MetricCard("저장 공간", "${state.status.storagePercent}%", Modifier.weight(1f))
    }
    Spacer(Modifier.height(AppSpacing.small))
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
            Spacer(Modifier.height(AppSpacing.small))
            val progress = if (state.status.ramTotalMb > 0) {
                state.status.ramUsedMb.toFloat() / state.status.ramTotalMb
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
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
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent = {
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
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

@Composable
private fun DashboardDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = AppSpacing.screen))
}

private fun statusAgeLabel(state: DashboardUiState): String = when (state.statusFreshness) {
    StatusFreshness.UNKNOWN -> "응답 대기 중"
    StatusFreshness.STALE -> "${state.statusAgeSeconds ?: 0}초 전"
    StatusFreshness.CURRENT -> when (val age = state.statusAgeSeconds ?: 0) {
        0L -> "방금 갱신"
        else -> "${age}초 전"
    }
}

private fun transportRequirementDescription(type: TransportType?, readyText: String): String =
    if (type == TransportType.BLE) "LAN 또는 Wi-Fi Direct 연결이 필요합니다" else readyText

private fun transportLabel(type: TransportType?, endpoint: String?): String {
    val label = when (type) {
        TransportType.LAN -> "LAN"
        TransportType.WIFI_DIRECT -> "Wi-Fi Direct"
        TransportType.BLE -> "Bluetooth"
        null -> "연결 확인 중"
    }
    return if (endpoint.isNullOrBlank()) label else "$label · $endpoint"
}

private fun uploadProgress(job: UploadJob): String {
    val total = job.bytesTotal ?: return "진행 중"
    val transferred = job.bytesTransferred ?: 0L
    if (total <= 0L) return "진행 중"
    return "${(transferred * 100L / total).coerceIn(0L, 100L)}%"
}

private fun formatMemory(megabytes: Int): String = when {
    megabytes >= 1024 -> "%.1f GB".format(megabytes / 1024f)
    else -> "$megabytes MB"
}
