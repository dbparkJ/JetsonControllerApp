package com.example.jetsoncontroller.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.alerts.AlertIconButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 홈.
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
    pendingTaskActions: Map<String, String> = emptyMap(),
    stagePlan: com.example.jetsoncontroller.ui.field.FieldStagePlan? = null,
    onStageAction: (com.example.jetsoncontroller.ui.field.FieldDestination) -> Unit = {},
    recentRuns: List<TaskRun> = emptyList(),
    latestRun: PipelineRun? = null,
    recentHistoryCurrent: Boolean = tasksConfirmed,
    onRecentRunsClick: () -> Unit = onPipelinesClick,
    onUploadJobClick: (UploadJob) -> Unit = {}
) {
    val c = LocalGeoColors.current
    val statusFresh = state.isOnline && state.statusFreshness == StatusFreshness.CURRENT
    val health = assessDashboardHealth(
        state.status,
        if (state.status == com.example.jetsoncontroller.model.JetsonStatus()) StatusFreshness.UNKNOWN
        else state.statusFreshness,
        pipelines,
        uploads
    )
    val healthKeys = dashboardHealthDismissalKeys(healthDeviceId, health)
    var hiddenHealth by rememberSaveable(healthDeviceId) { mutableStateOf(false) }
    val active = pipelines.filter {
        tasksConfirmed && it.state == PipelineState.RUNNING && !it.activeRunId.isNullOrBlank()
    }
    val summary = operationalSummary(state, pipelines, uploads, tasksConfirmed)
    val resolvedPlan = stagePlan
    val fallbackAction = summary.nextAction
    val fallbackClick = when (fallbackAction.destination) {
        HomeActionDestination.CONNECTION -> onConnectionClick
        HomeActionDestination.PIPELINES -> onPipelinesClick
        HomeActionDestination.SENSORS -> onSensorsClick
        HomeActionDestination.STORAGE -> onStorageClick
    }
    val connectionStage = userConnectionStage(state.isOnline, state.transportType)
    val latestRuns = homeRecentRuns(latestRun, recentRuns, uploads)
    val summaryUpload = homeUploadSummaryJob(uploads)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tabletLayout = maxWidth >= GeoBreakpoint.medium && LocalDensity.current.fontScale <= 1.3f
        Scaffold(
            containerColor = c.canvas,
            topBar = {
                if (tabletLayout) {
                    HomeTabletBar(
                        deviceName = state.deviceName,
                        unreadCount = unreadAlertCount,
                        onAlertsClick = onAlertsClick
                    )
                } else {
                    HomeBrandBar(
                        unreadCount = unreadAlertCount,
                        onAlertsClick = onAlertsClick
                    )
                }
            },
            bottomBar = { ControlNavigationBar(ControlSection.OVERVIEW, onSectionSelected) }
        ) { padding ->
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                val railVisible = com.example.jetsoncontroller.ui.components
                    .LocalControlNavigationRailVisible.current
                val wideRailLayout = tabletLayout && railVisible && maxWidth >= 1_100.dp
                val showHealth = state.isOnline && health.level == DashboardHealthLevel.ATTENTION &&
                    !hiddenHealth && !(healthKeys.isNotEmpty() && dismissedHealthKeys.containsAll(healthKeys))
                val dismissHealth = {
                    hiddenHealth = true
                    onHealthDismissalsChange(
                        dismissDashboardHealth(dismissedHealthKeys, healthDeviceId, health)
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = if (tabletLayout) GeoSpace.xxl else GeoSpace.gutter,
                        end = if (tabletLayout) GeoSpace.xxl else GeoSpace.gutter,
                        top = GeoSpace.sm,
                        bottom = GeoSpace.xxxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(GeoSpace.xxl)
                ) {
                    if (!tabletLayout) item { HomeHeading() }
                    when {
                        wideRailLayout -> item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(GeoSpace.xxl),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    Modifier.width(380.dp),
                                    verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
                                ) {
                                    HomeDeviceIdentity(
                                        deviceName = state.deviceName,
                                        connectionLabel = connectionStage.label,
                                        connectionTone = connectionStage.tone,
                                        onClick = onBack,
                                        modifier = Modifier.heightIn(min = 168.dp)
                                    )
                                    if (resolvedPlan != null) {
                                        StageCard(resolvedPlan) { onStageAction(resolvedPlan.destination) }
                                    } else {
                                        NextActionCard(fallbackAction, fallbackClick)
                                    }
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
                                ) {
                                    HomeFacts(
                                        storageLabel = storageAvailableLabel(state, statusFresh),
                                        collectionLabel = collectionFactLabel(tasksConfirmed, active)
                                    )
                                    summaryUpload?.let {
                                        HomeUploadSummary(it, onUploadQueueClick, onUploadJobClick)
                                    }
                                    if (showHealth) {
                                        SwipeDismissibleHealthOverview(
                                            state, health, onDismiss = dismissHealth, showCloseButton = true
                                        )
                                    }
                                    RecentSurveySection(
                                        runs = latestRuns,
                                        historyCurrent = recentHistoryCurrent,
                                        tableLayout = true,
                                        onClick = onRecentRunsClick
                                    )
                                }
                            }
                        }
                        tabletLayout -> {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(GeoSpace.xxl),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    HomeDeviceIdentity(
                                        deviceName = state.deviceName,
                                        connectionLabel = connectionStage.label,
                                        connectionTone = connectionStage.tone,
                                        onClick = onBack,
                                        modifier = Modifier.weight(1f).heightIn(min = 168.dp)
                                    )
                                    Box(Modifier.weight(1f)) {
                                        if (resolvedPlan != null) {
                                            StageCard(resolvedPlan) { onStageAction(resolvedPlan.destination) }
                                        } else {
                                            NextActionCard(fallbackAction, fallbackClick)
                                        }
                                    }
                                }
                            }
                            item {
                                HomeFacts(
                                    storageLabel = storageAvailableLabel(state, statusFresh),
                                    collectionLabel = collectionFactLabel(tasksConfirmed, active)
                                )
                            }
                            summaryUpload?.let { job ->
                                item { HomeUploadSummary(job, onUploadQueueClick, onUploadJobClick) }
                            }
                            if (showHealth) item {
                                SwipeDismissibleHealthOverview(
                                    state, health, onDismiss = dismissHealth, showCloseButton = true
                                )
                            }
                            item {
                                RecentSurveySection(
                                    runs = latestRuns,
                                    historyCurrent = recentHistoryCurrent,
                                    tableLayout = true,
                                    onClick = onRecentRunsClick
                                )
                            }
                        }
                        else -> {
                            item {
                                HomeDeviceIdentity(
                                    deviceName = state.deviceName,
                                    connectionLabel = connectionStage.label,
                                    connectionTone = connectionStage.tone,
                                    onClick = onBack
                                )
                            }
                            item {
                                if (resolvedPlan != null) {
                                    StageCard(resolvedPlan) { onStageAction(resolvedPlan.destination) }
                                } else {
                                    NextActionCard(fallbackAction, fallbackClick)
                                }
                            }
                            item {
                                HomeFacts(
                                    storageLabel = storageAvailableLabel(state, statusFresh),
                                    collectionLabel = collectionFactLabel(tasksConfirmed, active)
                                )
                            }
                            summaryUpload?.let { job ->
                                item { HomeUploadSummary(job, onUploadQueueClick, onUploadJobClick) }
                            }
                            if (showHealth) item {
                                SwipeDismissibleHealthOverview(
                                    state, health, onDismiss = dismissHealth, showCloseButton = true
                                )
                            }
                            item {
                                RecentSurveySection(
                                    runs = latestRuns,
                                    historyCurrent = recentHistoryCurrent,
                                    tableLayout = false,
                                    onClick = onRecentRunsClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun collectionFactLabel(tasksConfirmed: Boolean, active: List<ManagedPipeline>): String = when {
    !tasksConfirmed -> "상태 확인 필요"
    active.size > 1 -> "진행 중인 수집 ${active.size}개"
    active.size == 1 -> active.first().label
    else -> "진행 중인 수집 없음"
}

@Composable
private fun HomeBrandBar(unreadCount: Int, onAlertsClick: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(color = c.canvas, contentColor = c.ink) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = GeoSpace.gutter, end = GeoSpace.md, top = GeoSpace.sm, bottom = GeoSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.example.jetsoncontroller.ui.components.GeoLogo(
                modifier = Modifier.width(92.dp),
                backgroundColor = c.canvas
            )
            Spacer(Modifier.weight(1f))
            AlertIconButton(unreadCount, onAlertsClick)
        }
    }
}

@Composable
private fun HomeTabletBar(deviceName: String, unreadCount: Int, onAlertsClick: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(color = c.canvas, contentColor = c.ink) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = GeoSpace.xxl, vertical = GeoSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Text("홈", Modifier.weight(1f).testTag("home-title"), style = MaterialTheme.typography.headlineMedium)
            Text(
                deviceName,
                modifier = Modifier.widthIn(max = 174.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = c.muted
            )
            AlertIconButton(unreadCount, onAlertsClick)
        }
    }
}

