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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.jetsoncontroller.ui.model.Tone

enum class GeoBadgeVariant { INLINE, TAG, SOFT, ON_MAP, ON_DARK }

internal val LocalGeoRowStatus = staticCompositionLocalOf { false }

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
    variant: GeoBadgeVariant = GeoBadgeVariant.SOFT,
    highContrast: Boolean = LocalGeoPalette.current.highContrast,
    minWidth: androidx.compose.ui.unit.Dp = 64.dp
) {
    val inline = LocalGeoRowStatus.current
    val style = if (inline) LocalGeoType.current.inlineStatus else LocalGeoType.current.badge
    val report = LocalGeoStatusWidthReporter.current
    if (inline && report != null) {
        val measured = rememberTextMeasurer().measure(AnnotatedString(word), style, softWrap = false).size.width
        val naturalWidth = with(LocalDensity.current) { measured.toDp() } + 10.dp
        SideEffect { report(naturalWidth) }
    }
    val colors = geoBadgeColors(LocalGeoColors.current, tone, if (inline) GeoBadgeVariant.INLINE else variant)
    val shape = GeoShape.badge
    Row(modifier
        .background(colors.fill, shape)
        .then(if (highContrast && !inline) Modifier.border(1.dp, colors.ink, shape) else Modifier)
        .clearAndSetSemantics { stateDescription = word }
        .then(if (inline) Modifier.heightIn(min = 24.dp) else Modifier.widthIn(min = minWidth).heightIn(min = 24.dp))
        .padding(horizontal = if (inline) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (inline) Alignment.End else Alignment.CenterHorizontally)) {
        Box(Modifier.size(6.dp).background(colors.ink, CircleShape))
        Text(word, color = colors.ink,
            style = style, maxLines = if (inline) 1 else Int.MAX_VALUE, softWrap = !inline)
    }
}
