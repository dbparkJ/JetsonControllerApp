package com.example.jetsoncontroller.ui.storage

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.RemoteFileContent
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
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
    onServerDataClick: () -> Unit = {},
    onTransferQueue: () -> Unit = {},
    thumbnailLoader: (suspend (RemoteFileEntry) -> Result<RemoteFileContent>)? = null,
    onDismissMessage: (String) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    deviceName: String = "선택한 장치"
) {
    DataHubScreen(
        deviceName = deviceName,
        state = state,
        serverUploadEnabled = serverUploadEnabled,
        unavailableReason = serverUploadDisabledReason.orEmpty(),
        unreadCount = 0,
        onDevices = onBack,
        onAlerts = {},
        onRefresh = onRefresh,
        onNavigateBack = onBack,
        onDirectoryClick = onDirectoryClick,
        onFileClick = onFileClick,
        onDeleteClick = onDeleteClick,
        onHistory = onTransferQueue,
        onTransfer = onUploadClick,
        onSection = onSectionSelected,
        onServerData = onServerDataClick,
        onDismissMessage = onDismissMessage,
        onUndoDelete = onUndoDelete
    )
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
