package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoRunDashboard(state: FieldState, pipelines: List<ManagedPipeline>,
    onBack: () -> Unit, onNew: () -> Unit, onRefresh: () -> Unit, onMore: () -> Unit,
    onLog: (TaskRun) -> Unit, onRoute: (TaskRun) -> Unit, onDismissLog: () -> Unit,
    onPipeline: (ManagedPipeline) -> Unit,
    onDeleteRun: (TaskRun) -> Unit = {},
    onDismissMessage: (String) -> Unit = {},
    onUploadOutput: (TaskRun) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onTrash: () -> Unit = {},
    developerModeEnabled: Boolean = false
) {
    var tab by rememberSaveable(state.deviceId) { mutableStateOf("전체") }
    val c = LocalGeoColors.current
    val runs = state.runs.filter { when (tab) {
        "진행 중" -> it.isActiveRun()
        "완료" -> it.state in listOf("COMPLETED", "STOPPED", "FAILED", "UNKNOWN")
        else -> true
    } }
    var revealedRunId by rememberSaveable(state.deviceId) { mutableStateOf<String?>(null) }
    LaunchedEffect(revealedRunId, state.deviceId, state.online, state.deletingRunId, state.runs) {
        val revealed = state.runs.firstOrNull { it.id == revealedRunId }
        if (revealed == null || !state.online || state.deletingRunId != null || revealed.isActiveRun()) {
            revealedRunId = null
        }
    }
    state.log?.takeIf { developerModeEnabled }?.let { text -> AlertDialog(onDismissRequest = onDismissLog,
        title = { Text("저장된 로그 · 최근 64KB") },
        text = { SelectionContainer { Text(text, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = onDismissLog) { Text("닫기") } }) }
    Scaffold(snackbarHost = { OperationMessageHost(
        state.message, onDismissMessage,
        actionLabel = "실행 취소".takeIf { state.undoTrashId != null },
        onAction = onUndoDelete.takeIf { state.undoTrashId != null }
    ) },
        topBar = {
            TopAppBar(
                title = { Text("수집 이력") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onTrash) {
                        Icon(Icons.Default.DeleteOutline, "휴지통")
                    }
                    if (developerModeEnabled) {
                        IconButton(onClick = onRefresh, enabled = state.online && !state.loading) {
                            Icon(Icons.Default.Refresh, "기록 새로고침")
                        }
                    }
                }
            )
        }, bottomBar = {
            Surface(color = c.surface, tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                    Button(
                        onClick = onNew,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("새 수집 준비") }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("전체", "진행 중", "완료").forEach { label -> FilterChip(tab == label, { tab = label }, label = { Text(label) }) }
            } }
            if (state.loading || state.deletingRunId != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error ->
                item {
                    AppBanner(
                        error,
                        StatusTone.ERROR,
                        actionLabel = "다시 시도",
                        onAction = onRefresh
                    )
                }
            }
            if (developerModeEnabled) state.technicalError?.let { detail ->
                item { SelectionContainer { Text(detail, style = MaterialTheme.typography.bodySmall) } }
            }
            items(runs, key = { it.id }) { run ->
                val runPresentation = historyRunPresentation(run, state.online, state.historyCurrent)
                val canDelete = state.online && state.deletingRunId == null && !run.isActiveRun()
                TwoStageHistorySwipe(
                    runId = run.id,
                    enabled = canDelete,
                    revealed = revealedRunId == run.id,
                    onRevealChange = { reveal -> revealedRunId = run.id.takeIf { reveal } },
                    onTrash = {
                        if (canDelete) {
                            revealedRunId = null
                            onDeleteRun(run)
                        }
                    }
                ) {
                    Surface(color = c.sectionSoft, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (run.isActiveRun() && state.online && state.historyCurrent) Icons.Default.PlayCircle else Icons.Default.Assignment, null, tint = c.primary)
                                Text(run.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                StatusBadge(runPresentation.first, runPresentation.second)
                            }
                            Text(com.example.jetsoncontroller.ui.storage.localDateTimeLabel(run.startedAt), style = MaterialTheme.typography.bodySmall, color = c.muted)
                            RunQualityEvidence(run.quality, compact = true, title = "수집 요약")
                            run.contextSnapshot?.let { context ->
                                Text("${context.surveyProjectLabel} · ${context.surveySectionLabel}",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            run.output?.let { output ->
                                val manifest = output.manifest
                                Text(
                                    when {
                                        manifest != null -> "저장 파일 ${manifest.fileCount}개 · ${outputBytesLabel(manifest.bytesTotal)}"
                                        output.manifestState.equals("PENDING", true) ||
                                            output.manifestState.equals("RUNNING", true) -> "저장 결과 확인 중"
                                        output.manifestState.equals("FAILED", true) ||
                                            output.manifestState.equals("ERROR", true) -> "저장 결과 확인 필요"
                                        else -> "저장 결과 미확인"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.muted
                                )
                                if (developerModeEnabled) {
                                    Text(
                                        "manifest ${output.manifestState}" +
                                            (manifest?.let { " · expectation ${it.expectationState}" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.muted
                                    )
                                }
                            }
                            OutlinedButton(onClick = { onRoute(run) }, modifier = Modifier.fillMaxWidth()) {
                                Text("수집 경로")
                            }
                            if (developerModeEnabled) {
                                OutlinedButton(onClick = { onLog(run) }, enabled = state.online,
                                    modifier = Modifier.fillMaxWidth()) { Text("실행 로그") }
                            }
                            if (canonicalHistoryRunId(run) != null) {
                                Button(onClick = { onUploadOutput(run) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.CloudUpload, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("이 실행 결과 전송")
                                }
                            }
                            if (developerModeEnabled && run.isActiveRun()) pipelines.firstOrNull { it.id == run.pipelineId }?.let { pipeline ->
                                TextButton(onClick = { onPipeline(pipeline) }) { Text("진행 상태 · 작업 제어") }
                            }
                        }
                    }
                }
            }
            if (runs.isEmpty() && !state.loading) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                    Text("수집 이력이 없습니다", style = MaterialTheme.typography.titleMedium, color = c.muted)
                }
            }
            if (state.nextOffset != null) item { OutlinedButton(onClick = onMore, enabled = state.online && !state.loading && state.deletingRunId == null, modifier = Modifier.fillMaxWidth()) { Text("이전 기록 더 불러오기") } }
        }
        }
    }
}

