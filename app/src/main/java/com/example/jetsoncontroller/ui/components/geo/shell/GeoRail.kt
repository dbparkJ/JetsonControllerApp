package com.example.jetsoncontroller.ui.components.geo.shell

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab

@Composable
fun GeoRail(selected: GeoTab, onSelect: (GeoTab) -> Unit, modifier: Modifier = Modifier,
    attentionSections: Set<GeoTab> = emptySet()) {
    Surface(color = LocalGeoColors.current.canvas, modifier = modifier.width(80.dp).fillMaxHeight()) {
        Column(
            Modifier.selectableGroup().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shellTabs.forEach { tab ->
                ShellTabItem(tab, selected == tab.section, true,
                    { onSelect(tab.section) }, Modifier.fillMaxWidth(), tab.section in attentionSections, rail = true)
            }
        }
    }
}