@Composable
private fun HomeHeading() {
    val c = LocalGeoColors.current
    val date = SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date())
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
            Text("홈", Modifier.testTag("home-title"), style = MaterialTheme.typography.headlineMedium)
            Text(date, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("홈", Modifier.weight(1f).testTag("home-title"), style = MaterialTheme.typography.headlineMedium)
            Text(date, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

@Composable
private fun HomeDeviceIdentity(
    deviceName: String,
    connectionLabel: String,
    connectionTone: StatusTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalGeoColors.current
    val statusColor = com.example.jetsoncontroller.ui.components.statusVisuals(connectionTone).content
    Surface(
        onClick = onClick,
        color = c.surface,
        contentColor = c.ink,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(GeoSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.lg)
        ) {
            Image(
                painter = painterResource(R.drawable.geo_device),
                contentDescription = "GEO& 도로관리장치",
                modifier = Modifier.size(82.dp)
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)
            ) {
                Text(deviceName, style = MaterialTheme.typography.titleMedium)
                Text("● $connectionLabel", style = MaterialTheme.typography.labelMedium, color = statusColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("장치 변경", style = MaterialTheme.typography.bodySmall, color = c.primary)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.primary,
                        modifier = Modifier.size(GeoSize.iconSm))
                }
            }
        }
    }
}

