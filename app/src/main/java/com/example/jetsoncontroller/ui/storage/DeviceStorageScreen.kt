package com.example.jetsoncontroller.ui.storage

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceStorageScreen(
    state: DeviceStorageUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRootClick: (RemoteRoot) -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onUploadClick: (String, String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.currentRoot?.label ?: "저장소")
                        state.currentRoot?.let {
                            Text(
                                text = state.currentPath.ifEmpty { "/" },
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
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.currentRoot == null) {
                RootList(
                    roots = state.roots,
                    error = state.error,
                    loading = state.isLoading,
                    onRefresh = onRefresh,
                    onRootClick = onRootClick
                )
            } else {
                DirectoryList(
                    state = state,
                    onRefresh = onRefresh,
                    onDirectoryClick = onDirectoryClick,
                    onUploadClick = onUploadClick
                )
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun RootList(
    roots: List<RemoteRoot>,
    error: String?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onRootClick: (RemoteRoot) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (error != null) {
            item {
                InlineMessage(
                    message = error,
                    isError = true,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        if (roots.isEmpty() && !loading) {
            item {
                EmptyState(
                    title = "사용 가능한 저장소가 없습니다",
                    message = "Jetson 저장소 설정과 접근 권한을 확인하세요.",
                    actionLabel = "다시 확인",
                    onAction = onRefresh
                )
            }
        }

        items(roots, key = { root -> root.id }) { root ->
            ListItem(
                headlineContent = {
                    Text(root.label, fontWeight = FontWeight.SemiBold)
                },
                supportingContent = {
                    Column {
                        root.pathHint?.let { hint ->
                            Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (root.totalBytes != null && root.availableBytes != null) {
                            Text(
                                "${formatSize(root.availableBytes)} 여유 / ${formatSize(root.totalBytes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                },
                modifier = Modifier.clickable { onRootClick(root) }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
private fun DirectoryList(
    state: DeviceStorageUiState,
    onRefresh: () -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onUploadClick: (String, String) -> Unit
) {
    val root = state.currentRoot ?: return
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
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text(
                        text = state.currentPath.ifEmpty { "/" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { onUploadClick(root.id, state.currentPath) },
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Text("업로드", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        state.error?.let { error ->
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    InlineMessage(message = error, isError = true)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("다시 불러오기", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        if (state.entries.isEmpty() && !state.isLoading && state.error == null) {
            item {
                EmptyState(
                    title = "빈 폴더입니다",
                    message = "이 위치에는 표시할 파일이 없습니다."
                )
            }
        }

        items(
            items = state.entries,
            key = { entry -> entry.relativePath }
        ) { entry ->
            val isDirectory = entry.type == RemoteEntryType.DIRECTORY
            ListItem(
                headlineContent = {
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        text = if (isDirectory) {
                            "폴더"
                        } else {
                            listOfNotNull(
                                entry.sizeBytes?.let(::formatSize),
                                entry.modifiedAt?.substringBefore('T')
                            ).joinToString(" · ")
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (isDirectory) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = if (isDirectory) {
                    { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                } else {
                    {
                        IconButton(
                            onClick = { onUploadClick(root.id, entry.relativePath) },
                            enabled = !state.isLoading
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = "파일 업로드")
                        }
                    }
                },
                modifier = if (isDirectory) {
                    Modifier.clickable { onDirectoryClick(entry) }
                } else {
                    Modifier
                }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

private fun formatSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "$bytes B" else "%.1f %s".format(value, units[index])
}
