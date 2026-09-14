package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoRunDashboard(state: FieldState, pipelines: List<ManagedPipeline>, deviceName: String,
    unreadCount: Int, onBack: () -> Unit, onAlerts: () -> Unit, onSection: (ControlSection) -> Unit,
    onNew: () -> Unit, onRefresh: () -> Unit, onMore: () -> Unit,
    onLog: (TaskRun) -> Unit, onRoute: (TaskRun) -> Unit, onDismissLog: () -> Unit,
    onPipeline: (ManagedPipeline) -> Unit,
    onDeleteRun: (TaskRun) -> Unit = {},
    onDismissMessage: (String) -> Unit = {}
) {
    var tab by rememberSaveable(state.deviceId) { mutableStateOf("전체") }
    val c = LocalCobaltColors.current
    val runs = state.runs.filter { when (tab) {
        "진행 중" -> it.state == "RUNNING"
        "완료" -> it.state in listOf("COMPLETED", "STOPPED", "FAILED", "UNKNOWN")
        else -> true
    } }
    var deleting by remember(state.deviceId, state.online) { mutableStateOf<TaskRun?>(null) }
    deleting?.let { run -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text("작업 이력을 삭제할까요?") },
        text = { Text("${run.label}\n${com.example.jetsoncontroller.ui.storage.localDateTimeLabel(run.startedAt)}\n\n이 실행의 기록·로그·경로를 삭제합니다. 수집 원본 데이터와 작업 등록은 유지됩니다.") },
        confirmButton = { TextButton(onClick = { deleting = null; onDeleteRun(run) },
            enabled = state.online && state.deletingRunId == null && run.state != "RUNNING") { Text("이력 삭제") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }) }
    state.log?.let { text -> AlertDialog(onDismissRequest = onDismissLog,
        title = { Text("저장된 로그 · 최근 64KB") },
        text = { SelectionContainer { Text(text, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = onDismissLog) { Text("닫기") } }) }
    Scaffold(snackbarHost = { OperationMessageHost(state.message, onDismissMessage) },
        topBar = { DeviceContextHeader("작업 이력", deviceName, if (state.online) "전체 실행 기록 · 로그 · 수집 경로" else "오프라인 · 보관된 기록",
        onBack, unreadCount, onAlerts, actions = {
            IconButton(onClick = onNew) { Icon(Icons.Default.AddCircle, "새 작업 시작하기") }
            IconButton(onClick = onRefresh, enabled = state.online && !state.loading) { Icon(Icons.Default.Refresh, "기록 새로고침") }
        }) }, bottomBar = { ControlNavigationBar(ControlSection.PIPELINES, onSection) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("전체", "진행 중", "완료").forEach { label -> FilterChip(tab == label, { tab = label }, label = { Text(label) }) }
            } }
            if (state.loading || state.deletingRunId != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { item { InlineMessage(it, true) } }
            item { Text("실행 기록을 오른쪽으로 밀면 삭제할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = c.muted) }
            items(runs, key = { it.id }) { run ->
                val runPresentation = historyRunPresentation(run, state.online, state.historyCurrent)
                val canDelete = state.online && state.deletingRunId == null && run.state != "RUNNING"
                val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.StartToEnd && canDelete) deleting = run
                    false
                })
                SwipeToDismissBox(state = swipe, enableDismissFromStartToEnd = canDelete,
                    enableDismissFromEndToStart = false,
                    modifier = Modifier.testTag("task-run-${run.id}").semantics {
                        if (canDelete) customActions = listOf(CustomAccessibilityAction("작업 이력 삭제") { deleting = run; true })
                    },
                    backgroundContent = {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.large) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.DeleteOutline, null)
                                Text("삭제", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }) {
                    Surface(color = c.sectionSoft, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (run.state == "RUNNING" && state.online) Icons.Default.PlayCircle else Icons.Default.Assignment, null, tint = c.primary)
                                Text(run.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                StatusBadge(runPresentation.first, runPresentation.second)
                            }
                            Text(com.example.jetsoncontroller.ui.storage.localDateTimeLabel(run.startedAt), style = MaterialTheme.typography.bodySmall, color = c.muted)
                            RunQualityEvidence(run.quality, compact = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onLog(run) }, enabled = state.online, modifier = Modifier.weight(1f)) { Text("실행 로그") }
                                OutlinedButton(onClick = { onRoute(run) }, modifier = Modifier.weight(1f)) { Text("수집 경로") }
                            }
                            if (run.state == "RUNNING") pipelines.firstOrNull { it.id == run.pipelineId }?.let { pipeline ->
                                TextButton(onClick = { onPipeline(pipeline) }) { Text("진행 상태 · 작업 제어") }
                            }
                        }
                    }
                }
            }
            if (runs.isEmpty() && !state.loading) item { EmptyState("표시할 실행 기록 없음", "작업을 실행하면 시작 시간, 결과, 로그와 수집 경로가 여기에 남습니다.") }
            if (state.nextOffset != null) item { OutlinedButton(onClick = onMore, enabled = state.online && !state.loading && state.deletingRunId == null, modifier = Modifier.fillMaxWidth()) { Text("이전 기록 더 불러오기") } }
            item { Surface(onClick = onNew, color = c.sectionRaised, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.AddCircle, "새 작업", Modifier.size(42.dp), tint = c.primary)
                    Column { Text("새 작업 시작하기", style = MaterialTheme.typography.titleMedium); Text("실행할 작업 선택 · 새 작업 등록", style = MaterialTheme.typography.bodySmall) }
                }
            } }
        }
    }
}

internal fun historyRunPresentation(run: TaskRun, online: Boolean, historyCurrent: Boolean): Pair<String, StatusTone> = when {
    run.state == "RUNNING" && (!online || !historyCurrent) -> "최근 실행 보고 · 현재 미확인" to StatusTone.WARNING
    run.state == "RUNNING" -> "진행 중" to StatusTone.INFO
    run.state == "COMPLETED" -> "완료" to StatusTone.SUCCESS
    run.state == "STOPPED" -> "중지" to StatusTone.INFO
    run.state == "FAILED" -> "실패" to StatusTone.ERROR
    else -> "결과 미확인" to StatusTone.WARNING
}
