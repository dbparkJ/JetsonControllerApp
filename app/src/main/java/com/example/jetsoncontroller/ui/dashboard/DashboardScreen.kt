package com.example.jetsoncontroller.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.DeviceContextHeader
import com.example.jetsoncontroller.ui.components.GeoDataRow
import com.example.jetsoncontroller.ui.components.GeoFreshnessLabel
import com.example.jetsoncontroller.ui.components.GeoRowDivider
import com.example.jetsoncontroller.ui.components.GeoSection
import com.example.jetsoncontroller.ui.components.GeoSectionHeader
import com.example.jetsoncontroller.ui.components.GeoStatusTile
import com.example.jetsoncontroller.ui.components.MetricCard
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.connection.userConnectionStage
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.GeoBreakpoint
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.GeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.TextButton
import kotlin.math.roundToInt

/**
 * 현장 홈.
 *
 * The screen answers four questions, in this order, and nothing else competes for the
 * top of the page:
 *
 *   1. 어느 장치를 조작하고 있나?   → the persistent device header
 *   2. 지금 수집해도 되나?          → the independent status axes
 *   3. 다음에 무엇을 해야 하나?      → one next action, stated as a sentence
 *   4. 끝난 자료는 어디에 있나?      → results and transfer, kept visibly separate
 *
 * What was removed and why:
 *
 *  - The standing instructional banner ("작업 시작에서 프로젝트·조사 구간을 선택하고…") was a
 *    manual pinned above the content. Its guidance now lives inside the next-action card,
 *    where it is specific to the current state instead of being permanent furniture.
 *  - Status tiles no longer sit on tinted blocks. Four coloured rectangles side by side
 *    made the screen look alarming when nothing was wrong, and made a real warning hard
 *    to pick out. Colour now lives in the icon and the value, on a neutral surface.
 *  - The four axes are deliberately not summarised into one overall "정상" verdict. Device
 *    connection, collection, device storage and server receipt are independent facts, and
 *    collapsing them is the single misreading that loses a survey.
 */
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
    val c = LocalGeoColors.current
    val health = assessDashboardHealth(
        state.status,
        if (state.status == com.example.jetsoncontroller.model.JetsonStatus()) StatusFreshness.UNKNOWN else state.statusFreshness,
        pipelines,
        uploads
    )
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
    val connectionStage = userConnectionStage(state.isOnline, state.transportType)

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            DeviceContextHeader(
                title = "도로관리장치 제어",
                deviceName = state.deviceName,
                showLogo = true,
                connectionLabel = connectionStage.label,
                connectionTone = connectionStage.tone,
                onDevices = onBack,
                unreadCount = unreadAlertCount,
                onAlerts = onAlertsClick
            )
        },
        bottomBar = { ControlNavigationBar(ControlSection.OVERVIEW, onSectionSelected) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = GeoSpace.gutter,
                end = GeoSpace.gutter,
                top = GeoSpace.lg,
                bottom = GeoSpace.xxxl
            ),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.section)
        ) {
            // ---- 1. 다음 행동 ---------------------------------------------------------
            item { NextActionCard(nextAction, onNextAction) }

            // ---- 2. 지금 상태 (독립 축) -------------------------------------------------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                    GeoSectionHeader(
                        title = "지금 상태",
                        eyebrow = "각 항목은 서로 다른 사실입니다",
                        trailing = {
                            if (state.statusFreshness != StatusFreshness.CURRENT) {
                                StatusBadge("값 미확인", StatusTone.UNKNOWN)
                            }
                        }
                    )
                    OperationalStatusGrid(
                        signals = listOf(
                            summary.connection,
                            summary.collection,
                            summary.internet,
                            summary.positioning
                        )
                    )
                }
            }

            // ---- 3. 현재 수집 ---------------------------------------------------------
            item {
                CollectionSection(
                    task = task,
                    activeCount = active.size,
                    tasksConfirmed = tasksConfirmed,
                    taskObservedAt = taskObservedAt,
                    pendingAction = task?.let { pendingTaskActions[it.id] },
                    onClick = onPipelinesClick
                )
            }

            // ---- 4. 주의가 필요한 항목 --------------------------------------------------
            if (state.isOnline && !hiddenHealth &&
                !(healthKeys.isNotEmpty() && dismissedHealthKeys.containsAll(healthKeys))
            ) {
                item {
                    SwipeDismissibleHealthOverview(state, health, onDismiss = {
                        hiddenHealth = true
                        onHealthDismissalsChange(
                            dismissDashboardHealth(dismissedHealthKeys, healthDeviceId, health)
                        )
                    }, showCloseButton = true)
                }
            }

            // ---- 5. 시작 준비 ---------------------------------------------------------
            item {
                ReadinessSection(
                    state = state,
                    onSensorsClick = onSensorsClick,
                    onStorageClick = onStorageClick,
                    onCameraClick = onCameraClick,
                    onGnssClick = onGnssClick
                )
            }

            // ---- 6. 결과와 전송 -------------------------------------------------------
            item {
                GeoSection {
                    GeoSectionHeader(
                        title = "결과와 전송",
                        eyebrow = "장치 저장과 서버 수신은 별개입니다"
                    )
                    Text(
                        "장치에 원본이 남았는지와 서버가 실제로 받았는지는 각각 확인해야 합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted
                    )
                    GeoRowDivider()
                    NavigationRow(
                        icon = Icons.Default.Storage,
                        title = "장치 자료",
                        detail = "Jetson에 저장된 실제 파일과 폴더",
                        onClick = onStorageClick
                    )
                    NavigationRow(
                        icon = Icons.Default.CloudUpload,
                        title = "전송 · 수신 확인",
                        detail = "전송 내역 ${uploads.size}개 · 서버 수신 검증 결과",
                        onClick = onUploadQueueClick
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Next action
// =====================================================================================

/**
 * The single most prominent element on the home screen.
 *
 * It is a sentence, not a dashboard widget: a state, what it means, and one button. The
 * brand surface is used here and nowhere else on this screen, so that "what do I do now"
 * is the one thing colour is spent on.
 */
@Composable
private fun NextActionCard(action: HomeNextAction, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(
        color = c.hero,
        contentColor = c.heroText,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(GeoSpace.xl),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Text(action.eyebrow, style = GeoType.eyebrow, color = c.heroMuted)
            Text(action.title, style = MaterialTheme.typography.headlineSmall)
            Text(action.detail, style = MaterialTheme.typography.bodyMedium, color = c.heroMuted)
            Spacer(Modifier.height(GeoSpace.xs))
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.primaryAction),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = c.heroText,
                    contentColor = c.hero
                )
            ) { Text(action.buttonLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

// =====================================================================================
// Status axes
// =====================================================================================

/**
 * Lays the independent status axes out against the *window* width, per Android's
 * adaptive guidance, rather than guessing from the device type. A resized window on a
 * foldable gets the narrow layout it deserves.
 */
@Composable
private fun OperationalStatusGrid(signals: List<OperationalSignal>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= GeoBreakpoint.expanded -> 4
            maxWidth >= GeoBreakpoint.medium -> 2
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
            signals.chunked(columns).forEach { rowSignals ->
                Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                    rowSignals.forEach { signal ->
                        Box(Modifier.weight(1f)) {
                            GeoStatusTile(
                                title = signal.title,
                                value = signal.value,
                                detail = signal.detail,
                                tone = signal.tone
                            )
                        }
                    }
                    repeat(columns - rowSignals.size) { Box(Modifier.weight(1f)) {} }
                }
            }
        }
    }
}

// =====================================================================================
// Current collection
// =====================================================================================

@Composable
private fun CollectionSection(
    task: ManagedPipeline?,
    activeCount: Int,
    tasksConfirmed: Boolean,
    taskObservedAt: Long?,
    pendingAction: String?,
    onClick: () -> Unit
) {
    val c = LocalGeoColors.current
    val tone = when {
        activeCount > 1 -> StatusTone.WARNING
        !tasksConfirmed -> StatusTone.UNKNOWN
        task != null -> StatusTone.SUCCESS
        else -> StatusTone.INFO
    }
    GeoSection(tone = tone) {
        GeoSectionHeader(
            title = task?.label ?: if (tasksConfirmed) "실행 중인 수집 없음" else "수집 상태 미확인",
            eyebrow = "현재 수집",
            trailing = {
                StatusBadge(
                    when {
                        activeCount > 1 -> "실행 ${activeCount}개"
                        !tasksConfirmed -> "미확인"
                        task != null -> "수집 중"
                        else -> "대기"
                    },
                    tone
                )
            }
        )
        Text(
            task?.let {
                com.example.jetsoncontroller.ui.pipelines.taskStateLabel(it.state, tasksConfirmed, pendingAction)
            } ?: if (tasksConfirmed) {
                "새 수집은 프로젝트·구간 선택과 시작 전 점검을 거쳐 시작합니다."
            } else {
                "앱 연결이 끊겨도 Jetson의 수집은 계속될 수 있습니다. 장치에 다시 연결해 실제 상태를 확인하세요."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted
        )
        if (activeCount > 1) {
            Text(
                "같은 장치에서 실행이 ${activeCount}개로 보입니다. 새로 시작하기 전에 중복 여부를 확인하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.warning
            )
        }
        taskObservedAt?.let {
            GeoFreshnessLabel(
                "마지막 확인 · " + java.text.DateFormat.getTimeInstance().format(java.util.Date(it)),
                stale = !tasksConfirmed
            )
        }
        OutlinedButton(
            shape = MaterialTheme.shapes.small,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
        ) { Text(if (tasksConfirmed) "수집 상세 보기" else "실행 상태 다시 확인") }
    }
}

// =====================================================================================
// Readiness
// =====================================================================================

@Composable
private fun ReadinessSection(
    state: DashboardUiState,
    onSensorsClick: () -> Unit,
    onStorageClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGnssClick: () -> Unit
) {
    val c = LocalGeoColors.current
    val compact = LocalDensity.current.fontScale <= 1.3f

    val sensorUnknown = !state.isOnline || !state.status.sensorTelemetryAvailable
    val sensorValue = when {
        sensorUnknown -> "수신 상태 미확인"
        !state.status.sensorTelemetryFresh -> "마지막 수신 · 지연"
        else -> "${listOf(
            state.status.cameraSensor.active,
            state.status.gnssSensor.active,
            state.status.imuSensor.active
        ).count { it }}개 수신"
    }
    val storageFresh = state.isOnline && state.statusFreshness == StatusFreshness.CURRENT
    val storageValue = if (storageFresh) {
        state.status.metricDisplay("storagePercent", "${state.status.storagePercent}% 사용")
    } else {
        "현재 용량 미확인"
    }

    GeoSection {
        GeoSectionHeader(
            title = "시작 준비",
            eyebrow = "수집 전에 확인할 항목",
            trailing = { TextButton(onClick = onSensorsClick) { Text("센서 전체") } }
        )
        GeoDataRow(
            label = "센서",
            supporting = "카메라 · GNSS · IMU",
            value = sensorValue,
            valueUnavailable = sensorUnknown,
            trailing = {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.muted)
            }
        )
        GeoRowDivider()
        GeoDataRow(
            label = "저장 공간",
            supporting = "실제 파일과 폴더",
            value = storageValue,
            valueUnavailable = !storageFresh,
            trailing = {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.muted)
            }
        )
        GeoRowDivider()
        Text(
            "프리뷰가 보이는 것과 원본이 녹화되는 것은 다릅니다.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )
        if (compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                QuickCheckButton("카메라 확인", Icons.Default.CameraAlt, onCameraClick, Modifier.weight(1f))
                QuickCheckButton("GNSS 위치", Icons.Default.Explore, onGnssClick, Modifier.weight(1f))
            }
        } else {
            QuickCheckButton("카메라 확인", Icons.Default.CameraAlt, onCameraClick, Modifier.fillMaxWidth())
            QuickCheckButton("GNSS 위치", Icons.Default.Explore, onGnssClick, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QuickCheckButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        shape = MaterialTheme.shapes.small,
        onClick = onClick,
        modifier = modifier.heightIn(min = GeoSize.secondaryAction)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(GeoSize.iconSm))
        Spacer(Modifier.size(GeoSpace.sm))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    val c = LocalGeoColors.current
    Surface(
        onClick = onClick,
        color = c.surface,
        contentColor = c.ink,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.minTouchTarget)
    ) {
        Row(
            Modifier.padding(vertical = GeoSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(GeoSize.iconLg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.muted)
        }
    }
}

