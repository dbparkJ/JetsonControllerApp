package com.example.jetsoncontroller.ui.components.geo.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.geoSelectable
import com.example.jetsoncontroller.ui.theme.*

/** Tablet camera navigation floats above the image at the safe lower-left corner. */
@Composable
fun GeoFloatingMediaNavigation(selected: GeoTab, onSelect: (GeoTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.geoMediaShadow(GeoShape.pill).clip(GeoShape.pill)
        .background(GeoFixedColors.mediaPlate).selectableGroup().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shellTabs.forEach { tab ->
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            Box(Modifier.size(48.dp).clip(GeoShape.pill)
                .background(if (selected == tab.section) LocalGeoColors.current.primaryTint else androidx.compose.ui.graphics.Color.Transparent)
                .geoSelectable(selected == tab.section, shape = GeoShape.pill, interactionSource = source) { onSelect(tab.section) }
                .semantics { contentDescription = tab.label }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(tab.icon, null, Modifier.size(24.dp),
                    tint = if (pressed) LocalGeoColors.current.text else if (selected == tab.section) LocalGeoColors.current.primaryTintText else GeoFixedColors.mediaText)
            }
        }
    }
}
