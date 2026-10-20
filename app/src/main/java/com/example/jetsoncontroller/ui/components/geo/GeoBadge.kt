package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoPalette
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.GeoColors
import com.example.jetsoncontroller.ui.theme.GeoFixedColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone

enum class GeoBadgeVariant { INLINE, TAG, SOFT, ON_MAP, ON_DARK }

internal data class GeoBadgeColors(val ink: Color, val fill: Color)

internal fun geoBadgeColors(colors: GeoColors, tone: Tone, variant: GeoBadgeVariant): GeoBadgeColors = when (variant) {
    GeoBadgeVariant.INLINE -> GeoBadgeColors(colors.tone(tone), Color.Transparent)
    GeoBadgeVariant.TAG -> GeoBadgeColors(colors.primary, colors.primaryTint)
    GeoBadgeVariant.SOFT -> GeoBadgeColors(colors.tone(tone), colors.soft(tone))
    GeoBadgeVariant.ON_MAP -> GeoBadgeColors(colors.tone(tone), colors.mapPlate)
    GeoBadgeVariant.ON_DARK -> GeoBadgeColors(mediaTone(tone), GeoFixedColors.mediaBadge)
}

/** INLINE is a status; TAG identifies a unit. Opaque map and media plates stay legible. */
@Composable
fun GeoBadge(
    word: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    variant: GeoBadgeVariant = GeoBadgeVariant.INLINE,
    highContrast: Boolean = LocalGeoPalette.current.highContrast
) {
    val colors = geoBadgeColors(LocalGeoColors.current, tone, variant)
    val inline = variant == GeoBadgeVariant.INLINE
    val tag = variant == GeoBadgeVariant.TAG
    val shape = if (tag) GeoShape.tag else RoundedCornerShape(50)
    Row(modifier
        .then(if (variant == GeoBadgeVariant.ON_MAP) Modifier.shadow(1.dp, shape) else Modifier)
        .background(colors.fill, shape)
        .then(if (highContrast && !inline) Modifier.border(1.dp, colors.ink, shape) else Modifier)
        .clearAndSetSemantics { stateDescription = word }
        .heightIn(min = if (inline) 20.dp else 24.dp)
        .padding(horizontal = if (inline) 0.dp else 8.dp, vertical = if (inline) 0.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!tag) Box(Modifier.size(if (inline) 8.dp else 6.dp).background(colors.ink, CircleShape))
        Text(word, color = colors.ink, style = when {
            inline -> LocalGeoType.current.inlineStatus
            tag -> LocalGeoType.current.tag
            else -> LocalGeoType.current.badge
        })
    }
}
