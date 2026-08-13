package com.example.jetsoncontroller.ui.storage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

internal enum class DataLocation {
    DEVICE,
    SERVER
}

@Composable
internal fun DataLocationTabs(
    selected: DataLocation,
    onDeviceClick: () -> Unit,
    onServerClick: () -> Unit
) {
    TabRow(selectedTabIndex = if (selected == DataLocation.DEVICE) 0 else 1) {
        Tab(
            selected = selected == DataLocation.DEVICE,
            onClick = onDeviceClick,
            text = { Text("Jetson") },
            icon = { Icon(Icons.Default.Storage, contentDescription = null) }
        )
        Tab(
            selected = selected == DataLocation.SERVER,
            onClick = onServerClick,
            text = { Text("서버") },
            icon = { Icon(Icons.Default.Cloud, contentDescription = null) }
        )
    }
}
