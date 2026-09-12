package com.example.jetsoncontroller.ui.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter

internal val mediaCategories = listOf("전체", "이미지", "영상", "센서", "로그")
internal fun mediaCategory(entry: RemoteFileEntry): String = when (entry.name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "webp", "bmp", "heic" -> "이미지"
    "mp4", "mkv", "avi", "mov", "h264", "h265" -> "영상"
    "csv", "nmea", "ubx", "pcd", "bin", "json", "jsonl", "bag" -> "센서"
    "log", "txt" -> "로그"
    else -> "기타"
}
internal fun localDate(value: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    value?.let { runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(it).atZoneSameInstant(zone).toLocalDate() }.getOrNull() }
internal fun dateGroup(value: String?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String =
    when (val date = localDate(value, zone)) { today -> "오늘"; today.minusDays(1) -> "어제"; null -> "날짜 미확인"; else -> date.toString() }
internal fun localDateTimeLabel(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"))
}.getOrDefault(value)

@Composable
internal fun MediaFilters(selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        mediaCategories.forEach { name -> FilterChip(selected == name, { onSelected(name) }, label = { Text(name) }) }
    }
}
private val thumbnailPermits = Semaphore(2)

@Composable
internal fun MediaThumbnail(entry: RemoteFileEntry, sourceKey: String,
    load: (suspend (RemoteFileEntry) -> Result<RemoteFileContent>)?) {
    var bitmap by remember(sourceKey, entry.relativePath, entry.modifiedAt) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(sourceKey, entry.relativePath, entry.modifiedAt, load != null) {
        if (load != null && entry.type == RemoteEntryType.FILE && mediaCategory(entry) == "이미지" && (entry.sizeBytes ?: 0) <= 16 * 1024 * 1024) {
            thumbnailPermits.withPermit {
                load(entry).onSuccess { file ->
                    bitmap = withContext(Dispatchers.Default) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size, bounds)
                        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                            var sample = 1
                            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 160) sample *= 2
                            BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        } else null
                    }
                }
            }
        }
    }
    val current = bitmap
    if (current != null) Image(current.asImageBitmap(), entry.name, Modifier.size(48.dp), contentScale = ContentScale.Crop)
    else Icon(when { entry.type == RemoteEntryType.DIRECTORY -> Icons.Default.Folder
        mediaCategory(entry) == "이미지" -> Icons.Default.Image
        mediaCategory(entry) == "영상" -> Icons.Default.Videocam
        mediaCategory(entry) == "센서" -> Icons.Default.Sensors
        else -> Icons.Default.Description }, null, Modifier.size(36.dp))
}
