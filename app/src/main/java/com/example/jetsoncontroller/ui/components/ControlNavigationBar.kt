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
import androidx.compose.material3.NavigationBarItemDefaults
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
    onSelect: (ControlSection) -> Unit,
    enabledSections: Set<ControlSection> = ControlSection.entries.toSet()
) {
    val items = listOf(
        Triple(ControlSection.OVERVIEW, Icons.Default.Home, "홈"),
        Triple(ControlSection.PIPELINES, Icons.AutoMirrored.Filled.PlaylistPlay, "작업"),
        Triple(ControlSection.DATA, Icons.Default.FolderOpen, "데이터"),
        Triple(ControlSection.SETTINGS, Icons.Default.Settings, "설정")
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (section, icon, label) ->
            NavigationBarItem(
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.accent,
                    selectedIconColor = com.example.jetsoncontroller.ui.theme.LocalCobaltColors.current.onAccent,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface),
                selected = selected == section,
                onClick = { onSelect(section) },
                enabled = section in enabledSections,
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) }
            )
        }
    }
}
