package com.example.jetsoncontroller.ui.components.geo.shell

import com.example.jetsoncontroller.ui.components.geo.GeoIcons
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
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

internal data class ShellTab(val section: GeoTab, val label: String, val icon: ImageVector, val filled: (Color, Color) -> ImageVector)

internal val shellTabs = listOf(
    ShellTab(GeoTab.TASKS, "작업", GeoIcons.tabWork, GeoIcons::tabWorkFilled),
    ShellTab(GeoTab.DEVICES, "장치", GeoIcons.tabDevice, GeoIcons::tabDeviceFilled),
    ShellTab(GeoTab.TRANSFERS, "전송", GeoIcons.tabTransfer, GeoIcons::tabTransferFilled),
    ShellTab(GeoTab.SETTINGS, "설정", GeoIcons.tabSettings, GeoIcons::tabSettingsFilled)
)

@Composable
internal fun ShellTabItem(tab: ShellTab, selected: Boolean, enabled: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val colors = LocalGeoColors.current
    Column(
        modifier.heightIn(min = 64.dp).selectable(selected, enabled = enabled, role = Role.Tab, onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(if (selected) tab.filled(colors.navSelected, colors.nav) else tab.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (selected) Color.Unspecified else colors.navUnselected)
        Text(tab.label, style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.navSelected else colors.navUnselected)
    }
}

@Composable
fun GeoBottomBar(
    selected: GeoTab,
    onSelect: (GeoTab) -> Unit,
    modifier: Modifier = Modifier,
    enabledSections: Set<GeoTab> = GeoTab.entries.toSet()
) {
    Surface(color = LocalGeoColors.current.nav, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.selectableGroup().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
            shellTabs.forEach { tab ->
                ShellTabItem(tab, selected == tab.section, tab.section in enabledSections,
                    { onSelect(tab.section) }, Modifier.weight(1f))
            }
        }
    }
}
