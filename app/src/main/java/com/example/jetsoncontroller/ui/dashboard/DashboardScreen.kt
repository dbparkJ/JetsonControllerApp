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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.jetsoncontroller.ui.alerts.AlertIconButton
import kotlin.math.roundToInt

private enum class PowerAction { REBOOT, SHUTDOWN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    pipelines: List<ManagedPipeline>,
    uploads: List<UploadJob>,
    unreadAlertCount: Int,
    onAlertsClick: () -> Unit,
    onDisconnect: () -> Unit,
    onStartSystem: () -> Unit,
    onStopSystem: () -> Unit,
    onRestartServices: () -> Unit,
    onRefreshFan: () -> Unit,
    onSetFanAuto: () -> Unit,
    onSetFanManual: (Int) -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    onStorageClick: () -> Unit,
    onNetworkSettingsClick: () -> Unit,
    onUploadQueueClick: () -> Unit,
    onPipelinesClick: () -> Unit,
    onSectionSelected: (ControlSection) -> Unit,
    onDismissOperationMessage: () -> Unit,
    onBack: () -> Unit
) {
    var pendingPowerAction by remember { mutableStateOf<PowerAction?>(null) }
    var dismissedHealthKey by rememberSaveable { mutableStateOf<String?>(null) }
    val health = assessDashboardHealth(
        status = state.status,
        freshness = state.statusFreshness,
        pipelines = pipelines,
        uploads = uploads
    )
    val healthKey = dashboardHealthKey(health)
    val activeUploads = uploads.filter { it.state.isActiveUploadState() }

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
                            if (state.isOnline) "온라인" else "오프라인",
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
                    AlertIconButton(unreadAlertCount, onAlertsClick)
                    IconButton(onClick = onDisconnect, enabled = state.isOnline) {
                        Icon(Icons.Default.LinkOff, contentDescription = "연결 해제")
                    }
                }
            )
        },
        bottomBar = {
            ControlNavigationBar(
                selected = ControlSection.OVERVIEW,
                onSelect = onSectionSelected,
                enabledSections = if (state.fullControlAvailable) {
                    ControlSection.entries.toSet()
                } else {
                    setOf(ControlSection.OVERVIEW, ControlSection.SENSORS)
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
                    ConnectionModeSummary(state)
                    Spacer(Modifier.height(AppSpacing.medium))
                    if (!state.isOnline) {
                        AppBanner(
                            message = "기기가 오프라인입니다.",
                            tone = StatusTone.WARNING
                        )
                    } else if (dismissedHealthKey != healthKey) {
                        HealthOverview(
                            state = state,
                            health = health,
                            onDismiss = if (health.level == DashboardHealthLevel.ATTENTION) {
                                { dismissedHealthKey = healthKey }
                            } else {
                                null
                            }
                        )
                    }
                    if (state.isOnline && state.statusFreshness == StatusFreshness.STALE) {
                        Spacer(Modifier.height(AppSpacing.medium))
                        AppBanner(
                            message = "마지막 상태 응답 이후 ${state.statusAgeSeconds ?: 0}초가 지났습니다. 표시된 수치는 최신 값이 아닐 수 있습니다.",
                            tone = StatusTone.WARNING
                        )
                    }
                    if (state.isOnline) {
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
                    }
                    Spacer(Modifier.height(AppSpacing.section))
                    SectionHeader("빠른 작업")
                }
            }

            item {
                DashboardAction(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi 설정",
                    description = "사용할 공유기에 연결",
                    enabled = state.isOnline && state.capabilities.wifiProvisioning,
                    onClick = onNetworkSettingsClick
                )
                DashboardDivider()
                DashboardAction(
                    icon = Icons.Default.FolderOpen,
                    title = "저장 데이터 확인",
                    description = "수집 파일과 폴더 확인",
                    enabled = state.isOnline && state.fullControlAvailable &&
                        state.capabilities.fileBrowsing,
                    onClick = onStorageClick
                )
                DashboardDivider()
                DashboardAction(
                    icon = Icons.Default.CloudUpload,
                    title = "업로드 확인",
                    description = if (activeUploads.isNotEmpty()) {
                        "${activeUploads.size}개 업로드 진행 중"
                    } else {
                        "업로드 중이 아닙니다"
                    },
                    enabled = state.isOnline && state.fullControlAvailable &&
                        state.capabilities.uploads && activeUploads.isNotEmpty(),
                    onClick = onUploadQueueClick
                )
                DashboardDivider()
                DashboardAction(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = "사용 가능한 작업목록",
                    description = null,
                    enabled = state.isOnline && state.fullControlAvailable &&
                        state.capabilities.pipelines,
                    onClick = onPipelinesClick
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = AppSpacing.screen)) {
                    Spacer(Modifier.height(AppSpacing.section))
                    SectionHeader("장치 제어")
                    Spacer(Modifier.height(AppSpacing.medium))
                    if (state.capabilities.fanControl) {
                        FanControlCard(
                            state = state,
                            onRefresh = onRefreshFan,
                            onSetAuto = onSetFanAuto,
                            onSetManual = onSetFanManual
                        )
                        Spacer(Modifier.height(AppSpacing.medium))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                    ) {
                        OutlinedButton(
                            onClick = onStartSystem,
                            modifier = Modifier.weight(1f),
                            enabled = state.isOnline && state.capabilities.systemControlConfigured &&
                                !state.operationInProgress
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("시작", modifier = Modifier.padding(start = AppSpacing.xSmall))
                        }
                        OutlinedButton(
                            onClick = onStopSystem,
                            modifier = Modifier.weight(1f),
                            enabled = state.isOnline && state.capabilities.systemControlConfigured &&
                                !state.operationInProgress
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text("중지", modifier = Modifier.padding(start = AppSpacing.xSmall))
                        }
                        OutlinedButton(
                            onClick = onRestartServices,
                            modifier = Modifier.weight(1f),
                            enabled = state.isOnline && state.capabilities.systemControlConfigured &&
                                !state.operationInProgress
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Text("재시작", modifier = Modifier.padding(start = AppSpacing.xSmall))
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.medium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                    ) {
                        OutlinedButton(
                            onClick = { pendingPowerAction = PowerAction.REBOOT },
                            modifier = Modifier.weight(1f),
                            enabled = state.isOnline && state.capabilities.powerCommandsEnabled &&
                                !state.operationInProgress
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Text("재부팅", modifier = Modifier.padding(start = AppSpacing.small))
                        }
                        OutlinedButton(
                            onClick = { pendingPowerAction = PowerAction.SHUTDOWN },
                            modifier = Modifier.weight(1f),
                            enabled = state.isOnline && state.capabilities.powerCommandsEnabled &&
                                !state.operationInProgress
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
private fun FanControlCard(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onSetAuto: () -> Unit,
    onSetManual: (Int) -> Unit
) {
    val fan = state.fanStatus
    val minimum = fan?.minimumManualPercent ?: 20
    var manualPercent by rememberSaveable(fan?.percent, minimum) {
        mutableStateOf((fan?.percent ?: 40).coerceIn(minimum, 100).toFloat())
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(AppSpacing.large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("FAN 속도", fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            fan == null -> "상태 확인 중"
                            !fan.available -> "이 장치에서는 FAN 제어를 사용할 수 없습니다"
                            fan.mode == "AUTO" -> "자동 온도 제어"
                            else -> "수동 ${fan.percent ?: manualPercent.roundToInt()}%" +
                                (fan.rpm?.let { " · ${it} RPM" } ?: "")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.fanLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "FAN 상태 새로고침")
                }
            }
            if (fan?.available == true) {
                Slider(
                    value = manualPercent,
                    onValueChange = {
                        manualPercent = ((it / 10f).roundToInt() * 10)
                            .coerceIn(minimum, 100).toFloat()
                    },
                    valueRange = minimum.toFloat()..100f,
                    steps = ((100 - minimum) / 10 - 1).coerceAtLeast(0),
                    enabled = !state.fanLoading
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    OutlinedButton(
                        onClick = onSetAuto,
                        modifier = Modifier.weight(1f),
                        enabled = fan.autoAvailable && !state.fanLoading
                    ) { Text("자동") }
                    Button(
                        onClick = { onSetManual(manualPercent.roundToInt()) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.fanLoading
                    ) { Text("수동 ${manualPercent.roundToInt()}% 적용") }
                }
            }
            state.fanError?.let {
                Spacer(Modifier.height(AppSpacing.small))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.fanLoading) {
                Spacer(Modifier.height(AppSpacing.small))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HealthOverview(
    state: DashboardUiState,
    health: DashboardHealth,
    onDismiss: (() -> Unit)?
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
                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(
                        label = if (state.isOnline) "온라인" else "오프라인",
                        tone = tone
                    )
                    if (onDismiss != null) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "상태 알림 닫기")
                        }
                    }
                }
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
private fun ConnectionModeSummary(
    state: DashboardUiState
) {
    val wifiConnected = state.isOnline &&
        (state.status.wifiConnected || state.fullControlAvailable)
    val title = when {
        !state.isOnline -> "오프라인"
        wifiConnected -> "Wi-Fi 연결"
        else -> "Wi-Fi 미연결"
    }
    val tone = when {
        !state.isOnline -> StatusTone.WARNING
        wifiConnected -> StatusTone.SUCCESS
        else -> StatusTone.INFO
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (wifiConnected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            Icon(
                if (state.isOnline) Icons.Default.Wifi else Icons.Default.LinkOff,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            StatusBadge(if (state.isOnline) "온라인" else "오프라인", tone)
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
    description: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent = description?.let {
            {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
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

private fun UploadJobState.isActiveUploadState(): Boolean = this in setOf(
    UploadJobState.QUEUED,
    UploadJobState.SCANNING,
    UploadJobState.UPLOADING
)

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

private fun dashboardHealthKey(health: DashboardHealth): String = buildString {
    append(health.level.name)
    append('|')
    append(health.title)
    append('|')
    append(health.issues.joinToString("|"))
}
