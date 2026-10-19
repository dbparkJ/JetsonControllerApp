package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.PipelineOutputManifest
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.theme.*
import com.example.jetsoncontroller.ui.theme.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunResultScreen(
    deviceName: String,
    connectionLabel: String,
    connectionTone: StatusTone,
    run: PipelineRun?,
    uploadEnabled: Boolean,
    uploadDisabledReason: String,
    serverReceiptLabel: String?,
    serverReceiptTone: StatusTone = if (serverReceiptLabel == null) StatusTone.UNKNOWN else StatusTone.SUCCESS,
    ticketTitle: String? = null,
    ticketCompletionEnabled: Boolean = false,
    ticketCompletionMessage: String? = null,
    ticketCompletionPending: Boolean = false,
    onReportTicketCompletion: () -> Unit = {},
    online: Boolean,
    developerModeEnabled: Boolean = false,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrepareUpload: () -> Unit,
    onOpenFiles: () -> Unit,
    onNewSurvey: () -> Unit,
    historyEntry: TaskRun? = null,
    resultMessage: String? = null,
    onOpenLog: (() -> Unit)? = null,
    savedLog: String? = null,
    onDismissLog: () -> Unit = {}
) {
    val c = LocalGeoColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    val manifest = run?.output?.manifest
    val stored = runOutputStored(run)
    val finishedTone = when {
        run == null -> StatusTone.UNKNOWN
        run.active || run.finishedAt == null -> StatusTone.PENDING
        run.exitCode != null && run.exitCode != 0 -> StatusTone.WARNING
        else -> StatusTone.SUCCESS
    }
    val storedTone = when {
        stored -> StatusTone.SUCCESS
        run?.output?.manifestState.equals("PENDING", true) || run?.output?.manifestState.equals("RUNNING", true) -> StatusTone.PENDING
        run?.output?.manifestState.equals("FAILED", true) || run?.output?.manifestState.equals("ERROR", true) -> StatusTone.WARNING
        else -> StatusTone.UNKNOWN
    }
    val canUpload = run != null && stored && uploadEnabled
    androidx.activity.compose.BackHandler(onBack = onBack)
    savedLog?.let { text ->
        AlertDialog(
            onDismissRequest = onDismissLog,
            title = { Text("저장된 로그 · 최근 64KB") },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(text, Modifier.heightIn(max = 440.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = onDismissLog) { Text("닫기") } }
        )
    }

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = { Text("수집 결과") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "수집 결과 메뉴")
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
                            text = { Text("결과 새로고침") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = online,
                            onClick = { menuExpanded = false; onRefresh() }
                        )
                        if (onOpenLog != null) DropdownMenuItem(
                            text = { Text("실행 로그") },
                            enabled = online,
                            onClick = { menuExpanded = false; onOpenLog() }
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)
                ) {
                    GeoPrimaryAction(
                        label = "파일 전송 준비",
                        onClick = onPrepareUpload,
                        enabled = canUpload,
                        icon = Icons.Default.CloudUpload
                    )
                    com.example.jetsoncontroller.ui.theme.TextButton(
                        onClick = onNewSurvey,
                        modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
                    ) { Text("다음 구간 수집 준비") }
                }
            }
        }
    ) { padding ->
        AdaptiveContent(Modifier.fillMaxSize().padding(padding), maxWidth = 1000.dp) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = GeoSpace.sm, bottom = GeoSpace.xl),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
            ) {
                resultMessage?.let { message ->
                    item { AppBanner(message, StatusTone.WARNING) }
                }
                if (run == null && historyEntry != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                            Text(historyEntry.label, style = MaterialTheme.typography.titleLarge)
                            Text(com.example.jetsoncontroller.ui.storage.localDateTimeLabel(historyEntry.startedAt))
                            val presentation = historyRunPresentation(historyEntry, online, false)
                            StatusBadge(presentation.first, presentation.second)
                            RunQualityEvidence(historyEntry.quality)
                            Text("저장된 이력입니다. 실행 식별 정보가 없으면 전송 전에 수집 결과를 확인해야 합니다.")
                        }
                    }
                    return@LazyColumn
                }
                if (run == null) {
                    item {
                        GeoStateBlock(
                            title = "확인할 수집 결과가 없습니다",
                            impact = "최근 수집 결과를 다시 확인하거나 수집 이력에서 찾아보세요.",
                            tone = StatusTone.UNKNOWN,
                            primaryLabel = "다시 확인",
                            onPrimary = onRefresh,
                            secondaryLabel = "뒤로",
                            onSecondary = onBack
                        )
                    }
                    return@LazyColumn
                }

                item {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val stack = maxWidth < 600.dp || LocalDensity.current.fontScale > 1.3f
                        val summary: @Composable ColumnScope.() -> Unit = {
                            ResultHero(run, finishedTone, deviceName)
                            Spacer(Modifier.height(GeoSpace.lg))
                            RunReceiptStrip(pipelineRunReceiptCells(run, serverReceiptTone))
                            Spacer(Modifier.height(GeoSpace.sm))
                            Text(
                                receiptNote(finishedTone, storedTone, serverReceiptLabel, serverReceiptTone),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.muted
                            )
                            Spacer(Modifier.height(GeoSpace.xl))
                            ResultMetrics(run)
                            Spacer(Modifier.height(GeoSpace.lg))
                            RunQualityEvidence(run.quality)
                        }
                        val actions: @Composable ColumnScope.() -> Unit = {
                            CollectedFilesAction(run, stored, onOpenFiles)
                            if (!ticketTitle.isNullOrBlank()) {
                                Spacer(Modifier.height(GeoSpace.lg))
                                TicketCompletionAction(
                                    title = ticketTitle,
                                    enabled = ticketCompletionEnabled,
                                    message = ticketCompletionMessage,
                                    pending = ticketCompletionPending,
                                    onReport = onReportTicketCompletion
                                )
                            }
                            if (!online) {
                                Spacer(Modifier.height(GeoSpace.lg))
                                AppBanner(
                                    "장치 연결이 끊겼습니다. 표시된 결과는 마지막으로 확인한 값입니다.",
                                    StatusTone.UNKNOWN,
                                    actionLabel = "다시 연결",
                                    onAction = onDevices
                                )
                            }
                            if (!canUpload) {
                                Spacer(Modifier.height(GeoSpace.lg))
                                Text(
                                    when {
                                        !stored -> "장치 저장 결과를 확인한 뒤 파일을 전송할 수 있습니다."
                                        !uploadEnabled -> uploadDisabledReason
                                        else -> "파일 전송 조건을 확인해 주세요."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.muted
                                )
                            }
                            if (developerModeEnabled) {
                                Spacer(Modifier.height(GeoSpace.lg))
                                TechnicalResultDetails(run, manifest)
                            }
                        }
                        if (stack) {
                            Column {
                                summary()
                                Spacer(Modifier.height(GeoSpace.xl))
                                actions()
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.section)) {
                                Column(Modifier.weight(1.35f), content = summary)
                                Column(Modifier.weight(1f), content = actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCompletionAction(
    title: String,
    enabled: Boolean,
    message: String?,
    pending: Boolean,
    onReport: () -> Unit
) {
    val c = LocalGeoColors.current
    var confirm by remember(title) { mutableStateOf(false) }
    if (confirm) {
        val particle = if (hasKoreanFinalConsonant(title.lastOrNull())) "을" else "를"
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("구간 완료 보고") },
            text = { Text("$title$particle 완료로 보고합니다.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onReport() }) { Text("보고") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } }
        )
    }
    if (!pending && (enabled || message?.contains("작업자 권한") == true)) {
        OutlinedButton(
            onClick = { confirm = true },
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
        ) { Text("구간 완료 보고") }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    } else if (!message.isNullOrBlank()) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
    if (pending) {
        Text("완료 보고 전송 대기 중", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

private fun hasKoreanFinalConsonant(character: Char?): Boolean {
    if (character == null || character !in '\uAC00'..'\uD7A3') return false
    return (character.code - '\uAC00'.code) % 28 != 0
}

@Composable
private fun ResultMetrics(run: PipelineRun) {
    val telemetry = verifiedRunTelemetry(run)
    val manifest = run.output.manifest?.takeIf { runOutputStored(run) }
    val duration = runDurationLabel(telemetry?.durationMillis)
    val bytes = runBytesLabel(telemetry?.collectedBytes ?: manifest?.bytesTotal)
    val precision = precisionMetricLabel(run.quality)
    val c = LocalGeoColors.current
    Surface(color = c.surface, shape = RoundedCornerShape(20.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(GeoSpace.xl)) {
            val stack = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
            if (stack) {
                Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                    ResultMetric("수집 시간", duration, Modifier.fillMaxWidth())
                    ResultMetric("수집 용량", bytes, Modifier.fillMaxWidth())
                    ResultMetric("정밀 위치", precision, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
                    ResultMetric("수집 시간", duration, Modifier.weight(1f))
                    ResultMetric("수집 용량", bytes, Modifier.weight(1f))
                    ResultMetric("정밀 위치", precision, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value, style = GeoType.numeric, color = c.ink)
    }
}

@Composable
private fun ResultHero(run: PipelineRun, tone: StatusTone, deviceName: String) {
    val c = LocalGeoColors.current
    val visuals = statusVisuals(tone)
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Icon(visuals.icon, contentDescription = null, tint = visuals.content, modifier = Modifier.size(44.dp))
        Text(
            when (tone) {
                StatusTone.SUCCESS -> "수집을 마쳤습니다"
                StatusTone.PENDING -> "수집 종료를 확인하고 있습니다"
                StatusTone.WARNING, StatusTone.ERROR -> "수집 종료를 확인했습니다"
                else -> "수집 결과를 확인해 주세요"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = c.ink
        )
        Text(run.contextSnapshot.surveySectionLabel, style = MaterialTheme.typography.titleLarge, color = c.ink)
        Text(
            listOfNotNull(run.finishedAt?.takeIf { it.isNotBlank() }, deviceName).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )
    }
}

/**
 * The run receipt as three cells in one row: 수집 종료 → 장치 저장 → 서버 수신. Each cell carries
 * its own status tone and short state word, so the operator reads the whole chain at a glance.
 */
@Composable
internal fun RunReceiptStrip(cells: List<RunReceiptCell>, modifier: Modifier = Modifier) {
    val c = LocalGeoColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        cells.forEach { cell ->
            val visuals = statusVisuals(cell.tone)
            Column(
                Modifier
                    .weight(1f)
                    .background(visuals.container, RoundedCornerShape(GeoRadius.sm))
                    .padding(horizontal = GeoSpace.sm, vertical = GeoSpace.md),
                verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                    Icon(visuals.icon, contentDescription = null, tint = visuals.content, modifier = Modifier.size(16.dp))
                    Text(
                        cell.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.inkSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    cell.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = visuals.content,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One sentence under the receipt. It names what is still unresolved, and never reads silence as failure. */
private fun receiptNote(
    finishedTone: StatusTone,
    storedTone: StatusTone,
    serverReceiptLabel: String?,
    serverReceiptTone: StatusTone
): String = when {
    finishedTone == StatusTone.WARNING || finishedTone == StatusTone.ERROR ->
        "정상적으로 끝나지 않았습니다. 장치 저장 결과를 확인해 주세요."
    finishedTone == StatusTone.PENDING -> "장치의 종료 응답을 기다리고 있습니다."
    storedTone == StatusTone.PENDING -> "장치에서 파일 목록을 확인하고 있습니다."
    storedTone == StatusTone.WARNING || storedTone == StatusTone.ERROR ->
        "장치 저장 결과를 다시 확인해 주세요."
    serverReceiptTone == StatusTone.SUCCESS -> serverReceiptLabel ?: "서버에서 이 수집의 수신을 확인했습니다."
    serverReceiptTone == StatusTone.PENDING -> "서버의 수신 결과를 확인하고 있습니다."
    serverReceiptTone == StatusTone.WARNING || serverReceiptTone == StatusTone.ERROR ->
        "서버의 수신 결과를 다시 확인해 주세요."
    else -> "서버의 수신 결과는 아직 확인되지 않았습니다. 수신 상태를 다시 확인해 주세요."
}

@Composable
private fun CollectedFilesAction(run: PipelineRun, stored: Boolean, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(onClick = onClick, enabled = stored, color = c.surface, contentColor = c.ink, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(GeoSpace.lg), verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
            Text("저장 폴더", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Text(
                if (stored) run.output.path.substringAfterLast('/').ifBlank { "폴더 확인 중" } else "저장 확인 중",
                style = MaterialTheme.typography.titleMedium
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = c.primary)
                Text(
                    if (stored) "수집한 파일 보기" else "파일 목록 확인 대기",
                    Modifier.padding(start = GeoSpace.md).weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (stored) c.primary else c.muted
                )
                Icon(Icons.Default.ChevronRight, null, tint = if (stored) c.primary else c.muted)
            }
        }
    }
}

@Composable
private fun TechnicalResultDetails(run: PipelineRun, manifest: PipelineOutputManifest?) {
    GeoSection {
        GeoSectionHeader("결과 세부 정보", eyebrow = "개발자 모드")
        GeoIdentifier("경로", "${run.output.rootId}/${run.output.path}")
        GeoIdentifier("Run", run.runId)
        GeoIdentifier("Output", run.output.outputId)
        run.exitCode?.let { GeoIdentifier("종료 코드", it.toString()) }
        manifest?.let { ExpectationSection(it) }
    }
}

/** A FINAL marker is accepted only when the manifest and upload identity belong to this run. */
internal fun runOutputStored(run: PipelineRun?): Boolean {
    if (run == null || run.active || run.finishedAt == null) return false
    val output = run.output
    val manifest = output.manifest ?: return false
    val context = run.uploadContext
    return output.manifestState.equals("FINAL", ignoreCase = true) &&
        manifest.runId == run.runId && output.rootId.isNotBlank() && output.path.isNotBlank() &&
        context.runId == run.runId && context.deviceId.equals(run.deviceId, ignoreCase = true) &&
        context.pipelineId == run.pipelineId && context.sourceRevision == run.sourceRevision &&
        context.configSha256 == run.configRevision && context.outputId == output.outputId
}

@Composable
private fun ExpectationSection(manifest: PipelineOutputManifest) {
    val c = LocalGeoColors.current
    val expected = manifest.expected
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Text("기대 조건 검사 · ${manifest.expectationState}", style = MaterialTheme.typography.titleSmall)
        if (expected.minFiles > 0) Text("최소 파일 수 ${expected.minFiles}개 · 실제 ${manifest.fileCount}개", color = c.muted)
        if (expected.minBytes > 0) Text("최소 용량 ${formatBytes(expected.minBytes)} · 실제 ${formatBytes(manifest.bytesTotal)}", color = c.muted)
        manifest.matchedPatterns.forEach { Text("${it.pattern} · ${it.fileCount}개", color = c.muted) }
    }
}

private fun formatBytes(bytes: Long): String =
    com.example.jetsoncontroller.ui.model.Formats.size(bytes)
