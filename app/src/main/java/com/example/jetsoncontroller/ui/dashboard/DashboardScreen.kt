package com.example.jetsoncontroller.ui.dashboard

import androidx.compose.ui.graphics.Color
import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.Button
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import com.example.jetsoncontroller.ui.connection.UserConnectionStage
import com.example.jetsoncontroller.ui.connection.userConnectionStage
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
    onBack: () -> Unit,
    healthDeviceId: String = state.deviceName,
    dismissedHealthKeys: Set<String> = emptySet(),
    onHealthDismissalsChange: (Set<String>) -> Unit = {},
    onSensorsClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onGnssClick: () -> Unit = {},
    onConnectionClick: () -> Unit = onBack,
    tasksConfirmed: Boolean = false,
    taskObservedAt: Long? = null,
    pendingTaskActions: Map<String, String> = emptyMap()
) {
    val c = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current
    val health = assessDashboardHealth(state.status,
        if (state.status == com.example.jetsoncontroller.model.JetsonStatus()) StatusFreshness.UNKNOWN else state.statusFreshness,
        pipelines, uploads)
    val healthKeys = dashboardHealthDismissalKeys(healthDeviceId, health)
    var hiddenHealth by rememberSaveable(healthDeviceId) { mutableStateOf(false) }
    val active = pipelines.filter {
        tasksConfirmed && it.state == PipelineState.RUNNING && !it.activeRunId.isNullOrBlank()
    }
    val task = active.firstOrNull()
    val summary = operationalSummary(state, pipelines, uploads, tasksConfirmed)
    val nextAction = summary.nextAction
    val onNextAction = when (nextAction.destination) {
        HomeActionDestination.CONNECTION -> onConnectionClick
        HomeActionDestination.PIPELINES -> onPipelinesClick
        HomeActionDestination.SENSORS -> onSensorsClick
        HomeActionDestination.STORAGE -> onStorageClick
    }
    Scaffold(
        topBar = {
            com.example.jetsoncontroller.ui.components.DeviceContextHeader(
                title = "도로관리장치 제어", deviceName = state.deviceName, showLogo = true,
                connectionLabel = userConnectionStage(state.isOnline, state.transportType).label,
                onDevices = onBack, unreadCount = unreadAlertCount, onAlerts = onAlertsClick)
        },
        bottomBar = { ControlNavigationBar(ControlSection.OVERVIEW, onSectionSelected) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "현장 상태",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                OperationalStatusGrid(
                    signals = listOf(
                        summary.connection,
                        summary.collection,
                        summary.internet,
                        summary.positioning
                    )
                )
            }
            item {
                NextActionAndCollection(
                    nextAction = nextAction,
                    task = task,
                    activeCount = active.size,
                    tasksConfirmed = tasksConfirmed,
                    taskObservedAt = taskObservedAt,
                    pendingAction = task?.let { pendingTaskActions[it.id] },
                    onNextAction = onNextAction,
                    onPipelinesClick = onPipelinesClick
                )
            }
            item {
                AppBanner(
                    message = "프로젝트·조사 구간은 현재 앱에 연결되지 않았습니다. 실제 운영 전 배정 정보를 별도로 확인하세요.",
                    tone = StatusTone.WARNING
                )
            }
            item { SectionHeader("시작 준비", trailing = { TextButton(onClick = onSensorsClick) { Text("센서 전체 보기") } }) }
            if (state.isOnline && !hiddenHealth &&
                !(healthKeys.isNotEmpty() && dismissedHealthKeys.containsAll(healthKeys))) {
                item {
                    SwipeDismissibleHealthOverview(state, health, onDismiss = {
                        hiddenHealth = true
                        onHealthDismissalsChange(dismissDashboardHealth(dismissedHealthKeys, healthDeviceId, health))
                    }, showCloseButton = true)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val compact = androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f
                    val sensorValue = if (!state.isOnline || !state.status.sensorTelemetryAvailable) "수신 상태 미확인"
                        else if (!state.status.sensorTelemetryFresh) "마지막 수신 · 지연"
                        else "${listOf(state.status.cameraSensor.active, state.status.gnssSensor.active, state.status.imuSensor.active).count { it }}개 수신"
                    val storageValue = if (state.isOnline && state.statusFreshness == StatusFreshness.CURRENT)
                        state.status.metricDisplay("storagePercent", "${state.status.storagePercent}% 사용") else "현재 용량 미확인"
                    if (compact) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) { ReadinessTile("센서", sensorValue, "카메라 · GNSS · IMU", onSensorsClick) }
                            Box(Modifier.weight(1f)) { ReadinessTile("저장 공간", storageValue, "파일과 폴더", onStorageClick) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onCameraClick, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("카메라 확인") }
                            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onGnssClick, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("GNSS 위치") }
                        }
                    } else {
                        ReadinessTile("센서", sensorValue, "카메라 · GNSS · IMU", onSensorsClick)
                        ReadinessTile("저장 공간", storageValue, "실제 파일과 폴더 확인", onStorageClick)
                        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onCameraClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("카메라 확인") }
                        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onGnssClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("GNSS 위치") }
                    }
                }
            }
            item { ReadinessTile("저장 · 전송 증거", "전송 내역 ${uploads.size}개", "Jetson 원본과 서버 수신 검증을 각각 확인", onUploadQueueClick, color = c.sectionRaised) }
        }
    }
}

