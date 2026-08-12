package com.example.jetsoncontroller.ui.storage

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileContent
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceStorageScreen(
    state: DeviceStorageUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit,
    onUploadClick: (String, String) -> Unit,
    onSectionSelected: (ControlSection) -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.preview?.name ?: state.currentRoot?.label ?: "수집 데이터")
                        state.currentRoot?.takeIf { state.preview == null }?.let {
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
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.preview != null -> FilePreview(state.preview)
                state.currentRoot != null -> DirectoryList(
                    state = state,
                    onRefresh = onRefresh,
                    onDirectoryClick = onDirectoryClick,
                    onFileClick = onFileClick,
                    onUploadClick = onUploadClick
                )
                state.error != null -> EmptyState(
                    title = "수집 데이터 저장소를 열 수 없습니다",
                    message = state.error,
                    actionLabel = "다시 확인",
                    onAction = onRefresh
                )
            }
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun DirectoryList(
    state: DeviceStorageUiState,
    onRefresh: () -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit,
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
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
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
                EmptyState("빈 폴더입니다", "이 위치에는 표시할 파일이 없습니다.")
            }
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
                        contentDescription = null,
                        tint = if (directory) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = if (directory) {
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
                modifier = Modifier.clickable {
                    if (directory) onDirectoryClick(entry) else onFileClick(entry)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
private fun FilePreview(content: RemoteFileContent) {
    val image = content.mimeType.startsWith("image/") ||
        content.name.substringAfterLast('.', "").lowercase() in
        setOf("jpg", "jpeg", "png", "webp", "bmp")
    if (image) {
        val bitmap = remember(content.bytes) {
            BitmapFactory.decodeByteArray(content.bytes, 0, content.bytes.size)
        }
        if (bitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = content.name,
                    modifier = Modifier.fillMaxSize()
                )
            }
            return
        }
    }
    val text = remember(content.bytes) {
        content.bytes.takeIf { bytes -> bytes.none { it == 0.toByte() } }
            ?.toString(Charsets.UTF_8)
    }
    if (text != null) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp)
            ) {
                item { Text(text, style = MaterialTheme.typography.bodySmall) }
            }
        }
    } else {
        EmptyState(
            title = "미리볼 수 없는 파일입니다",
            message = "이미지와 UTF-8 텍스트 파일을 앱에서 열 수 있습니다."
        )
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
