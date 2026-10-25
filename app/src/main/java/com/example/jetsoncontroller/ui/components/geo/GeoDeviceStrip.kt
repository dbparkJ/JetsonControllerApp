package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.geoCardShadow

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

@Composable
fun GeoDeviceStrip(state: GeoDeviceStripState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (state.hiddenByFocus) return
    val colors = LocalGeoColors.current
    val attentionContent: (@Composable ColumnScope.() -> Unit)? = if (state.firstAttention == null) null else {
        {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Icon(GeoIcons.warning, null, Modifier.size(16.dp), tint = colors.warning)
                Text(state.firstAttention, color = colors.warning, style = LocalGeoType.current.supporting)
            }
        }
    }
    GeoRowList(GeoRowTrailing.Status, modifier.fillMaxWidth().geoCardShadow().clip(GeoShape.card)
        .background(colors.surface).padding(16.dp)) {
        GeoRow(state.deviceName, onClick,
            meta = (state.facts + if (state.firstAttention == null) listOf(state.freshness) else emptyList())
                .filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotEmpty),
            leading = { Image(painterResource(R.drawable.geo_device), null, Modifier.size(40.dp)) },
            badge = { GeoBadge(state.connectionWord, state.connectionTone) }, divider = false,
            extra = attentionContent)
    }
}

/** Device facts without a destination keep the shared row grid and have no click affordance. */
@Composable
fun GeoDeviceStatusRow(
    title: String,
    status: String,
    tone: Tone,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val density = LocalDensity.current
    val firstLine = with(density) { type.titleSm.lineHeight.toDp() }
    val statusCell: @Composable () -> Unit = {
        Box(Modifier.width(72.dp).heightIn(min = firstLine), contentAlignment = Alignment.TopEnd) {
            GeoBadge(status, tone)
        }
    }
    CompositionLocalProvider(LocalGeoRowStatus provides true) {
        BoxWithConstraints(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            val stacked = density.fontScale >= 1.5f || maxWidth < 280.dp
            Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.TopCenter) { leading() }
                    Text(title, Modifier.weight(1f), style = type.titleSm, color = colors.text)
                    if (!stacked) statusCell()
                    Spacer(Modifier.width(24.dp))
                }
                if (stacked) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.width(40.dp))
                    Spacer(Modifier.weight(1f))
                    statusCell()
                    Spacer(Modifier.width(24.dp))
                }
            }
        }
    }
}
