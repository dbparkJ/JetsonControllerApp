package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
        .background(colors.surface)) {
        GeoRow(state.deviceName, onClick,
            meta = (state.facts + if (state.firstAttention == null) listOf(state.freshness) else emptyList())
                .filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotEmpty),
            leading = { Image(painterResource(R.drawable.geo_device), null, Modifier.size(40.dp)) },
            badge = { GeoBadge(state.connectionWord, state.connectionTone) }, divider = false,
            leadingIsPhoto = true,
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
    GeoRow(title, {}, modifier, leading = leading, badge = { GeoBadge(status, tone) },
        divider = false, navigable = false)
}
