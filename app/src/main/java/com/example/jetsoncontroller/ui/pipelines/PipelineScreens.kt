package com.example.jetsoncontroller.ui.pipelines

import com.example.jetsoncontroller.ui.theme.TextButton
import com.example.jetsoncontroller.ui.theme.OutlinedButton
import com.example.jetsoncontroller.ui.theme.Button
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineConfigField
import com.example.jetsoncontroller.model.PipelineConfigValueType
import com.example.jetsoncontroller.model.PipelineLogFile
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.SectionHeader
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineListScreen(
    state: PipelineUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onControl: (ManagedPipeline, String) -> Unit,
    onRemove: (ManagedPipeline) -> Unit,
    onLogs: (ManagedPipeline) -> Unit,
    onConfig: (ManagedPipeline) -> Unit,
    onOutput: (ManagedPipeline) -> Unit,
    onSectionSelected: (ControlSection) -> Unit,
    onClearMessage: () -> Unit,
    deviceName: String = state.deviceId ?: "선택된 장비 없음",
    unreadCount: Int = 0,
    onAlerts: () -> Unit = {},
    onDetails: (ManagedPipeline) -> Unit = onLogs,
    detailId: String? = null,
    startCapability: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
    onHistory: () -> Unit = {}
) {
    var pendingRemoval by remember(state.deviceId, state.controlAvailable) { mutableStateOf<ManagedPipeline?>(null) }
    var pendingStart by remember(state.deviceId, state.controlAvailable) { mutableStateOf<Pair<ManagedPipeline, String>?>(null) }
    val fresh = tasksAreFresh(state.controlAvailable, state.observedAtMillis, nowMillis)
    pendingStart?.let { (pipeline, action) ->
        val current = state.pipelines.firstOrNull { it.id == pipeline.id }
        val ready = current != null && (action == "restart" || current.state in setOf(
            PipelineState.STOPPED, PipelineState.FAILED, PipelineState.WAITING_FOR_TIME_SYNC))
        val checked = fresh && !state.isLoading && state.error == null
        AlertDialog(onDismissRequest = { pendingStart = null }, title = { Text("작업 시작 · 최종 점검") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("대상 장비: $deviceName\n작업: ${pipeline.label}")
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (checked) "✓ 장비 연결과 최신 작업 상태 확인" else "장비 연결과 최신 작업 상태를 확인하고 있습니다.")
                Text(if (startCapability) "✓ 작업 제어와 시간 동기화 지원 확인" else "작업 제어 또는 시간 동기화 지원 여부 미확인")
                Text(if (ready) "✓ 시작 가능한 작업 확인" else "작업이 실행 중이거나 시작 가능한 상태가 아닙니다.")
                state.error?.let { InlineMessage(it, true) }
                Text("확인 후 시작을 누르면 휴대전화 시간으로 동기화한 뒤 작업을 시작합니다. 카메라·GNSS·IMU는 시작 후 상태를 확인합니다.")
            } },
            confirmButton = { Button(shape = MaterialTheme.shapes.small, enabled = checked && ready && startCapability && state.busyPipelineId == null && pipeline.id !in state.pendingActions,
                onClick = { pendingStart = null; current?.let { onControl(it, action) } }, modifier = Modifier.heightIn(min = 52.dp)) { Text("확인 후 시작") } },
            dismissButton = { TextButton(onClick = { pendingStart = null }) { Text("취소") } })
    }
    pendingRemoval?.let { pipeline ->
        AlertDialog(onDismissRequest = { pendingRemoval = null },
            title = { Text("${pipeline.label} 등록을 해제할까요?") },
            text = { Text("대상: $deviceName\n실행 서비스는 중지되며 저장된 실행 스냅샷과 측정 파일은 유지됩니다.") },
            confirmButton = { Button(shape = MaterialTheme.shapes.small, enabled = state.controlAvailable && state.busyPipelineId == null, onClick = {
                pendingRemoval = null; onRemove(pipeline)
            }) { Text("작업 등록 해제") } },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("취소") } })
    }
    Scaffold(
        topBar = {
            com.example.jetsoncontroller.ui.components.DeviceContextHeader(
                if (detailId == null) "작업 시작 · 실행 프로그램 선택" else "작업 상세", deviceName,
                if (fresh) "작업 상태 확인됨" else "현재 상태 미확인", onBack, unreadCount, onAlerts,
                actions = {
                    if (detailId == null) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "작업 추가") }
                    IconButton(onClick = onRefresh, enabled = !state.isLoading && state.busyPipelineId == null) { Icon(Icons.Default.Refresh, "상태 새로고침") }
                })
        },
        bottomBar = { ControlNavigationBar(ControlSection.PIPELINES, onSectionSelected) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("작업 이력")
            } }
            item { Text("전체 ${state.pipelines.size} · 실행 확인 ${if (fresh) state.pipelines.count { it.state == PipelineState.RUNNING && it.id !in state.pendingActions } else 0}",
                style = MaterialTheme.typography.bodyMedium) }
            state.error?.let { item { InlineMessage(it, true) } }
            state.message?.let { item { InlineMessage(it, false) } }
            if (state.mobileRtkRelay.active || state.mobileRtkRelay.error != null) {
                item { InlineMessage(state.mobileRtkRelay.error ?: "모바일 RTK 중계 · RTCM ${state.mobileRtkRelay.bytesFromCaster} bytes · 파일 업로드와 별개", state.mobileRtkRelay.error != null) }
            }
            if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.pipelines.isEmpty() && !state.isLoading) item {
                EmptyState(if (fresh) "등록된 작업이 없습니다" else "작업 상태 확인 필요",
                    if (fresh) "장비에 등록된 Python 작업이 아직 없습니다." else "연결 후 목록을 다시 확인하세요.",
                    actionLabel = "작업 추가", onAction = onAdd)
            }
            items(state.pipelines.filter { detailId == null || it.id == detailId }, key = { it.id }) { pipeline ->
                TaskStateCard(pipeline, fresh, state.pendingActions[pipeline.id], state.busyPipelineId == pipeline.id,
                    controlsEnabled = fresh && state.busyPipelineId == null && pipeline.id !in state.pendingActions,
                    expanded = detailId != null,
                    onDetails = { onDetails(pipeline) },
                    onControl = { action -> if (action in setOf("start", "restart")) {
                        pendingStart = pipeline to action
                        onRefresh()
                    } else onControl(pipeline, action) },
                    onRemove = { pendingRemoval = pipeline }, onLogs = { onLogs(pipeline) },
                    onConfig = { onConfig(pipeline) }, onOutput = { onOutput(pipeline) })
            }
        }
    }
}

