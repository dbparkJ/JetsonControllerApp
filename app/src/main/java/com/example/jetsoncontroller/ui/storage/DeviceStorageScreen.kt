package com.example.jetsoncontroller.ui.storage

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntSize
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
    serverUploadEnabled: Boolean,
    serverUploadDisabledReason: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit,
    onDeleteClick: (RemoteFileEntry) -> Unit,
    onUploadClick: (String, String) -> Unit,
    onSectionSelected: (ControlSection) -> Unit,
    onServerDataClick: () -> Unit = {}
) {
    var pendingDeletion by remember { mutableStateOf<RemoteFileEntry?>(null) }
    pendingDeletion?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("장치 데이터를 삭제할까요?") },
            text = {
                val target = if (entry.type == RemoteEntryType.DIRECTORY) {
                    "${entry.name} 폴더와 내부 데이터를"
                } else {
                    "${entry.name} 파일을"
                }
                Text("$target 장치에서 영구 삭제합니다.")
            },
            confirmButton = {
                Button(onClick = {
                    pendingDeletion = null
                    onDeleteClick(entry)
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("취소") }
            }
        )
    }
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
                        IconButton(
                            onClick = onRefresh,
                            enabled = !state.isLoading && !state.isDeleting
                        ) {
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
                    selected = DataLocation.DEVICE,
                    onDeviceClick = {},
                    onServerClick = onServerDataClick
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.preview != null -> FilePreview(state.preview)
                    state.currentRoot != null -> DirectoryList(
                        state = state,
                        onRefresh = onRefresh,
                        onDirectoryClick = onDirectoryClick,
                        onFileClick = onFileClick,
                        onDeleteClick = { pendingDeletion = it },
                        onUploadClick = onUploadClick,
                        serverUploadEnabled = serverUploadEnabled,
                        serverUploadDisabledReason = serverUploadDisabledReason
                    )
                    state.error != null -> EmptyState(
                        title = "수집 데이터 저장소를 열 수 없습니다",
                        message = state.error,
                        actionLabel = "다시 확인",
                        onAction = onRefresh
                    )
                }
                if (state.isLoading || state.isDeleting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
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
    onDeleteClick: (RemoteFileEntry) -> Unit,
    onUploadClick: (String, String) -> Unit,
    serverUploadEnabled: Boolean,
    serverUploadDisabledReason: String?
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
                        enabled = serverUploadEnabled && !state.isLoading && !state.isDeleting
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Text("업로드", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (!serverUploadEnabled && !serverUploadDisabledReason.isNullOrBlank()) {
            item {
                InlineMessage(
                    message = serverUploadDisabledReason,
                    isError = false,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
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
        state.message?.let { message ->
            item {
                InlineMessage(
                    message = message,
                    isError = false,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
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
                        listOfNotNull(
                            "폴더".takeIf { directory },
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
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!directory) {
                            IconButton(
                                onClick = { onUploadClick(root.id, entry.relativePath) },
                                enabled = serverUploadEnabled && !state.isLoading &&
                                    !state.isDeleting
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = "파일 업로드")
                            }
                        }
                        IconButton(
                            onClick = { onDeleteClick(entry) },
                            enabled = !state.isLoading && !state.isDeleting
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "장치 데이터 삭제")
                        }
                        if (directory) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
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
internal fun FilePreview(content: RemoteFileContent) {
    val image = content.mimeType.startsWith("image/") ||
        content.name.substringAfterLast('.', "").lowercase() in
        setOf("jpg", "jpeg", "png", "webp", "bmp")
    if (image) {
        val bitmap = remember(content.bytes) {
            decodePreviewBitmap(content.bytes)
        }
        if (bitmap != null) {
            ZoomableImagePreview(content.name, bitmap.asImageBitmap())
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

private fun decodePreviewBitmap(bytes: ByteArray): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_PREVIEW_DIMENSION) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    )
}

@Composable
private fun ZoomableImagePreview(
    name: String,
    image: androidx.compose.ui.graphics.ImageBitmap
) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun updateTransform(newScale: Float, newOffset: Offset = offset) {
        scale = newScale.coerceIn(1f, 6f)
        val maxX = viewport.width * (scale - 1f) / 2f
        val maxY = viewport.height * (scale - 1f) / 2f
        offset = if (scale == 1f) {
            Offset.Zero
        } else {
            Offset(
                newOffset.x.coerceIn(-maxX, maxX),
                newOffset.y.coerceIn(-maxY, maxY)
            )
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        updateTransform(scale * zoomChange, offset + panChange)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .transformable(transformState),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = image,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    clip = true
                }
        )
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 3.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { updateTransform(scale / 1.5f) },
                    enabled = scale > 1f
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "축소")
                }
                IconButton(
                    onClick = { updateTransform(1f, Offset.Zero) },
                    enabled = scale > 1f
                ) {
                    Icon(Icons.Default.FitScreen, contentDescription = "화면에 맞춤")
                }
                IconButton(
                    onClick = { updateTransform(scale * 1.5f) },
                    enabled = scale < 6f
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "확대")
                }
            }
        }
    }
}

internal fun formatSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "$bytes B" else "%.1f %s".format(value, units[index])
}

private const val MAX_PREVIEW_DIMENSION = 2048
