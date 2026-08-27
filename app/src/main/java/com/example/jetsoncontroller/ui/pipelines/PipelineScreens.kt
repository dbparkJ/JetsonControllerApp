package com.example.jetsoncontroller.ui.pipelines

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
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onClearMessage: () -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<ManagedPipeline?>(null) }
    pendingRemoval?.let { pipeline ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text("${pipeline.label} 등록을 해제할까요?") },
            text = { Text("실행 서비스는 중지되며 저장된 실행 스냅샷은 시스템 보관 영역에 남습니다.") },
            confirmButton = {
                Button(onClick = {
                    pendingRemoval = null
                    onRemove(pipeline)
                }) { Text("등록 해제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("자동 실행 작업") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("작업 추가") }
            )
        },
        bottomBar = {
            ControlNavigationBar(ControlSection.PIPELINES, onSectionSelected)
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { error ->
                    item {
                        InlineMessage(
                            message = error,
                            isError = true,
                            modifier = Modifier.clickable(onClick = onClearMessage)
                        )
                    }
                }
                state.message?.let { message ->
                    item {
                        InlineMessage(
                            message = message,
                            isError = false,
                            modifier = Modifier.clickable(onClick = onClearMessage)
                        )
                    }
                }
                if (state.mobileRtkRelay.active || state.mobileRtkRelay.error != null) {
                    item {
                        val relay = state.mobileRtkRelay
                        val transferred = relay.bytesFromCaster
                        InlineMessage(
                            message = relay.error ?: buildString {
                                append(relay.message ?: "모바일 데이터 RTK 중계 중")
                                if (transferred > 0) {
                                    append(" · RTCM ")
                                    append(transferred)
                                    append(" bytes")
                                }
                            },
                            isError = relay.error != null
                        )
                    }
                }
                if (state.pipelines.isEmpty() && !state.isLoading && state.error == null) {
                    item {
                        EmptyState(
                            title = "등록된 작업이 없습니다",
                            message = "장비에 등록된 Python 작업이 아직 없습니다.",
                            actionLabel = "작업 추가",
                            onAction = onAdd
                        )
                    }
                }
                items(state.pipelines, key = { it.id }) { pipeline ->
                    PipelineItem(
                        pipeline = pipeline,
                        busy = state.busyPipelineId == pipeline.id,
                        controlsEnabled = state.busyPipelineId == null,
                        onControl = { action -> onControl(pipeline, action) },
                        onRemove = { pendingRemoval = pipeline },
                        onLogs = { onLogs(pipeline) },
                        onConfig = { onConfig(pipeline) },
                        onOutput = { onOutput(pipeline) }
                    )
                }
            }
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun PipelineItem(
    pipeline: ManagedPipeline,
    busy: Boolean,
    controlsEnabled: Boolean,
    onControl: (String) -> Unit,
    onRemove: () -> Unit,
    onLogs: () -> Unit,
    onConfig: () -> Unit,
    onOutput: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pipeline.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = pipelineStateColor(pipeline.state),
                            contentColor = Color.White,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                pipelineStateLabel(pipeline.state),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove, enabled = controlsEnabled && !busy) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "작업 등록 해제")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onLogs, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                    Text("로그", modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = onConfig, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Text("설정", modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(
                    onClick = onOutput,
                    enabled = pipeline.outputRootId != null && pipeline.outputPath != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text("결과", modifier = Modifier.padding(start = 4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("부팅 시 실행", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = pipeline.enabled,
                    onCheckedChange = { onControl(if (it) "enable" else "disable") },
                    enabled = controlsEnabled && !busy,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                if (pipeline.state == PipelineState.RUNNING ||
                    pipeline.state == PipelineState.STARTING ||
                    pipeline.state == PipelineState.RETRYING
                ) {
                    IconButton(
                        onClick = { onControl("stop") },
                        enabled = controlsEnabled && !busy
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "중지")
                    }
                    IconButton(
                        onClick = { onControl("restart") },
                        enabled = controlsEnabled && !busy
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "다시 시작")
                    }
                } else {
                    IconButton(
                        onClick = { onControl("start") },
                        enabled = controlsEnabled && !busy
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "시작")
                    }
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
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
                OutlinedTextField(
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
                Button(
                    onClick = onRegister,
                    enabled = draft.canSubmit && state.discoveredFolder != null &&
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
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
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
                    Button(
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(
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
                        tint = if (live) Color(0xFF237A45)
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
    onSave: () -> Unit
) {
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
                        enabled = !state.detailLoading && !state.configSaving &&
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
    OutlinedTextField(
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

private fun pipelineStateLabel(state: PipelineState): String = when (state) {
    PipelineState.RUNNING -> "실행 중"
    PipelineState.STARTING -> "시작 중"
    PipelineState.STOPPING -> "중지 중"
    PipelineState.STOPPED -> "중지됨"
    PipelineState.FAILED -> "오류"
    PipelineState.RETRYING -> "재시도 대기"
    PipelineState.WAITING_FOR_TIME_SYNC -> "모바일 시간 동기화 대기"
    PipelineState.UNKNOWN -> "확인 필요"
}

private val pipelineLogTimeFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

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

@Composable
private fun pipelineStateColor(state: PipelineState): Color = when (state) {
    PipelineState.RUNNING -> Color(0xFF237A45)
    PipelineState.STARTING,
    PipelineState.RETRYING,
    PipelineState.WAITING_FOR_TIME_SYNC,
    PipelineState.STOPPING -> MaterialTheme.colorScheme.tertiary
    PipelineState.FAILED -> MaterialTheme.colorScheme.error
    PipelineState.STOPPED -> MaterialTheme.colorScheme.outline
    PipelineState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
