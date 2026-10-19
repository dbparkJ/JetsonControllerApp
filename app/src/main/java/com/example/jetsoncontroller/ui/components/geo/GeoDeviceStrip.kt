package com.example.jetsoncontroller.ui.components.geo

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
import com.example.jetsoncontroller.ui.components.StatusTone

/** All facts, the first attention item and visibility are supplied by the screen model. */
data class GeoDeviceStripState(
    val deviceName: String,
    val connectionWord: String,
    val connectionTone: StatusTone,
    val facts: List<String>,
    val freshness: String,
    val firstAttention: String? = null,
    val hiddenByFocus: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeoDeviceStrip(state: GeoDeviceStripState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (state.hiddenByFocus) return
    val colors = contentColors
    Row(modifier.fillMaxWidth().contentTarget(onClick = onClick).heightIn(min = 72.dp)
        .padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Image(painterResource(R.drawable.geo_device), contentDescription = null, modifier = Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.deviceName, color = colors.text, style = ContentType.section)
                GeoBadge(state.connectionWord, state.connectionTone)
            }
            if (state.firstAttention == null) {
                Text((state.facts + state.freshness).joinToString(" · "), color = colors.secondary, style = ContentType.caption)
            } else {
                if (state.facts.isNotEmpty()) Text(state.facts.joinToString(" · "), color = colors.secondary, style = ContentType.caption)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceStripIcon(warning = true)
                    Text(state.firstAttention, color = colors.tone(StatusTone.WARNING), style = ContentType.caption)
                }
            }
        }
        DeviceStripIcon(warning = false)
    }
}

// TODO(3-B merge): replace with GeoIcons.warning / chevronRight.
@Composable
private fun DeviceStripIcon(warning: Boolean) {
    val colors = contentColors
    val color = if (warning) colors.tone(StatusTone.WARNING) else colors.subtle
    Canvas(Modifier.size(if (warning) 16.dp else 20.dp)) {
        val stroke = 1.75.dp.toPx()
        if (warning) {
            drawCircle(color, style = Stroke(stroke))
            drawLine(color, Offset(size.width / 2, size.height * .25f), Offset(size.width / 2, size.height * .55f), stroke, StrokeCap.Round)
            drawCircle(color, 1.dp.toPx(), Offset(size.width / 2, size.height * .75f))
        } else {
            drawLine(color, Offset(size.width * .4f, size.height * .25f), Offset(size.width * .65f, size.height * .5f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .4f, size.height * .75f), stroke, StrokeCap.Round)
        }
    }
}
