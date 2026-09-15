package com.example.jetsoncontroller.ui.storage

import androidx.compose.material3.MaterialTheme
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
            text = { Text("장치", style = MaterialTheme.typography.titleMedium) }
        )
        Tab(
            selected = selected == DataLocation.SERVER,
            onClick = onServerClick,
            text = { Text("서버", style = MaterialTheme.typography.titleMedium) }
        )
    }
}