@Composable
private fun OperationalStatusGrid(signals: List<OperationalSignal>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        when {
            maxWidth >= 760.dp -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                signals.forEach { signal ->
                    Box(Modifier.weight(1f)) { OperationalSignalCard(signal) }
                }
            }
            maxWidth >= 480.dp -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                signals.chunked(2).forEach { rowSignals ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowSignals.forEach { signal ->
                            Box(Modifier.weight(1f)) { OperationalSignalCard(signal) }
                        }
                    }
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                signals.forEach { OperationalSignalCard(it) }
            }
        }
    }
}

@Composable
private fun OperationalSignalCard(signal: OperationalSignal) {
    val colors = when (signal.tone) {
        StatusTone.SUCCESS -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.successBg to
            com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.success
        StatusTone.WARNING -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.warningBg to
            com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.warning
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.INFO -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.infoBg to
            com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.info
    }
    Surface(
        color = colors.first,
        contentColor = colors.second,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 116.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(signal.title, style = MaterialTheme.typography.labelLarge)
            Text(signal.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(signal.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NextActionAndCollection(
    nextAction: HomeNextAction,
    task: ManagedPipeline?,
    activeCount: Int,
    tasksConfirmed: Boolean,
    taskObservedAt: Long?,
    pendingAction: String?,
    onNextAction: () -> Unit,
    onPipelinesClick: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { NextActionCard(nextAction, onNextAction) }
                Box(Modifier.weight(1f)) {
                    CollectionCard(task, activeCount, tasksConfirmed, taskObservedAt, pendingAction, onPipelinesClick)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NextActionCard(nextAction, onNextAction)
                CollectionCard(task, activeCount, tasksConfirmed, taskObservedAt, pendingAction, onPipelinesClick)
            }
        }
    }
}

@Composable
private fun NextActionCard(action: HomeNextAction, onClick: () -> Unit) {
    val c = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current
    Surface(
        color = c.hero,
        contentColor = c.heroText,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(action.eyebrow, style = MaterialTheme.typography.labelLarge, color = c.heroMuted)
            Text(action.title, style = MaterialTheme.typography.headlineSmall)
            Text(action.detail, style = MaterialTheme.typography.bodyMedium, color = c.heroMuted)
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = c.primary,
                    contentColor = c.onPrimary
                )
            ) { Text(action.buttonLabel) }
        }
    }
}

