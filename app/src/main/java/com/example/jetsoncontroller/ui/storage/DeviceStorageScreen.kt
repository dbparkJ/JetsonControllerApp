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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.GeoFixedColors
import com.example.jetsoncontroller.ui.theme.GeoShape

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
    onSectionSelected: (GeoTab) -> Unit,
    onServerDataClick: () -> Unit = {},
    onTransferQueue: () -> Unit = {},
    thumbnailLoader: (suspend (RemoteFileEntry) -> Result<RemoteFileContent>)? = null,
    onDismissMessage: (String) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onTrash: (() -> Unit)? = null,
    ongoingRun: @Composable () -> Unit = {},
    onConnectionRemedy: () -> Unit = onBack,
    onReconnect: () -> Unit = onConnectionRemedy
) {
    DataHubScreen(
        state = state,
        serverUploadEnabled = serverUploadEnabled,
        unavailableReason = serverUploadDisabledReason.orEmpty(),
        unreadCount = 0,
        onDevices = onConnectionRemedy,
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
        onUndoDelete = onUndoDelete,
        onTrash = onTrash,
        ongoingRun = ongoingRun,
        onReconnect = onReconnect
    )
}

@Composable
internal fun FilePreview(content: RemoteFileContent, deviceName: String? = null, entry: RemoteFileEntry? = null) {
    val details = listOfNotNull(deviceName?.let { "장치" to it },
        "크기" to formatSize(entry?.sizeBytes ?: content.bytes.size.toLong()),
        entry?.modifiedAt?.let { "수정 시각" to localDateTimeLabel(it) })
    val image = content.mimeType.startsWith("image/") ||
        content.name.substringAfterLast('.', "").lowercase() in
        setOf("jpg", "jpeg", "png", "webp", "bmp")
    if (image) {
        val bitmap = remember(content.bytes) {
            decodePreviewBitmap(content.bytes)
        }
        if (bitmap != null) {
            ZoomableImagePreview(content.name, bitmap.asImageBitmap(), remember(content.bytes) { previewImageDimensions(content.bytes) },
                details)
            return
        }
    }
    val text = remember(content.bytes, image) { if (image) null else previewText(content.bytes) }
    if (text != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(LocalGeoColors.current.surface),
                contentPadding = PaddingValues(storageGutter, 16.dp)
            ) {
                item { PreviewFileInformation(content.name, details, Modifier.padding(bottom = 24.dp)) }
                item { SelectionContainer { Text(text, style = LocalGeoType.current.mono, color = LocalGeoColors.current.text) } }
            }
    } else {
        Column(Modifier.fillMaxSize().background(LocalGeoColors.current.canvas).padding(storageGutter)
            .verticalScroll(rememberScrollState())) {
            PreviewFileInformation(content.name, details, Modifier.padding(bottom = 24.dp))
            GeoStateBlock(GeoStateContent(GeoStateKind.UNSUPPORTED, "미리 볼 수 없는 파일입니다",
                "이미지와 텍스트 파일만 앱에서 볼 수 있습니다."), fullScreen = true)
        }
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
internal fun ZoomableImagePreview(
    name: String,
    image: androidx.compose.ui.graphics.ImageBitmap,
    dimensions: String? = null,
    details: List<Pair<String, String>> = emptyList()
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

    val c = LocalGeoColors.current
    CompositionLocalProvider(LocalGeoColors provides c.copy(text = GeoFixedColors.mediaText,
        textSecondary = GeoFixedColors.mediaPending, surface = GeoFixedColors.mediaBadge,
        primary = GeoFixedColors.mediaText)) {
    BoxWithConstraints(Modifier.fillMaxSize().background(GeoFixedColors.mediaStage)) {
    val mediaHeight = (maxHeight * .62f).coerceAtLeast(240.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Box(
        modifier = Modifier
            .fillMaxWidth().height(mediaHeight)
            .background(GeoFixedColors.mediaStage)
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
        dimensions?.let {
            Text(it, Modifier.align(Alignment.BottomStart).padding(16.dp)
                .background(GeoFixedColors.mediaPlate, GeoShape.badge).padding(8.dp),
                style = LocalGeoType.current.caption, color = GeoFixedColors.mediaText)
        }
        Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GeoMediaIconButton(GeoIcons.zoomOut, "축소", { updateTransform(scale / 1.5f) })
                GeoMediaIconButton(GeoIcons.fitZone, "화면에 맞춤", { updateTransform(1f, Offset.Zero) })
                GeoMediaIconButton(GeoIcons.zoomIn, "확대", { updateTransform(scale * 1.5f) })
            }
        }
    }
    PreviewFileInformation(name, details, Modifier.padding(horizontal = storageGutter, vertical = 24.dp))
    }
    }
    }
}

@Composable
private fun PreviewFileInformation(name: String, details: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    R7Card(modifier, contentPadding = PaddingValues(16.dp)) {
        GeoCopyableValue(name, label = "파일명")
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            GeoPropertyList(details.map { it.first }) {
                details.forEach { (label, value) -> GeoFactRow(label, value) }
            }
        }
    }
}

internal fun formatSize(bytes: Long): String {
    return com.example.jetsoncontroller.ui.model.Formats.size(bytes)
}

internal fun previewImageDimensions(bytes: ByteArray): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) "${bounds.outWidth} × ${bounds.outHeight}" else null
}

/** Text and unsupported files retain a detail header so floating media tools cannot cover them. */
internal fun usesImageMediaFrame(content: RemoteFileContent): Boolean {
    val image = content.mimeType.startsWith("image/") ||
        content.name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "bmp")
    return image && previewImageDimensions(content.bytes) != null
}

private const val MAX_PREVIEW_DIMENSION = 2048