@Composable
private fun TaskStateCard(
    pipeline: ManagedPipeline, fresh: Boolean, pendingAction: String?, busy: Boolean,
    controlsEnabled: Boolean, expanded: Boolean, onDetails: () -> Unit,
    onControl: (String) -> Unit, onRemove: () -> Unit, onLogs: () -> Unit,
    onConfig: () -> Unit, onOutput: () -> Unit
) {
    val c = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current
    val active = pipeline.state in setOf(PipelineState.RUNNING, PipelineState.STARTING, PipelineState.RETRYING, PipelineState.STOPPING)
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (active) c.hero else c.surface, contentColor = if (active) c.heroText else c.ink) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(pipeline.label, style = MaterialTheme.typography.titleLarge)
            com.example.jetsoncontroller.ui.components.StatusBadge(taskStateLabel(pipeline.state, fresh, pendingAction),
                when {
                    !fresh -> com.example.jetsoncontroller.ui.components.StatusTone.WARNING
                    pipeline.state == PipelineState.FAILED -> com.example.jetsoncontroller.ui.components.StatusTone.ERROR
                    pipeline.state == PipelineState.RUNNING && pendingAction == null -> com.example.jetsoncontroller.ui.components.StatusTone.SUCCESS
                    else -> com.example.jetsoncontroller.ui.components.StatusTone.INFO
                })
            if (pipeline.result != "unknown") com.example.jetsoncontroller.ui.components.SectionSurface(c.sectionRaised) {
                Text("최근 실행 결과: ${pipeline.result} · 종료 코드 ${pipeline.lastExitCode}", style = MaterialTheme.typography.bodyMedium)
            }
            if (!expanded) {
                Button(shape = MaterialTheme.shapes.small, onClick = onDetails, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = if (active) androidx.compose.material3.ButtonDefaults.buttonColors(c.accent, c.onAccent) else androidx.compose.material3.ButtonDefaults.buttonColors()) { Text("상태 보기") }
            } else {
                Text(pipeline.description.ifBlank { "장비의 실제 실행 상태와 결과를 확인합니다." }, style = MaterialTheme.typography.bodyLarge)
                Text("실행 파일: ${pipeline.entrypoint}", style = MaterialTheme.typography.bodyMedium)
                Button(shape = MaterialTheme.shapes.small, onClick = onLogs, modifier = Modifier.fillMaxWidth()) { Text("실행 로그") }
                Button(shape = MaterialTheme.shapes.small, onClick = onConfig, modifier = Modifier.fillMaxWidth()) { Text("작업 설정") }
                Button(shape = MaterialTheme.shapes.small, onClick = onOutput, enabled = pipeline.outputRootId != null && pipeline.outputPath != null, modifier = Modifier.fillMaxWidth()) { Text("저장 결과") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("부팅 시 실행", modifier = Modifier.weight(1f))
                    Switch(checked = pipeline.enabled, onCheckedChange = { onControl(if (it) "enable" else "disable") }, enabled = controlsEnabled)
                }
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onRemove, enabled = controlsEnabled, modifier = Modifier.fillMaxWidth()) { Text("작업 등록 해제") }
            }
            if (active) {
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onControl("stop") }, enabled = controlsEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = if (active) c.heroText else c.primary)) { Text("중지 요청") }
                if (expanded) Button(shape = MaterialTheme.shapes.small, onClick = { onControl("restart") }, enabled = controlsEnabled, modifier = Modifier.fillMaxWidth()) { Text("다시 시작") }
            } else {
                Button(shape = MaterialTheme.shapes.small, onClick = { onControl("start") }, enabled = controlsEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (pendingAction != null) "요청 확인 중…" else "작업 시작") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!controlsEnabled) Text("연결·최근 상태·진행 중 요청을 확인한 뒤 제어할 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineEditorScreen(
    state: PipelineUiState,
    onBack: () -> Unit,
    onPick: (PipelinePickerTarget) -> Unit,
    onLabelChange: (String) -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    onRegister: () -> Unit
) {
    val draft = state.draft
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("자동 실행 작업 추가") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.error?.let { error ->
                item { InlineMessage(message = error, isError = true) }
            }
            item {
                SectionHeader("작업 정보")
            }
            item {
                OutlinedTextField(colors = com.example.jetsoncontroller.ui.theme.slateTextFieldColors(),
                    value = draft.label,
                    onValueChange = onLabelChange,
                    label = { Text("표시 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { SectionHeader("실행 소스") }
            item {
                SelectorRow(
                    icon = Icons.Default.FolderOpen,
                    title = "작업 폴더",
                    value = selectionLabel(draft.repositoryRoot, draft.repositoryPath),
                    enabled = !state.isLoading,
                    onClick = { onPick(PipelinePickerTarget.REPOSITORY) }
                )
            }
            if (state.isDiscoveringFolder) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            state.discoveredFolder?.let { discovered ->
                item {
                    InlineMessage(
                        message = "${discovered.pipelineId} · ${discovered.entrypoint} · " +
                            "${discovered.config} · 결과 폴더 자동 설정",
                        isError = false
                    )
                }
            }
            item { SectionHeader("실행 설정") }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("부팅 시 자동 실행", fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = draft.autostart,
                            onCheckedChange = onAutostartChange,
                            enabled = !state.isLoading
                        )
                    }
                }
            }
            item {
                Button(shape = MaterialTheme.shapes.small,
                    onClick = onRegister,
                    enabled = state.controlAvailable && draft.canSubmit && state.discoveredFolder != null &&
                        !state.isLoading && !state.isDiscoveringFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("작업 등록", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (state.isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun SelectorRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val c = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) c.primary else c.onDisabled)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = if (enabled) c.ink else c.onDisabled)
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) c.muted else c.onDisabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (enabled) c.muted else c.onDisabled
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelinePickerScreen(
    roots: List<RemoteRoot>,
    state: PipelinePickerState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRootClick: (RemoteRoot) -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit,
    onSelectCurrentDirectory: () -> Unit
) {
    BackHandler(onBack = onBack)
    val directoryTarget = state.target == PipelinePickerTarget.REPOSITORY ||
        state.target == PipelinePickerTarget.VIRTUALENV
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pickerTitle(state.target))
                        if (state.root != null) {
                            Text(
                                state.currentPath.ifEmpty { "/" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (state.root != null) {
                        IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (directoryTarget && state.root != null) {
                Surface(shadowElevation = 4.dp) {
                    Button(shape = MaterialTheme.shapes.small,
                        onClick = onSelectCurrentDirectory,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Text("이 폴더 선택", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(Modifier.fillMaxSize()) {
                state.error?.let { error ->
                    item {
                        InlineMessage(
                            message = error,
                            isError = true,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                if (state.root == null) {
                    items(roots, key = { it.id }) { root ->
                        ListItem(
                            headlineContent = { Text(root.label, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                root.pathHint?.let {
                                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable { onRootClick(root) }
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                } else {
                    val visibleEntries = state.entries.filter { entry ->
                        entry.type == RemoteEntryType.DIRECTORY || fileMatches(entry, state.target)
                    }
                    items(visibleEntries, key = { it.relativePath }) { entry ->
                        val directory = entry.type == RemoteEntryType.DIRECTORY
                        ListItem(
                            headlineContent = {
                                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(if (directory) "폴더" else "파일")
                            },
                            leadingContent = {
                                Icon(
                                    if (directory) Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (directory) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                if (directory) onDirectoryClick(entry) else onFileClick(entry)
                            }
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineLogScreen(
    state: PipelineUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLogSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.selectedLogId, state.logFollowingLatest) {
        snapshotFlow { scrollState.maxValue }.collectLatest { maximum ->
            scrollState.scrollTo(maximum)
        }
    }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("실행 로그") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.detailLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "로그 새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            PipelineLogToolbar(
                files = state.logFiles,
                selectedLogId = state.selectedLogId,
                followingLatest = state.logFollowingLatest,
                live = state.logLive,
                onSelected = onLogSelected
            )
            if (state.error != null) {
                InlineMessage(
                    message = state.error,
                    isError = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            HorizontalDivider()
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.logFiles.isEmpty() && !state.detailLoading -> EmptyState(
                        title = "저장된 실행 로그가 없습니다",
                        message = "작업을 시작하면 실행마다 별도 로그 파일이 생성됩니다."
                    )
                    state.logContent.isEmpty() && !state.detailLoading -> EmptyState(
                        title = "로그 내용이 없습니다",
                        message = "선택한 실행에서는 아직 결과가 기록되지 않았습니다."
                    )
                    else -> SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(16.dp)
                        ) {
                            Text(
                                state.logContent,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
                if (state.detailLoading) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineLogToolbar(
    files: List<PipelineLogFile>,
    selectedLogId: String?,
    followingLatest: Boolean,
    live: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = files.firstOrNull { it.id == selectedLogId }
    Surface(color = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.sectionSoft) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(shape = MaterialTheme.shapes.small,
                    onClick = { expanded = true },
                    enabled = files.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        selected?.let(::pipelineLogStartedLabel) ?: "실행 로그 선택",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    files.forEachIndexed { index, file ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        pipelineLogFileLabel(file),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        when {
                                            file.active -> "현재 실행"
                                            index == 0 -> "최근 실행"
                                            else -> "보관 로그"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null)
                            },
                            onClick = {
                                expanded = false
                                onSelected(file.id)
                            }
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint = if (live) com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.success
                        else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        when {
                            selected?.active == true && live -> "실시간"
                            followingLatest && live -> "자동 갱신"
                            else -> "보관 로그"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                selected?.let {
                    Text(
                        formatPipelineLogSize(it.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineConfigScreen(
    state: PipelineUiState,
    onBack: () -> Unit,
    onValueChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit = {}
) {
    var confirmReload by remember(state.deviceId, state.controlAvailable) { mutableStateOf(false) }
    if (confirmReload) {
        AlertDialog(
            onDismissRequest = { confirmReload = false },
            title = { Text("장비 설정을 다시 불러올까요?") },
            text = { Text("작성 중인 설정 초안을 장비의 현재 설정으로 바꿉니다.") },
            confirmButton = { Button(shape = MaterialTheme.shapes.small, onClick = { confirmReload = false; onReload() }) { Text("다시 불러오기") } },
            dismissButton = { TextButton(onClick = { confirmReload = false }) { Text("초안 유지") } }
        )
    }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("작업 설정")
                        if (state.configPath.isNotBlank()) {
                            Text(
                                state.configPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSave,
                        enabled = state.controlAvailable && !state.configNeedsReview &&
                            !state.detailLoading && !state.configSaving &&
                            state.configHasChanges && state.configValuesValid
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "설정 저장")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { error ->
                    item { InlineMessage(error, isError = true) }
                }
                state.message?.let { message ->
                    item { InlineMessage(message, isError = false) }
                }
                if (state.configNeedsReview) {
                    item {
                        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { confirmReload = true }, enabled = state.controlAvailable) {
                            Text("장비 설정 다시 불러오기")
                        }
                    }
                }
                if (state.configFields.isEmpty() && !state.detailLoading && state.error == null) {
                    item {
                        EmptyState(
                            title = "편집할 설정 값이 없습니다",
                            message = "이 작업 설정에는 변경 가능한 키와 값이 없습니다."
                        )
                    }
                }
                items(state.configFields, key = { it.path }) { field ->
                    PipelineConfigFieldEditor(
                        field = field,
                        enabled = !state.detailLoading && !state.configSaving,
                        onValueChange = { onValueChange(field.path, it) }
                    )
                }
            }
            if (state.detailLoading || state.configSaving) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun PipelineConfigFieldEditor(
    field: PipelineConfigField,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    if (field.type == PipelineConfigValueType.BOOLEAN) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    field.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = field.value == "true",
                    onCheckedChange = { onValueChange(it.toString()) },
                    enabled = enabled
                )
            }
        }
        return
    }

    val keyboardType = when (field.type) {
        PipelineConfigValueType.INTEGER -> KeyboardType.Number
        PipelineConfigValueType.DECIMAL -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
    val valid = configFieldValueValid(field.type, field.value)
    OutlinedTextField(colors = com.example.jetsoncontroller.ui.theme.slateTextFieldColors(),
        value = field.value,
        onValueChange = { onValueChange(it.take(4096)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(field.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = field.type != PipelineConfigValueType.STRING,
        minLines = if (field.type == PipelineConfigValueType.STRING) 1 else 1,
        maxLines = if (field.type == PipelineConfigValueType.STRING) 4 else 1,
        isError = !valid,
        supportingText = if (!valid) {
            {
                Text(
                    if (field.type == PipelineConfigValueType.INTEGER) "정수를 입력하세요."
                    else "유효한 숫자를 입력하세요."
                )
            }
        } else null
    )
}

private fun selectionLabel(root: RemoteRoot?, path: String): String = when {
    root == null -> "선택 안 됨"
    path.isEmpty() -> root.label
    else -> "${root.label} / $path"
}

private fun pickerTitle(target: PipelinePickerTarget?): String = when (target) {
    PipelinePickerTarget.REPOSITORY -> "작업 폴더 선택"
    PipelinePickerTarget.VIRTUALENV -> "가상환경 선택"
    PipelinePickerTarget.ENTRYPOINT -> "메인 Python 선택"
    PipelinePickerTarget.CONFIG -> "작업 설정 파일 선택"
    null -> "경로 선택"
}

private fun fileMatches(entry: RemoteFileEntry, target: PipelinePickerTarget?): Boolean = when (target) {
    PipelinePickerTarget.ENTRYPOINT -> entry.name.endsWith(".py", ignoreCase = true)
    PipelinePickerTarget.CONFIG -> entry.name.endsWith(".yaml", ignoreCase = true) ||
        entry.name.endsWith(".yml", ignoreCase = true)
    else -> false
}


private val pipelineLogTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

private fun pipelineLogStartedLabel(file: PipelineLogFile): String = try {
    pipelineLogTimeFormatter.format(Instant.parse(file.startedAt))
} catch (_: Exception) {
    file.startedAt
}

private fun pipelineLogFileLabel(file: PipelineLogFile): String =
    "${pipelineLogStartedLabel(file)} · ${formatPipelineLogSize(file.sizeBytes)}"

private fun formatPipelineLogSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
