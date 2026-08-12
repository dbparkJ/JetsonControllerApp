package com.example.jetsoncontroller.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class ControlSection {
    OVERVIEW,
    DATA,
    PIPELINES,
    SENSORS,
    SETTINGS
}

@Composable
fun ControlNavigationBar(
    selected: ControlSection,
    onSelect: (ControlSection) -> Unit
) {
    val items = listOf(
        Triple(ControlSection.OVERVIEW, Icons.Default.Home, "홈"),
        Triple(ControlSection.DATA, Icons.Default.FolderOpen, "데이터"),
        Triple(ControlSection.PIPELINES, Icons.AutoMirrored.Filled.PlaylistPlay, "작업"),
        Triple(ControlSection.SENSORS, Icons.Default.Sensors, "센서"),
        Triple(ControlSection.SETTINGS, Icons.Default.Settings, "설정")
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (section, icon, label) ->
            NavigationBarItem(
                selected = selected == section,
                onClick = { onSelect(section) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
