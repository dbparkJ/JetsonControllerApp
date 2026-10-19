package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.model.Tone

/** All facts, the first attention item and visibility are supplied by the screen model. */
data class GeoDeviceStripState(
    val deviceName: String,
    val connectionWord: String,
    val connectionTone: Tone,
    val facts: List<String>,
    val freshness: String,
    val firstAttention: String? = null,
    val hiddenByFocus: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeoDeviceStrip(state: GeoDeviceStripState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (state.hiddenByFocus) return
    val colors = LocalGeoColors.current
    Row(modifier.fillMaxWidth().contentTarget(onClick = onClick).heightIn(min = 72.dp)
        .padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Image(painterResource(R.drawable.geo_device), contentDescription = null, modifier = Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.deviceName, color = colors.text, style = LocalGeoType.current.sectionTitle)
                GeoBadge(state.connectionWord, state.connectionTone)
            }
            if (state.firstAttention == null) {
                Text((state.facts + state.freshness).joinToString(" · "), color = colors.textSecondary, style = LocalGeoType.current.caption)
            } else {
                if (state.facts.isNotEmpty()) Text(state.facts.joinToString(" · "), color = colors.textSecondary, style = LocalGeoType.current.caption)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceStripIcon(warning = true)
                    Text(state.firstAttention, color = colors.tone(Tone.WARNING), style = LocalGeoType.current.caption)
                }
            }
        }
        DeviceStripIcon(warning = false)
    }
}

@Composable
private fun DeviceStripIcon(warning: Boolean) {
    val c = LocalGeoColors.current
    androidx.compose.material3.Icon(if (warning) GeoIcons.warning else GeoIcons.chevronRight, null,
        Modifier.size(if (warning) 16.dp else 20.dp), tint = if (warning) c.warning else c.textSubtle)
}
