package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.ui.components.AdaptiveContent
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.GeoIdentifier
import com.example.jetsoncontroller.ui.components.GeoPrimaryAction
import com.example.jetsoncontroller.ui.components.GeoSection
import com.example.jetsoncontroller.ui.components.GeoSectionHeader
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.*
import kotlinx.coroutines.delay

private val SystemElapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveRunScreen(
    deviceName: String,
    connectionLabel: String,
    connectionTone: StatusTone,
    run: PipelineRun?,
    telemetryReceivedAtElapsedRealtime: Long? = null,
    elapsedLabel: String?,
    lastObservedLabel: String?,
    sensorSummary: String?,
    storageSummary: String?,
    stopInProgress: Boolean,
    online: Boolean,
    stopAvailable: Boolean = true,
    pendingStart: Boolean = false,
    unconfirmedRunId: String? = null,
    onRetryPendingStart: () -> Unit = {},
    developerModeEnabled: Boolean = false,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onCamera: () -> Unit,
    onMap: () -> Unit,
    onHome: () -> Unit = {},
    elapsedRealtimeMillis: () -> Long = SystemElapsedRealtimeMillis
) {
    val c = LocalGeoColors.current
    val awaitingStart = pendingStart || unconfirmedRunId != null
    val confirmedActive = run?.active == true && online && !awaitingStart
    val telemetry = verifiedRunTelemetry(run)
    var displayedDuration by remember(run?.runId) { mutableStateOf(telemetry?.durationMillis) }
    var nowElapsed by remember(run?.runId) {
        mutableLongStateOf(telemetryReceivedAtElapsedRealtime ?: 0L)
    }
    LaunchedEffect(
        run?.runId, telemetry?.observedAtEpochMillis, telemetry?.durationMillis,
        telemetryReceivedAtElapsedRealtime
    ) {
        displayedDuration = telemetry?.durationMillis
        nowElapsed = telemetryReceivedAtElapsedRealtime ?: 0L
    }
    LaunchedEffect(
        run?.runId, telemetry?.observedAtEpochMillis, telemetryReceivedAtElapsedRealtime,
        confirmedActive, online
    ) {
        while (telemetry != null && telemetryReceivedAtElapsedRealtime != null) {
            nowElapsed = elapsedRealtimeMillis()
            if (confirmedActive && online && nowElapsed - telemetryReceivedAtElapsedRealtime <= RunTelemetryFreshMillis) {
                displayedDuration = smoothedDurationMillis(
                    telemetry.durationMillis, telemetryReceivedAtElapsedRealtime, nowElapsed, mayAdvance = true
                )
            }
            delay(1_000L)
        }
    }
    val telemetryAge = telemetry?.let {
        telemetryReceivedAtElapsedRealtime?.let { receipt -> (nowElapsed - receipt).coerceAtLeast(0L) }
    }
    val elapsedValue = telemetry?.let { runDurationLabel(displayedDuration) } ?: elapsedLabel ?: "—"
    val bytesValue = telemetry?.let {
        when {
            it.collectedBytes != null -> runBytesLabel(it.collectedBytes)
            it.collectionBytesState.equals("TRUNCATED", true) ||
                it.collectionBytesState.equals("UNAVAILABLE", true) -> "용량 미확인"
            else -> "확인 중"
        }
    } ?: "확인 중"
    val observedValue = telemetry?.takeIf { telemetryAge != null }
        ?.let { telemetryObservationLabel(it, telemetryAge!!) }
        ?: lastObservedLabel ?: "미확인"
    var confirmStop by remember(run?.runId) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(
        confirmStop, online, stopInProgress, stopAvailable, run?.runId, run?.active, awaitingStart
    ) {
        if (confirmStop && (!online || stopInProgress || !stopAvailable || run?.active != true || awaitingStart)) {
            confirmStop = false
        }
    }

    if (confirmStop && run != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = c.surface,
            icon = { Icon(Icons.Default.Storage, contentDescription = null, tint = c.danger) },
            title = { Text("수집을 종료할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                    Text(
                        "${run.contextSnapshot.surveyProjectLabel} · ${run.contextSnapshot.surveySectionLabel}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("${deviceName}에서 진행 중인 수집을 종료합니다.", color = c.muted)
                    Text("종료 후 장치 저장 결과를 확인합니다.", color = c.muted)
                    if (developerModeEnabled) GeoIdentifier("Run", run.runId)
                }
            },
            confirmButton = {
                com.example.jetsoncontroller.ui.theme.Button(
                    onClick = {
                        if (online && !stopInProgress && stopAvailable && run.active && !awaitingStart) {
                            confirmStop = false
                            onStop()
                        }
                    },
                    enabled = online && !stopInProgress && stopAvailable && run.active && !awaitingStart,
                    colors = ButtonDefaults.buttonColors(containerColor = c.danger, contentColor = c.onDanger),
                    modifier = Modifier.testTag("confirm-stop")
                ) { Text("수집 종료") }
            },
            dismissButton = {
                com.example.jetsoncontroller.ui.theme.TextButton(onClick = { confirmStop = false }) {
                    Text("계속 수집")
                }
            }
        )
    }

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            pendingStart -> "시작 확인 중"
                            confirmedActive -> "수집 중"
                            else -> "수집 상태"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "수집 메뉴")
                    }
                    DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("장치 선택") },
                            onClick = { menuExpanded = false; onDevices() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (unreadCount > 0) "알림 ${unreadCount}개" else "알림") },
                            onClick = { menuExpanded = false; onAlerts() }
                        )
                        DropdownMenuItem(
                            text = { Text("수집 상태 새로고침") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = online && !stopInProgress,
                            onClick = { menuExpanded = false; onRefresh() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.canvas, titleContentColor = c.ink)
            )
        },
        bottomBar = {
            Surface(color = c.surface) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = GeoSpace.gutter, vertical = GeoSpace.md),
                    verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)
                ) {
                    when {
                                pendingStart && online -> GeoPrimaryAction(
                                    label = "시작 상태 확인",
                                    onClick = onRetryPendingStart,
                                    enabled = !stopInProgress,
                                    icon = Icons.Default.Refresh
                                )
                                unconfirmedRunId != null && online -> GeoPrimaryAction(
                                    label = "수집 상태 확인",
                                    onClick = onRefresh,
                                    enabled = !stopInProgress,
                                    icon = Icons.Default.Refresh
                                )
                                !online -> GeoPrimaryAction("다시 연결", onDevices, icon = Icons.Default.Refresh)
                                run == null || !stopAvailable -> GeoPrimaryAction(
                                    "수집 상태 다시 확인", onRefresh, enabled = online && !stopInProgress,
                                    icon = Icons.Default.Refresh
                                )
                                else -> {
                                    com.example.jetsoncontroller.ui.theme.TextButton(
                                        onClick = onHome,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
                                    ) { Text("홈으로") }
                                    DangerPrimaryAction(
                                    label = if (stopInProgress) "수집 종료 확인 중" else "수집 종료",
                                    onClick = { if (confirmedActive && stopAvailable) confirmStop = true },
                                    enabled = confirmedActive && stopAvailable && !stopInProgress
                                    )
                                }
                            }
                }
            }
        }
    ) { padding ->
        AdaptiveContent(Modifier.fillMaxSize().padding(padding), maxWidth = 1200.dp) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = GeoSpace.sm, bottom = GeoSpace.xl),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)
            ) {
                when {
                    awaitingStart -> item {
                        PendingStartContent(
                            run, deviceName, online, pendingStart, developerModeEnabled, unconfirmedRunId
                        )
                    }
                    run == null -> item {
                        UnknownRunContent(online = online, onRefresh = onRefresh, onDevices = onDevices)
                    }
                    else -> item {
                        ActiveWorkspace(
                            run, deviceName, connectionLabel, elapsedValue, bytesValue, observedValue,
                            sensorSummary, storageSummary, online, stopInProgress,
                            stopAvailable, developerModeEnabled, onCamera, onMap,
                            onRequestStop = { if (confirmedActive && stopAvailable) confirmStop = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DangerPrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean) {
    val c = LocalGeoColors.current
    com.example.jetsoncontroller.ui.theme.Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = c.danger, contentColor = c.onDanger),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.primaryAction)
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun PendingStartContent(
    run: PipelineRun?,
    deviceName: String,
    online: Boolean,
    pendingStart: Boolean,
    developerModeEnabled: Boolean,
    unconfirmedRunId: String?
) {
    val c = LocalGeoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)) {
        Text(
            if (pendingStart) "시작 요청을 보냈습니다" else "수집 상태를 확인하고 있습니다",
            style = MaterialTheme.typography.headlineMedium,
            color = c.ink
        )
        Surface(color = c.pendingBg, contentColor = c.ink, shape = RoundedCornerShape(20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(GeoSpace.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
            ) {
                Icon(Icons.Default.Refresh, null, tint = c.pending, modifier = Modifier.size(GeoSize.iconMd))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                    Text(
                        if (online) "장치 응답을 기다리는 중" else "장치 연결을 기다리는 중",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        listOfNotNull(run?.contextSnapshot?.surveySectionLabel, deviceName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted
                    )
                }
            }
        }
        Text(
            "같은 수집의 시작 결과를 확인합니다. 확인이 끝날 때까지 새 수집을 시작할 수 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted
        )
        if (developerModeEnabled && unconfirmedRunId != null) {
            GeoSection { GeoIdentifier("확인 중인 Run", unconfirmedRunId) }
        }
    }
}

@Composable
private fun UnknownRunContent(online: Boolean, onRefresh: () -> Unit, onDevices: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
        Text("현재 수집 상태를 확인해 주세요", style = MaterialTheme.typography.headlineMedium)
        AppBanner(
            message = if (online) "장치에서 진행 중인 수집을 찾지 못했습니다."
                else "장치 연결이 끊겼습니다. 수집은 계속되고 있을 수 있습니다.",
            tone = StatusTone.UNKNOWN,
            actionLabel = if (online) "다시 확인" else "다시 연결",
            onAction = if (online) onRefresh else onDevices
        )
    }
}

@Composable
private fun ActiveWorkspace(
    run: PipelineRun,
    deviceName: String,
    connectionLabel: String,
    elapsedLabel: String,
    bytesLabel: String,
    lastObservedLabel: String,
    sensorSummary: String?,
    storageSummary: String?,
    online: Boolean,
    stopInProgress: Boolean,
    stopAvailable: Boolean,
    developerModeEnabled: Boolean,
    onCamera: () -> Unit,
    onMap: () -> Unit,
    onRequestStop: () -> Unit
) {
    val c = LocalGeoColors.current
    val confirmedActive = online && run.active && stopAvailable
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)) {
        Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
            Text(run.contextSnapshot.surveyProjectLabel, style = MaterialTheme.typography.bodySmall, color = c.muted)
            Text(run.contextSnapshot.surveySectionLabel, style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Text(
                if (online) deviceName else "$deviceName · 연결 끊김",
                style = MaterialTheme.typography.bodySmall,
                color = if (confirmedActive) c.success else c.unknown
            )
        }
        if (!online) {
            AppBanner(
                "장치 연결이 끊겼습니다. 마지막 상태를 표시하며 수집은 계속되고 있을 수 있습니다.",
                StatusTone.UNKNOWN
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stack = maxWidth < 600.dp || LocalDensity.current.fontScale > 1.3f
            val wideWorkspace = maxWidth >= 1000.dp
            val statusPane: @Composable ColumnScope.() -> Unit = {
                LiveStatusCard(confirmedActive, stopInProgress, elapsedLabel, bytesLabel, lastObservedLabel)
                Spacer(Modifier.height(GeoSpace.lg))
                ObservationRow(Icons.Default.Storage, "저장 폴더", run.output.path.substringAfterLast('/').ifBlank { "확인 중" })
                Spacer(Modifier.height(GeoSpace.md))
                ObservationRow(Icons.Default.Map, "위치 품질", runQualityPresentation(run.quality).headline)
            }
            val controlsPane: @Composable ColumnScope.() -> Unit = {
                CameraAction(onCamera, enabled = confirmedActive)
            }
            if (stack) {
                Column {
                    statusPane()
                    Spacer(Modifier.height(GeoSpace.xl))
                    controlsPane()
                    Spacer(Modifier.height(GeoSpace.lg))
                    MapObservationAction(sensorSummary, onMap, confirmedActive, minHeight = 146.dp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.section)) {
                    Column(Modifier.weight(if (wideWorkspace) 1.7f else 1f)) {
                        MapObservationAction(sensorSummary, onMap, confirmedActive, minHeight = 420.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        statusPane()
                        Spacer(Modifier.height(GeoSpace.lg))
                        controlsPane()
                    }
                }
            }
        }
        if (developerModeEnabled) {
            GeoSection {
                GeoSectionHeader("실행 세부 정보", eyebrow = "개발자 모드")
                GeoIdentifier("Run", run.runId)
                GeoIdentifier("결과 경로", "${run.output.rootId}/${run.output.path}")
                GeoIdentifier("점검", run.preflightSnapshot.preflightId)
            }
        }
    }
}

@Composable
private fun LiveStatusCard(
    confirmedActive: Boolean,
    stopInProgress: Boolean,
    elapsedLabel: String,
    bytesLabel: String,
    lastObservedLabel: String
) {
    val c = LocalGeoColors.current
    Surface(color = c.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(GeoSpace.xl), verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                Icon(Icons.Default.Wifi, null, tint = if (confirmedActive) c.success else c.unknown)
                Text(
                    when {
                        stopInProgress -> "수집 종료를 확인하고 있습니다"
                        confirmedActive -> "수집이 진행 중입니다"
                        else -> "수집 상태가 확인되지 않았습니다"
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            HorizontalDivider(color = c.border)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stack = maxWidth < 420.dp || LocalDensity.current.fontScale > 1.3f
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                        Metric("경과 시간", elapsedLabel, Modifier.fillMaxWidth(), large = true)
                        Metric("수집 용량", bytesLabel, Modifier.fillMaxWidth())
                        Metric("최근 확인", lastObservedLabel, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                        Metric("경과 시간", elapsedLabel, Modifier.weight(1f), large = true)
                        Metric("수집 용량", bytesLabel, Modifier.weight(1f))
                        Metric("최근 확인", lastObservedLabel, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, large: Boolean = false) {
    val c = LocalGeoColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value, style = if (large) GeoType.numericLarge else GeoType.numeric, color = c.ink)
    }
}

@Composable
private fun ObservationRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val c = LocalGeoColors.current
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
        val stack = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(GeoSize.iconMd))
            if (stack) {
                Column(
                    Modifier.padding(start = GeoSpace.md).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(value, style = MaterialTheme.typography.labelMedium, color = c.muted)
                }
            } else {
                Text(label, Modifier.padding(start = GeoSpace.md), style = MaterialTheme.typography.bodyMedium)
                Text(
                    value,
                    Modifier.padding(start = GeoSpace.md).weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.muted,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun CameraAction(onClick: () -> Unit, enabled: Boolean) {
    val c = LocalGeoColors.current
    Surface(
        onClick = onClick, enabled = enabled, color = c.surface, contentColor = c.ink,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
    ) {
        Row(Modifier.padding(GeoSpace.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CameraAlt, null, tint = c.primary)
            Text("카메라 영상 확인", Modifier.padding(start = GeoSpace.md).weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Default.ChevronRight, null, tint = c.muted)
        }
    }
}

@Composable
private fun MapObservationAction(
    sensorSummary: String?,
    onClick: () -> Unit,
    enabled: Boolean,
    minHeight: androidx.compose.ui.unit.Dp
) {
    val c = LocalGeoColors.current
    val stroke = with(LocalDensity.current) { 1.dp.toPx() }
    val radius = with(LocalDensity.current) { 16.dp.toPx() }
    Box(
        Modifier.fillMaxWidth().heightIn(min = minHeight)
            .drawBehind {
                drawRoundRect(
                    color = c.border,
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                )
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
            Icon(Icons.Default.Map, null, tint = c.muted, modifier = Modifier.size(32.dp))
            Text(
                if (sensorSummary == null) "위치 정보 대기" else "위치 정보 확인",
                style = MaterialTheme.typography.labelLarge,
                color = c.ink
            )
            Text(
                sensorSummary?.let { "$it · 지도 열기 ›" } ?: "지도 열기 ›",
                style = MaterialTheme.typography.bodySmall,
                color = c.primary
            )
        }
    }
}
