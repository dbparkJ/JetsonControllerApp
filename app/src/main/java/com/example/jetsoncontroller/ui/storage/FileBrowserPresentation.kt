package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.TrashEntry
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.ZoneId

internal const val MAX_TEXT_PREVIEW_BYTES = 12 * 1024 * 1024

internal fun previewText(bytes: ByteArray): String? {
    if (bytes.size > MAX_TEXT_PREVIEW_BYTES || bytes.any { it == 0.toByte() }) return null
    return runCatching {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()
}

internal fun browserEntries(entries: List<RemoteFileEntry>, query: String, category: String): List<RemoteFileEntry> =
    entries.filter { it.name.contains(query, true) &&
        (category == "전체" || it.type == RemoteEntryType.DIRECTORY || mediaCategory(it) == category) }
        .sortedWith(compareByDescending<RemoteFileEntry> { it.modifiedAt.orEmpty() }.thenBy { it.name.lowercase() })

internal fun browserDay(value: String?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String =
    when (val date = localDate(value, zone)) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        null -> "날짜 미확인"
        else -> "${date.monthValue}월 ${date.dayOfMonth}일"
    }

internal data class DataDeletionSnapshot(
    val deviceId: String?, val rootId: String, val parentPath: String, val entry: RemoteFileEntry
)

internal fun dataDeletionSnapshot(state: DeviceStorageUiState, entry: RemoteFileEntry): DataDeletionSnapshot? =
    state.currentRoot?.id?.let { DataDeletionSnapshot(state.deviceId, it, state.currentPath, entry) }

internal fun DataDeletionSnapshot.matches(state: DeviceStorageUiState): Boolean =
    deviceId != null && deviceId == state.deviceId && rootId == state.currentRoot?.id && parentPath == state.currentPath &&
        state.entries.any { it == entry } && state.controlAvailable && !state.isDeleting && !state.isLoading

internal data class DeviceTrashUndoTarget(val deviceId: String, val trashId: String, val rootId: String, val originalPath: String) {
    fun matches(entry: TrashEntry): Boolean = entry.trashId == trashId && entry.rootId == rootId &&
        entry.relativePath == originalPath && entry.restoreSupported && entry.state == "TRASHED"
}

internal fun deviceTrashUndoTarget(state: DeviceStorageUiState): DeviceTrashUndoTarget? {
    return DeviceTrashUndoTarget(state.deviceId ?: return null, state.undoTrashId ?: return null,
        state.undoRootId ?: return null, state.undoOriginalPath ?: return null)
}

/** An entry point can revisit its own location without borrowing another browser's selection. */
internal class FileBrowserLocations {
    private val locations = mutableMapOf<Pair<String, String?>, DeviceStorageUiState>()
    private var active: Pair<String, String?>? = null

    fun select(entryPoint: String, deviceId: String?, current: DeviceStorageUiState): DeviceStorageUiState {
        val key = entryPoint to deviceId?.lowercase()
        if (active == key) return current
        active?.let { locations[it] = current.copy(isLoading = false, isDeleting = false, preview = null) }
        active = key
        return (locations[key] ?: DeviceStorageUiState()).copy(deviceId = deviceId)
    }
}

internal fun storageObjectName(name: String): String {
    val last = name.lastOrNull() ?: return name
    val hasFinalConsonant = if (last in '가'..'힣') (last.code - '가'.code) % 28 != 0 else last in "013678"
    return name + if (hasFinalConsonant) "을" else "를"
}
