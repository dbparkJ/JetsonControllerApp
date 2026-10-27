package com.example.jetsoncontroller.ui.components.geo.shell

import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.components.geo.GeoIcons
import com.example.jetsoncontroller.ui.components.geo.geoSelectable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoPalette
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.GeoPalette
import com.example.jetsoncontroller.ui.theme.GeoColors

internal data class ShellTab(val section: GeoTab, val label: String, val icon: ImageVector, val filled: (Color, Color) -> ImageVector)

internal val shellTabs = listOf(
    ShellTab(GeoTab.TASKS, "작업", GeoIcons.tabWork, GeoIcons::tabWorkFilled),
    ShellTab(GeoTab.DEVICES, "장치", GeoIcons.tabDevice, GeoIcons::tabDeviceFilled),
    ShellTab(GeoTab.TRANSFERS, "전송", GeoIcons.tabTransfer, GeoIcons::tabTransferFilled),
    ShellTab(GeoTab.SETTINGS, "설정", GeoIcons.tabSettings, GeoIcons::tabSettingsFilled)
)

internal fun geoTabSelectedShapeColor(palette: GeoPalette, colors: GeoColors = palette.colors): Color = colors.primaryTint

@Composable
internal fun ShellTabItem(tab: ShellTab, selected: Boolean, enabled: Boolean, onSelect: () -> Unit, modifier: Modifier,
    attention: Boolean = false, rail: Boolean = false) {
    val colors = LocalGeoColors.current
    val palette = LocalGeoPalette.current
    val selectedFill = geoTabSelectedShapeColor(palette, colors)
    val shape = GeoShape.pill
    Column(
        modifier.heightIn(min = 72.dp).semantics { if (attention) stateDescription = "확인할 알림 있음" }
            .geoSelectable(selected, enabled = enabled, onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(if (rail) 56.dp else 64.dp, 32.dp)
            .background(if (selected) selectedFill else Color.Transparent, shape)
            .then(if (selected && palette.highContrast) Modifier.border(1.dp, colors.navSelected, shape) else Modifier),
            contentAlignment = Alignment.Center) {
            Icon(tab.icon,
                contentDescription = null, modifier = Modifier.size(24.dp),
                tint = if (selected) colors.primary else colors.textSubtle)
            if (attention) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 8.dp)
                .size(6.dp).background(colors.attention, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.height(4.dp))
        Text(tab.label, style = if (selected) LocalGeoType.current.tabSelected else LocalGeoType.current.tab,
            color = if (selected) colors.primary else colors.textSubtle)
    }
}

@Composable
fun GeoBottomBar(
    selected: GeoTab,
    onSelect: (GeoTab) -> Unit,
    modifier: Modifier = Modifier,
    enabledSections: Set<GeoTab> = GeoTab.entries.toSet(),
    attentionSections: Set<GeoTab> = emptySet()
) {
    val colors = LocalGeoColors.current
    Surface(color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.drawWithContent {
            drawContent()
            if (colors.navBorder.alpha > 0f) {
                drawRect(colors.navBorder, size = Size(size.width, 1.dp.toPx()))
            }
        }.selectableGroup().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
            shellTabs.forEach { tab ->
                ShellTabItem(tab, selected == tab.section, tab.section in enabledSections,
                    { onSelect(tab.section) }, Modifier.weight(1f), tab.section in attentionSections)
            }
        }
    }
}
