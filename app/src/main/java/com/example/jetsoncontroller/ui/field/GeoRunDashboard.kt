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
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.*
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors

@Composable
fun GeoRunDashboard(state: FieldState, pipelines: List<ManagedPipeline>, deviceName: String,
    unreadCount: Int, onBack: () -> Unit, onAlerts: () -> Unit, onSection: (ControlSection) -> Unit,
    onNew: () -> Unit, onRefresh: () -> Unit, onMore: () -> Unit,
    onLog: (TaskRun) -> Unit, onRoute: (TaskRun) -> Unit, onDismissLog: () -> Unit,
    onPipeline: (ManagedPipeline) -> Unit
) {
    var tab by rememberSaveable(state.deviceId) { mutableStateOf("전체") }
    val c = LocalCobaltColors.current
    val runs = state.runs.filter { when (tab) {
        "진행 중" -> it.state == "RUNNING"
        "대기" -> false
        "완료" -> it.state in listOf("COMPLETED", "STOPPED", "FAILED", "UNKNOWN")
        else -> true
    } }.let { if (tab == "전체") it.take(10) else it }
    state.log?.let { text -> AlertDialog(onDismissRequest = onDismissLog,
        title = { Text("저장된 로그 · 최근 64KB") },
        text = { SelectionContainer { Text(text, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = onDismissLog) { Text("닫기") } }) }
    Scaffold(topBar = { DeviceContextHeader("작업", deviceName, if (state.online) "작업 실행 기록" else "오프라인 · 보관된 기록",
        onBack, unreadCount, onAlerts, actions = {
            IconButton(onClick = onNew) { Icon(Icons.Default.AddCircle, "새 작업 시작하기") }
            IconButton(onClick = onRefresh, enabled = state.online && !state.loading) { Icon(Icons.Default.Refresh, "기록 새로고침") }
        }) }, bottomBar = { ControlNavigationBar(ControlSection.PIPELINES, onSection) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("전체", "진행 중", "대기", "완료").forEach { label -> FilterChip(tab == label, { tab = label }, label = { Text(label) }) }
            } }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { item { InlineMessage(it, true) } }
            if (tab == "전체") item { Text("최근 실행 10개 · 완료 탭에서 이전 기록을 확인하세요.", style = MaterialTheme.typography.bodySmall, color = c.muted) }
            if (tab == "대기") {
                val waiting = pipelines.filter { it.state in setOf(PipelineState.STOPPED, PipelineState.WAITING_FOR_TIME_SYNC) }
                items(waiting, key = { it.id }) { pipeline ->
                    Surface(onClick = { onPipeline(pipeline) }, color = c.sectionSoft, shape = MaterialTheme.shapes.large) {
                        ListItem(headlineContent = { Text(pipeline.label) }, supportingContent = { Text("등록된 작업 · 시작 대기") },
                            leadingContent = { Icon(Icons.Default.Schedule, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) })
                    }
                }
                if (waiting.isEmpty()) item { EmptyState("대기 중인 작업 없음", "새 작업 시작하기에서 실행할 작업을 선택하세요.") }
            } else {
                items(runs, key = { it.id }) { run ->
                    Surface(color = c.sectionSoft, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (run.state == "RUNNING") Icons.Default.PlayCircle else Icons.Default.Assignment, null, tint = c.primary)
                                Text(run.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                StatusBadge(when (run.state) { "RUNNING" -> "진행 중"; "COMPLETED" -> "완료"; "STOPPED" -> "중지"; "FAILED" -> "실패"; else -> "결과 미확인" },
                                    when (run.state) { "COMPLETED" -> StatusTone.SUCCESS; "FAILED" -> StatusTone.ERROR; "UNKNOWN" -> StatusTone.WARNING; else -> StatusTone.INFO })
                            }
                            Text(com.example.jetsoncontroller.ui.storage.localDateTimeLabel(run.startedAt), style = MaterialTheme.typography.bodySmall, color = c.muted)
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
                if (runs.isEmpty() && !state.loading) item { EmptyState("표시할 실행 기록 없음", "작업을 실행하면 시작 시간, 결과, 로그와 수집 경로가 여기에 남습니다.") }
                if (tab != "전체" && state.nextOffset != null) item { OutlinedButton(onClick = onMore, enabled = state.online && !state.loading, modifier = Modifier.fillMaxWidth()) { Text("이전 기록 더 불러오기") } }
            }
            item { Surface(onClick = onNew, color = c.sectionRaised, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.AddCircle, "새 작업", Modifier.size(42.dp), tint = c.primary)
                    Column { Text("새 작업 시작하기", style = MaterialTheme.typography.titleMedium); Text("실행할 작업 선택 · 새 작업 등록", style = MaterialTheme.typography.bodySmall) }
                }
            } }
        }
    }
}