// =====================================================================================
// Health
// =====================================================================================

@Composable
private fun SwipeDismissibleHealthOverview(
    state: DashboardUiState,
    health: DashboardHealth,
    onDismiss: () -> Unit,
    showCloseButton: Boolean
) {
    val c = LocalGeoColors.current
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
                    .background(c.sectionRaised)
                    .padding(horizontal = GeoSpace.lg),
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
private fun HealthOverview(
    state: DashboardUiState,
    health: DashboardHealth,
    onDismiss: (() -> Unit)?
) {
    val c = LocalGeoColors.current
    val tone = when (health.level) {
        DashboardHealthLevel.HEALTHY -> StatusTone.SUCCESS
        DashboardHealthLevel.ATTENTION -> StatusTone.WARNING
        DashboardHealthLevel.UNKNOWN -> StatusTone.UNKNOWN
    }
    val icon = when (health.level) {
        DashboardHealthLevel.HEALTHY -> Icons.Default.CheckCircle
        DashboardHealthLevel.ATTENTION -> Icons.Default.WarningAmber
        DashboardHealthLevel.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
    }
    val visuals = com.example.jetsoncontroller.ui.components.statusVisuals(tone)

    GeoSection(tone = tone) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = visuals.content,
                modifier = Modifier.size(GeoSize.iconLg)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(health.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    health.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted
                )
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(GeoSize.minTouchTarget)) {
                    Icon(Icons.Default.Close, contentDescription = "상태 알림 닫기", tint = c.muted)
                }
            }
        }
        if (health.issues.size > 1) {
            GeoRowDivider()
            health.issues.drop(1).forEach { issue ->
                Text("• $issue", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

// =====================================================================================
// Admin-side cards, unchanged in behaviour
// =====================================================================================

@Composable
internal fun FanControlCard(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onSetAuto: () -> Unit,
    onSetManual: (Int) -> Unit
) {
    val c = LocalGeoColors.current
    val fan = state.fanStatus
    val minimum = fan?.minimumManualPercent ?: 20
    var manualPercent by rememberSaveable(fan?.percent, minimum) {
        mutableStateOf((fan?.percent ?: 40).coerceIn(minimum, 100).toFloat())
    }
    GeoSection {
        GeoSectionHeader(
            title = "FAN 속도",
            trailing = {
                IconButton(onClick = onRefresh, enabled = state.isOnline && !state.fanLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "FAN 상태 새로고침")
                }
            }
        )
        Text(
            when {
                fan == null -> "상태 확인 중"
                !fan.available -> "이 장치에서는 FAN 제어를 사용할 수 없습니다"
                fan.mode == "AUTO" -> "자동 온도 제어"
                else -> "수동 ${fan.percent ?: manualPercent.roundToInt()}%" +
                    (fan.rpm?.let { " · ${it} RPM" } ?: "")
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )
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
                horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)
            ) {
                OutlinedButton(
                    shape = MaterialTheme.shapes.small,
                    onClick = onSetAuto,
                    modifier = Modifier.weight(1f).heightIn(min = GeoSize.secondaryAction),
                    enabled = state.isOnline && fan.autoAvailable && !state.fanLoading
                ) { Text("자동") }
                Button(
                    shape = MaterialTheme.shapes.small,
                    onClick = { onSetManual(manualPercent.roundToInt()) },
                    modifier = Modifier.weight(1f).heightIn(min = GeoSize.secondaryAction),
                    enabled = state.isOnline && !state.fanLoading
                ) { Text("수동 ${manualPercent.roundToInt()}% 적용") }
            }
        }
        state.fanError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger)
        }
        if (state.fanLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun MetricsGrid(state: DashboardUiState) {
    val c = LocalGeoColors.current
    if (state.statusFreshness == StatusFreshness.UNKNOWN ||
        state.status == com.example.jetsoncontroller.model.JetsonStatus()
    ) {
        Text("장비 지표 미확인", style = MaterialTheme.typography.bodyLarge, color = c.unknown)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)
    ) {
        MetricCard("CPU", state.status.metricDisplay("cpuPercent", "${state.status.cpuPercent}%"), Modifier.weight(1f))
        MetricCard("GPU", state.status.metricDisplay("gpuPercent", "${state.status.gpuPercent}%"), Modifier.weight(1f))
    }
    Spacer(Modifier.height(GeoSpace.sm))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)
    ) {
        MetricCard("온도", state.status.metricDisplay("temperatureC", "${state.status.temperatureC} C"), Modifier.weight(1f))
        MetricCard("저장 공간", state.status.metricDisplay("storagePercent", "${state.status.storagePercent}%"), Modifier.weight(1f))
    }
    Spacer(Modifier.height(GeoSpace.sm))
    GeoSection {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("메모리", style = MaterialTheme.typography.labelMedium, color = c.muted)
            Text(
                state.status.metricDisplay(
                    "ramUsedMb",
                    "${formatMemory(state.status.ramUsedMb)} / ${formatMemory(state.status.ramTotalMb)}"
                ),
                style = GeoType.numericSmall
            )
        }
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

private fun formatMemory(megabytes: Int): String = when {
    megabytes >= 1024 -> "%.1f GB".format(megabytes / 1024f)
    else -> "$megabytes MB"
}
