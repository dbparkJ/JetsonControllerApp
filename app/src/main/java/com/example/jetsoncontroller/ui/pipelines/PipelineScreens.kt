package com.example.jetsoncontroller.ui.pipelines

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineListScreen(
    state: PipelineUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onControl: (ManagedPipeline, String) -> Unit,
    onRemove: (ManagedPipeline) -> Unit,
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
                        onRemove = { pendingRemoval = pipeline }
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
    onRemove: () -> Unit
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
                        Text(
                            pipeline.id,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRemove, enabled = controlsEnabled && !busy) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "작업 등록 해제")
                }
            }
            Spacer(Modifier.height(12.dp))
            PipelineMetadata(Icons.Default.Code, pipeline.entrypoint)
            PipelineMetadata(Icons.Default.Description, pipeline.config)
            PipelineMetadata(
                Icons.Default.Terminal,
                listOfNotNull(
                    pipeline.pythonVersion.takeIf { it.isNotBlank() },
                    pipeline.sourceBranch.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
            )
            if (pipeline.sourceDirty) {
                Text(
                    "미커밋 변경 포함 스냅샷",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (pipeline.state == PipelineState.RETRYING || pipeline.state == PipelineState.FAILED) {
                Text(
                    "최근 종료 코드 ${pipeline.lastExitCode} · 자동 재시작 ${pipeline.restartCount}회",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
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

@Composable
private fun PipelineMetadata(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    if (text.isBlank()) return
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineEditorScreen(
    state: PipelineUiState,
    onBack: () -> Unit,
    onPick: (PipelinePickerTarget) -> Unit,
    onIdChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onWritableDirectoryChange: (String) -> Unit,
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
            item {
                OutlinedTextField(
                    value = draft.id,
                    onValueChange = onIdChange,
                    label = { Text("작업 ID") },
                    supportingText = { Text("영문 소문자, 숫자, 점, 하이픈") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { SectionHeader("실행 소스") }
            item {
                SelectorRow(
                    icon = Icons.Default.FolderOpen,
                    title = "Git 레포",
                    value = selectionLabel(draft.repositoryRoot, draft.repositoryPath),
                    enabled = !state.isLoading,
                    onClick = { onPick(PipelinePickerTarget.REPOSITORY) }
                )
            }
            item {
                SelectorRow(
                    icon = Icons.Default.Terminal,
                    title = "가상환경",
                    value = selectionLabel(draft.virtualenvRoot, draft.virtualenvPath),
                    enabled = !state.isLoading,
                    onClick = { onPick(PipelinePickerTarget.VIRTUALENV) }
                )
            }
            item {
                SelectorRow(
                    icon = Icons.Default.Code,
                    title = "메인 Python",
                    value = draft.entrypoint.ifBlank { "선택 안 됨" },
                    enabled = draft.repositoryRoot != null && !state.isLoading,
                    onClick = { onPick(PipelinePickerTarget.ENTRYPOINT) }
                )
            }
            item {
                SelectorRow(
                    icon = Icons.Default.Description,
                    title = "YAML 설정",
                    value = draft.config,
                    enabled = draft.repositoryRoot != null && !state.isLoading,
                    onClick = { onPick(PipelinePickerTarget.CONFIG) }
                )
            }
            item { SectionHeader("실행 설정") }
            item {
                OutlinedTextField(
                    value = draft.writableDirectory,
                    onValueChange = onWritableDirectoryChange,
                    label = { Text("출력 폴더") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
                            Text(
                                "systemd 활성화",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    enabled = draft.canSubmit && !state.isLoading,
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

private fun selectionLabel(root: RemoteRoot?, path: String): String = when {
    root == null -> "선택 안 됨"
    path.isEmpty() -> root.label
    else -> "${root.label} / $path"
}

private fun pickerTitle(target: PipelinePickerTarget?): String = when (target) {
    PipelinePickerTarget.REPOSITORY -> "Git 레포 선택"
    PipelinePickerTarget.VIRTUALENV -> "가상환경 선택"
    PipelinePickerTarget.ENTRYPOINT -> "메인 Python 선택"
    PipelinePickerTarget.CONFIG -> "YAML 설정 선택"
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
    PipelineState.UNKNOWN -> "확인 필요"
}

@Composable
private fun pipelineStateColor(state: PipelineState): Color = when (state) {
    PipelineState.RUNNING -> Color(0xFF237A45)
    PipelineState.STARTING,
    PipelineState.RETRYING,
    PipelineState.STOPPING -> MaterialTheme.colorScheme.tertiary
    PipelineState.FAILED -> MaterialTheme.colorScheme.error
    PipelineState.STOPPED -> MaterialTheme.colorScheme.outline
    PipelineState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