@Composable
private fun HomeFacts(storageLabel: String, collectionLabel: String) {
    if (LocalDensity.current.fontScale <= 1.3f) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GeoSpace.xl)) {
            HomeFact("장치 저장 공간", storageLabel, Modifier.weight(1f))
            HomeFact("현재 수집", collectionLabel, Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
            HomeFact("장치 저장 공간", storageLabel)
            HomeFact("현재 수집", collectionLabel)
        }
    }
}

@Composable
private fun HomeFact(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    val icon = if (label == "장치 저장 공간") Icons.Default.Storage else Icons.Default.Explore
    Surface(
        color = c.surface,
        contentColor = c.ink,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(GeoSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(GeoSize.iconMd))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(value, style = GeoType.numeric)
            }
        }
    }
}

@Composable
private fun HomeUploadSummary(
    job: UploadJob,
    onQueueClick: () -> Unit,
    onJobClick: (UploadJob) -> Unit
) {
    val c = LocalGeoColors.current
    val active = job.state in setOf(
        com.example.jetsoncontroller.model.UploadJobState.QUEUED,
        com.example.jetsoncontroller.model.UploadJobState.SCANNING,
        com.example.jetsoncontroller.model.UploadJobState.UPLOADING
    )
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
        Surface(
            onClick = { onJobClick(job) },
            color = c.surface,
            contentColor = c.ink,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("home-upload-summary")
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(GeoSpace.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = c.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                    Text("업로드", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Text(
                        when (job.state) {
                            com.example.jetsoncontroller.model.UploadJobState.QUEUED -> "업로드 대기 중"
                            com.example.jetsoncontroller.model.UploadJobState.SCANNING -> "파일 확인 중"
                            com.example.jetsoncontroller.model.UploadJobState.UPLOADING -> "업로드 중"
                            com.example.jetsoncontroller.model.UploadJobState.COMPLETED -> "최근 업로드 완료"
                            com.example.jetsoncontroller.model.UploadJobState.FAILED -> "업로드 확인 필요"
                            com.example.jetsoncontroller.model.UploadJobState.CANCELLED -> "업로드 취소됨"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (job.state == com.example.jetsoncontroller.model.UploadJobState.FAILED) c.danger else c.ink
                    )
                    if (active && job.bytesTransferred != null) {
                        Text(
                            formatHomeBytes(job.bytesTransferred),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "업로드 상태 열기", tint = c.muted)
            }
        }
        TextButton(onClick = onQueueClick, modifier = Modifier.align(Alignment.End)) {
            Text("업로드 목록")
        }
    }
}

@Composable
private fun RecentSurveySection(
    runs: List<HomeRecentRun>,
    historyCurrent: Boolean,
    tableLayout: Boolean,
    onClick: () -> Unit
) {
    val c = LocalGeoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("최근 수집", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (!historyCurrent) Text("기록 미확인", style = MaterialTheme.typography.labelMedium, color = c.unknown)
        }
        if (runs.isEmpty()) {
            Text(
                if (historyCurrent) "아직 확인된 수집 기록이 없습니다." else "장치에 연결해 최근 수집을 확인하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted
            )
        } else {
            Surface(color = c.surface, shape = MaterialTheme.shapes.large) {
                Column {
                    if (tableLayout) {
                        RecentSurveyTableHeadings()
                        GeoRowDivider()
                    }
                    runs.forEachIndexed { index, run ->
                        if (tableLayout) RecentSurveyTableRow(run, onClick)
                        else RecentSurveyRow(run, onClick)
                        if (index < runs.lastIndex) GeoRowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSurveyTableHeadings() {
    val c = LocalGeoColors.current
    Row(
        Modifier.fillMaxWidth().padding(GeoSpace.md),
        horizontalArrangement = Arrangement.spacedBy(GeoSpace.lg)
    ) {
        Text("조사 구간", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text("저장·전송", Modifier.width(140.dp), style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text("용량", Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text("최근 확인", Modifier.width(104.dp), style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}

@Composable
private fun RecentSurveyTableRow(run: HomeRecentRun, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(onClick = onClick, color = c.surface, contentColor = c.ink) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = GeoSize.minTouchTarget).padding(GeoSpace.md),
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.lg),
            verticalAlignment = Alignment.Top
        ) {
            Text(run.title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(
                run.receiptLabel,
                Modifier.width(140.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (run.serverReceived) c.success else c.muted
            )
            Text(run.bytesLabel, Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
            Text(run.finishedAt.orEmpty(), Modifier.width(104.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

@Composable
private fun RecentSurveyRow(run: HomeRecentRun, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(onClick = onClick, color = c.surface, contentColor = c.ink) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = GeoSpace.md, vertical = GeoSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = c.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                Text(run.title, style = MaterialTheme.typography.labelLarge)
                Text(
                    listOfNotNull(
                        run.finishedAt,
                        if (run.stored) "장치 저장 완료" else "저장 결과 확인 필요"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "수집 이력 열기", tint = c.ink)
        }
    }
}

private data class HomeRecentRun(
    val id: String,
    val title: String,
    val finishedAt: String?,
    val stored: Boolean,
    val serverReceived: Boolean,
    val bytesLabel: String
) {
    val receiptLabel: String
        get() = when {
            serverReceived -> "서버 수신 확인"
            stored -> "장치 저장 완료"
            else -> "저장 결과 확인 필요"
        }
}

private fun homeRecentRuns(
    latestRun: PipelineRun?,
    history: List<TaskRun>,
    uploads: List<UploadJob>
): List<HomeRecentRun> {
    val latest = latestRun?.takeUnless { it.active }?.let { run ->
        HomeRecentRun(
            id = run.runId,
            title = run.contextSnapshot.surveySectionLabel.takeIf(String::isNotBlank) ?: run.pipelineId,
            finishedAt = homeObservedLabel(run.finishedAt),
            stored = com.example.jetsoncontroller.ui.field.runOutputStored(run),
            serverReceived = uploads.any { job ->
                job.context == run.uploadContext && job.verification?.matched == true
            },
            bytesLabel = run.output.manifest?.bytesTotal?.let(::formatHomeBytes) ?: "미확인"
        )
    }
    val historyItems = history.filterNot { it.active || it.state.equals("RUNNING", ignoreCase = true) }
        .map { run ->
            val runId = run.runId ?: run.id
            HomeRecentRun(
                id = runId,
                title = run.contextSnapshot?.surveySectionLabel?.takeIf(String::isNotBlank) ?: run.label,
                finishedAt = homeObservedLabel(run.finishedAt ?: run.startedAt),
                stored = run.output?.let { output ->
                    output.manifestState.equals("FINAL", ignoreCase = true) &&
                        output.manifest?.runId == runId
                } == true,
                serverReceived = uploads.any { job ->
                    job.verification?.matched == true && job.context?.let { context ->
                        context.runId == runId && context.pipelineId == run.pipelineId &&
                            (run.deviceId == null || context.deviceId.equals(run.deviceId, true)) &&
                            (run.output == null || context.outputId == run.output.outputId)
                    } == true
                },
                bytesLabel = run.output?.manifest?.bytesTotal?.let(::formatHomeBytes) ?: "미확인"
            )
        }
    return listOfNotNull(latest).plus(historyItems).distinctBy { it.id }.take(3)
}

/** The queue API is ordered newest first. An in-flight transfer always outranks history. */
internal fun homeUploadSummaryJob(jobs: List<UploadJob>): UploadJob? =
    jobs.firstOrNull {
        it.state == com.example.jetsoncontroller.model.UploadJobState.QUEUED ||
            it.state == com.example.jetsoncontroller.model.UploadJobState.SCANNING ||
            it.state == com.example.jetsoncontroller.model.UploadJobState.UPLOADING
    } ?: jobs.firstOrNull {
        it.state == com.example.jetsoncontroller.model.UploadJobState.COMPLETED ||
            it.state == com.example.jetsoncontroller.model.UploadJobState.FAILED
    }

private fun formatHomeBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(Locale.US, bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(Locale.US, bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(Locale.US, bytes / 1_024.0)
    else -> "$bytes B"
}

private fun homeObservedLabel(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return try {
        val observed = java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
        val today = java.time.LocalDate.now(observed.zone)
        val prefix = if (observed.toLocalDate() == today) "오늘" else
            "${observed.monthValue}월 ${observed.dayOfMonth}일"
        "$prefix ${"%02d:%02d".format(Locale.US, observed.hour, observed.minute)}"
    } catch (_: Exception) {
        value
    }
}

private fun storageAvailableLabel(state: DashboardUiState, current: Boolean): String {
    if (!current || !state.status.metricIsValid("storageAvailableBytes")) return "용량 확인 필요"
    val bytes = state.status.storageAvailableBytes
    return when {
        bytes >= 1_000_000_000L -> "%.0f GB 사용 가능".format(Locale.US, bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.0f MB 사용 가능".format(Locale.US, bytes / 1_000_000.0)
        else -> "$bytes B 사용 가능"
    }
}

// =====================================================================================
// Next action
// =====================================================================================

/**
 * 업무 단계 카드 — 홈에서 가장 눈에 띄는 단 하나의 요소.
 *
 * 홈이 여러 화면으로 나뉘지 않고 단계에 따라 이 카드 하나가 바뀝니다. 브랜드 색은 이
 * 화면에서 여기에만 쓰이므로, "지금 뭘 해야 하나" 가 색을 쓸 자격이 있는 유일한 정보가
 * 됩니다. 단계가 '미확인' 이나 '결과 대기' 일 때는 배지로 그 사실을 함께 말합니다 —
 * 모르는 상태에서 사용자를 '시작' 으로 밀지 않기 위해서입니다.
 */
@Composable
private fun StageCard(
    plan: com.example.jetsoncontroller.ui.field.FieldStagePlan,
    onClick: () -> Unit
) {
    val c = LocalGeoColors.current
    Surface(
        color = c.hero,
        contentColor = c.heroText,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(GeoSpace.xl),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(plan.eyebrow, style = GeoType.eyebrow, color = c.heroMuted)
                if (plan.tone == StatusTone.UNKNOWN || plan.tone == StatusTone.PENDING) {
                    StatusBadge(
                        if (plan.tone == StatusTone.UNKNOWN) "미확인" else "결과 대기",
                        plan.tone
                    )
                }
            }
            Text(plan.title, style = MaterialTheme.typography.headlineSmall)
            if (plan.detail.isNotBlank()) {
                Text(plan.detail, style = MaterialTheme.typography.bodyMedium, color = c.heroMuted)
            }
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = c.heroText,
                    contentColor = c.hero
                )
            ) {
                Text(plan.actionLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(GeoSpace.md))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}

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
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(GeoSpace.xl),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Text(action.eyebrow, style = GeoType.eyebrow, color = c.heroMuted)
            Text(action.title, style = MaterialTheme.typography.headlineSmall)
            if (action.detail.isNotBlank()) {
                Text(action.detail, style = MaterialTheme.typography.bodyMedium, color = c.heroMuted)
            }
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = c.heroText,
                    contentColor = c.hero
                )
            ) {
                Text(action.buttonLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(GeoSpace.md))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
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