@Composable
private fun CollectionCard(
    task: ManagedPipeline?,
    activeCount: Int,
    tasksConfirmed: Boolean,
    taskObservedAt: Long?,
    pendingAction: String?,
    onClick: () -> Unit
) {
    val c = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current
    Surface(
        color = c.sectionRaised,
        contentColor = c.ink,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("현재 작업", style = MaterialTheme.typography.labelLarge, color = c.muted)
            Text(
                task?.label ?: if (tasksConfirmed) "실행 중인 수집 없음" else "작업 상태 확인 필요",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                task?.let {
                    com.example.jetsoncontroller.ui.pipelines.taskStateLabel(it.state, tasksConfirmed, pendingAction)
                } ?: if (tasksConfirmed) {
                    "새 수집은 작업 선택과 최종 점검 후 시작합니다."
                } else {
                    "앱 연결이 끊겨도 Jetson 수집이 계속될 수 있습니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted
            )
            if (activeCount > 1) {
                Text("실행 ${activeCount}개 · 중복 여부 확인 필요", color = MaterialTheme.colorScheme.error)
            }
            taskObservedAt?.let {
                Text(
                    "마지막 관찰 · " + java.text.DateFormat.getTimeInstance().format(java.util.Date(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted
                )
            }
            OutlinedButton(
                shape = MaterialTheme.shapes.small,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("수집 상세 보기") }
        }
    }
}

@Composable
private fun ReadinessTile(title: String, value: String, detail: String, onClick: () -> Unit,
    color: Color = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.sectionSoft) {
    Surface(onClick = onClick, color = color, contentColor = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.ink, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwipeDismissibleHealthOverview(
    state: DashboardUiState,
    health: DashboardHealth,
    onDismiss: () -> Unit,
    showCloseButton: Boolean
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = AppSpacing.large),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Close, contentDescription = "상태 카드 닫기")
            }
        },
        modifier = Modifier.testTag("dashboard-health-card")
    ) {
        HealthOverview(
            state = state,
            health = health,
            onDismiss = onDismiss.takeIf { showCloseButton }
        )
    }
}

@Composable
internal fun FanControlCard(
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
                IconButton(onClick = onRefresh, enabled = state.isOnline && !state.fanLoading) {
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
                    enabled = state.isOnline && !state.fanLoading
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    OutlinedButton(shape = MaterialTheme.shapes.small,
                        onClick = onSetAuto,
                        modifier = Modifier.weight(1f),
                        enabled = state.isOnline && fan.autoAvailable && !state.fanLoading
                    ) { Text("자동") }
                    Button(shape = MaterialTheme.shapes.small,
                        onClick = { onSetManual(manualPercent.roundToInt()) },
                        modifier = Modifier.weight(1f),
                        enabled = state.isOnline && !state.fanLoading
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
        StatusTone.SUCCESS -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.successBg
        StatusTone.WARNING -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.warningBg
        else -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.infoBg
    }
    val content = when (tone) {
        StatusTone.SUCCESS -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.success
        StatusTone.WARNING -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.warning
        else -> com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.info
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
                        label = userConnectionStage(state.isOnline, state.transportType).label,
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
internal fun MetricsGrid(state: DashboardUiState) {
    if (state.statusFreshness == StatusFreshness.UNKNOWN || state.status == com.example.jetsoncontroller.model.JetsonStatus()) {
        Text("장비 지표 미확인", style = MaterialTheme.typography.bodyLarge)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        MetricCard("CPU", state.status.metricDisplay("cpuPercent", "${state.status.cpuPercent}%"), Modifier.weight(1f))
        MetricCard("GPU", state.status.metricDisplay("gpuPercent", "${state.status.gpuPercent}%"), Modifier.weight(1f))
    }
    Spacer(Modifier.height(AppSpacing.small))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        MetricCard("온도", state.status.metricDisplay("temperatureC", "${state.status.temperatureC} C"), Modifier.weight(1f))
        MetricCard("저장 공간", state.status.metricDisplay("storagePercent", "${state.status.storagePercent}%"), Modifier.weight(1f))
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
                    state.status.metricDisplay("ramUsedMb", "${formatMemory(state.status.ramUsedMb)} / ${formatMemory(state.status.ramTotalMb)}"),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.height(AppSpacing.small))
            val progress = if (state.status.ramTotalMb > 0) {
                state.status.ramUsedMb.toFloat() / state.status.ramTotalMb
            } else {
                0f
            }
            if (state.status.metricIsValid("ramUsedMb") && state.status.metricIsValid("ramTotalMb")) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatMemory(megabytes: Int): String = when {
    megabytes >= 1024 -> "%.1f GB".format(megabytes / 1024f)
    else -> "$megabytes MB"
}
