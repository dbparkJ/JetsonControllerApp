package com.example.jetsoncontroller.ui.storage

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.UploadLibrarySession
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStorageScreen(
    state: ServerStorageUiState,
    onBack: () -> Unit,
    onDeviceDataClick: () -> Unit,
    onRefresh: () -> Unit,
    onTargetSelected: (UploadTarget) -> Unit,
    onSessionClick: (UploadLibrarySession) -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit,
    onLoadMore: () -> Unit,
    onSectionSelected: (ControlSection) -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.preview?.name ?: state.selectedSession?.sourceName ?: "서버 데이터")
                        state.selectedSession?.takeIf { state.preview == null }?.let {
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
                    if (state.preview == null) {
                        IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                    }
                }
            )
        },
        bottomBar = {
            ControlNavigationBar(ControlSection.DATA, onSectionSelected)
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            if (state.preview == null) {
                DataLocationTabs(
                    selected = DataLocation.SERVER,
                    onDeviceClick = onDeviceDataClick,
                    onServerClick = {}
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.preview != null -> FilePreview(state.preview)
                    state.selectedSession != null -> ServerDirectoryList(
                        state = state,
                        onRefresh = onRefresh,
                        onDirectoryClick = onDirectoryClick,
                        onFileClick = onFileClick
                    )
                    else -> ServerSessionList(
                        state = state,
                        onRefresh = onRefresh,
                        onTargetSelected = onTargetSelected,
                        onSessionClick = onSessionClick,
                        onLoadMore = onLoadMore
                    )
                }
                if (state.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
}

@Composable
private fun ServerSessionList(
    state: ServerStorageUiState,
    onRefresh: () -> Unit,
    onTargetSelected: (UploadTarget) -> Unit,
    onSessionClick: (UploadLibrarySession) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            ServerTargetSelector(
                targets = state.targets,
                selected = state.selectedTarget,
                onSelected = onTargetSelected
            )
        }
        state.error?.let { error ->
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    InlineMessage(error, isError = true)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("다시 불러오기", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (state.sessions.isEmpty() && !state.isLoading && state.error == null) {
            item {
                EmptyState(
                    title = "서버 데이터가 없습니다",
                    message = "완료된 업로드가 이 서버에 아직 없습니다."
                )
            }
        }
        items(state.sessions, key = { it.sessionId }) { session ->
            ListItem(
                headlineContent = {
                    Text(session.sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            "${session.fileCount}개 파일",
                            formatSize(session.totalBytes),
                            session.completedAt?.substringBefore('T')
                        ).joinToString(" · ")
                    )
                },
                leadingContent = { Icon(Icons.Default.Cloud, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onSessionClick(session) }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
        if (state.nextOffset != null) {
            item {
                OutlinedButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    enabled = !state.isLoading
                ) {
                    Text("더 불러오기")
                }
            }
        }
    }
}

@Composable
private fun ServerTargetSelector(
    targets: List<UploadTarget>,
    selected: UploadTarget?,
    onSelected: (UploadTarget) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null)
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = targets.isNotEmpty()
                ) {
                    Text(
                        selected?.label ?: "서버 선택",
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
                    targets.forEach { target ->
                        DropdownMenuItem(
                            text = { Text(target.label) },
                            onClick = {
                                expanded = false
                                onSelected(target)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerDirectoryList(
    state: ServerStorageUiState,
    onRefresh: () -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Text(
                        state.currentPath.ifEmpty { "/" },
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        state.error?.let { error ->
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    InlineMessage(error, isError = true)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("다시 불러오기", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (state.listingTruncated) {
            item {
                InlineMessage(
                    "항목이 많아 현재 폴더의 처음 500개만 표시합니다.",
                    isError = false,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
        if (state.entries.isEmpty() && !state.isLoading && state.error == null) {
            item { EmptyState("빈 폴더입니다", "이 위치에는 표시할 파일이 없습니다.") }
        }
        items(state.entries, key = { it.relativePath }) { entry ->
            val directory = entry.type == RemoteEntryType.DIRECTORY
            ListItem(
                headlineContent = {
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        if (directory) "폴더" else listOfNotNull(
                            entry.sizeBytes?.let(::formatSize),
                            entry.modifiedAt?.substringBefore('T')
                        ).joinToString(" · ")
                    )
                },
                leadingContent = {
                    Icon(
                        if (directory) Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = null
                    )
                },
                trailingContent = if (directory) {
                    { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                } else null,
                modifier = Modifier.clickable {
                    if (directory) onDirectoryClick(entry) else onFileClick(entry)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}