@Composable
private fun TwoStageHistorySwipe(
    runId: String,
    enabled: Boolean,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onTrash: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val revealPx = with(density) { 84.dp.toPx() }
    var offsetPx by remember(runId, revealed) { mutableFloatStateOf(if (revealed) revealPx else 0f) }
    var sent by remember(runId, revealed) { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            offsetPx = 0f
            onRevealChange(false)
        }
    }
    Box(
        Modifier.fillMaxWidth().testTag("task-run-$runId").semantics {
            if (enabled) customActions = listOf(
                CustomAccessibilityAction("작업 이력을 휴지통으로 이동") {
                    if (!sent) {
                        sent = true
                        onTrash()
                    }
                    true
                }
            )
        }
    ) {
        Surface(
            onClick = {
                if (enabled && !sent) {
                    sent = true
                    onTrash()
                }
            },
            color = LocalGeoColors.current.danger,
            contentColor = LocalGeoColors.current.onDanger,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.matchParentSize().testTag("task-run-trash-$runId")
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(Icons.Default.DeleteOutline, null)
                Spacer(Modifier.width(8.dp))
                Text("휴지통", style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                // The tag follows the layout modifier so instrumentation observes the
                // translated card bounds after the first-stage reveal.
                .testTag("task-run-content-$runId")
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(0f, revealPx * 2f)
                    },
                    onDragStopped = {
                        if (!enabled) {
                            offsetPx = 0f
                            onRevealChange(false)
                        } else if (revealed && offsetPx >= revealPx * 1.65f) {
                            if (!sent) {
                                sent = true
                                onTrash()
                            }
                        } else if (offsetPx >= revealPx * 0.55f) {
                            offsetPx = revealPx
                            onRevealChange(true)
                        } else {
                            offsetPx = 0f
                            onRevealChange(false)
                        }
                    }
                )
        ) { content() }
    }
}

private fun outputBytesLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

internal fun historyRunPresentation(run: TaskRun, online: Boolean, historyCurrent: Boolean): Pair<String, StatusTone> = when {
    run.isActiveRun() && (!online || !historyCurrent) -> "최근 실행 보고 · 현재 미확인" to StatusTone.WARNING
    run.isActiveRun() -> "실행 보고" to StatusTone.INFO
    run.state == "COMPLETED" -> "완료" to StatusTone.SUCCESS
    run.state == "STOPPED" -> "중지" to StatusTone.INFO
    run.state == "FAILED" -> "실패" to StatusTone.ERROR
    else -> "결과 미확인" to StatusTone.WARNING
}

internal fun canonicalHistoryRunId(run: TaskRun): String? {
    if (run.contextSnapshot == null || !run.output?.manifestState.equals("FINAL", true)) return null
    val manifestId = run.output?.manifest?.runId?.takeIf(String::isNotBlank) ?: return null
    return run.runId?.takeIf { it == manifestId } ?: manifestId.takeIf { run.runId == null }
}
