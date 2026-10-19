package com.example.jetsoncontroller.ui.storage

import androidx.compose.runtime.Composable
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab

/** Compatibility entry for the device browser. Navigation chrome belongs to the app shell. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun DataHubScreen(
    state: DeviceStorageUiState,
    serverUploadEnabled: Boolean,
    unavailableReason: String,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    onDirectoryClick: (RemoteFileEntry) -> Unit,
    onFileClick: (RemoteFileEntry) -> Unit,
    onDeleteClick: (RemoteFileEntry) -> Unit,
    onHistory: () -> Unit,
    onTransfer: (String, String) -> Unit,
    onSection: (GeoTab) -> Unit,
    onServerData: () -> Unit = {},
    onTrash: (() -> Unit)? = null,
    onDismissMessage: (String) -> Unit = {},
    onUndoDelete: () -> Unit = {},
    ongoingRun: @Composable () -> Unit = {},
    onReconnect: () -> Unit = onDevices
) = FileBrowser(state, "device", onNavigateBack, onNavigateBack, onRefresh,
    onDirectoryClick, onFileClick, onDeleteClick, onTrash, onDevices,
    onDismissMessage, onUndoDelete, ongoingRun = ongoingRun, onReconnect = onReconnect)
