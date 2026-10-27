package com.example.jetsoncontroller.ui.transfers

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.UploadSourceSummary
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.storage.DeviceStorageUiState
import com.example.jetsoncontroller.ui.components.geo.*

/** Send-mode adapter; the data view model retains its own location independently of route browsing. */
@Composable
internal fun TransferFilesScreen(
    state: DeviceStorageUiState,
    targets: List<UploadTarget>,
    summary: UploadSourceSummary?,
    calculating: Boolean,
    sending: Boolean,
    sendEnabled: Boolean,
    disabledReason: String,
    onBack: () -> Unit,
    onParent: () -> Unit,
    onDirectory: (RemoteFileEntry) -> Unit,
    onFilePreview: (RemoteFileEntry) -> Unit,
    onSelect: (String, String) -> Unit,
    onStart: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    onRemedy: () -> Unit,
    error: String? = null,
    ongoingRun: @Composable () -> Unit = {}
) {
    com.example.jetsoncontroller.ui.storage.StorageSendBrowser(
        state = state, targets = targets, summary = summary, calculating = calculating,
        sending = sending, sendEnabled = sendEnabled, disabledReason = disabledReason,
        onBack = onBack, onParent = onParent, onDirectory = onDirectory,
        onFilePreview = onFilePreview, onSelect = onSelect, onStart = onStart,
        onRefresh = onRefresh, onRemedy = onRemedy, error = error, ongoingRun = ongoingRun
    )
}

internal fun transferFileTypeMatches(entry: RemoteFileEntry, type: Int): Boolean {
    if (entry.type == com.example.jetsoncontroller.model.RemoteEntryType.DIRECTORY || type == 0) return true
    val extension = entry.name.substringAfterLast('.', "").lowercase()
    return when (type) {
        1 -> extension in setOf("jpg", "jpeg", "png", "webp")
        2 -> extension in setOf("mp4", "mkv", "avi", "mov")
        3 -> extension in setOf("csv", "json", "bag", "db", "pcd")
        4 -> extension in setOf("log", "txt")
        else -> true
    }
}
