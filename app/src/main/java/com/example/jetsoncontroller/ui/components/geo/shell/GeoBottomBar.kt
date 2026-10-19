package com.example.jetsoncontroller.ui.components.geo.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

/** Adapter to the committed palette. No phase-3-A role or icon is required. */
@Immutable
internal data class ShellColors(
    val canvas: Color, val surface: Color, val topBar: Color, val nav: Color,
    val text: Color, val selected: Color, val unselected: Color, val attention: Color
)

@Composable
internal fun shellColors(): ShellColors {
    val colors = LocalGeoColors.current
    return ShellColors(colors.canvas, colors.surface, colors.surface, colors.surface,
        colors.ink, colors.primary, colors.inkSubtle, colors.danger)
}

internal data class ShellTab(val section: ControlSection, val label: String, val icon: ImageVector)

internal val shellTabs = listOf(
    ShellTab(ControlSection.TASKS, "작업", Icons.Default.PlayCircle),
    ShellTab(ControlSection.DEVICES, "장치", Icons.Default.Devices),
    ShellTab(ControlSection.TRANSFERS, "전송", Icons.Default.Upload),
    ShellTab(ControlSection.SETTINGS, "설정", Icons.Default.Settings)
)

@Composable
internal fun ShellTabItem(tab: ShellTab, selected: Boolean, enabled: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val colors = shellColors()
    Column(
        modifier.heightIn(min = 64.dp).selectable(selected, enabled = enabled, role = Role.Tab, onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (selected) colors.selected else colors.unselected)
        Text(tab.label, style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.selected else colors.unselected)
    }
}

@Composable
fun GeoBottomBar(
    selected: ControlSection,
    onSelect: (ControlSection) -> Unit,
    modifier: Modifier = Modifier,
    enabledSections: Set<ControlSection> = ControlSection.entries.toSet()
) {
    Surface(color = shellColors().nav, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.selectableGroup().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
            shellTabs.forEach { tab ->
                ShellTabItem(tab, selected.tab() == tab.section, tab.section in enabledSections,
                    { onSelect(tab.section) }, Modifier.weight(1f))
            }
        }
    }
}
