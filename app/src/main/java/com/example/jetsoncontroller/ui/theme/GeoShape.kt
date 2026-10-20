package com.example.jetsoncontroller.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** R4 radii with the existing R3 strokes and focus geometry. */
object GeoShape {
    val sm = 6.dp
    val md = 8.dp
    val lg = 10.dp
    val xl = 14.dp
    val xxl = 14.dp
    val pillRadius = 999.dp

    val tag = RoundedCornerShape(4.dp)
    val segmentedTrack = RoundedCornerShape(8.dp)
    val segmentedThumb = RoundedCornerShape(4.dp)
    val checkbox = RoundedCornerShape(sm)
    val control = RoundedCornerShape(md)
    val card = RoundedCornerShape(lg)
    val dialog = RoundedCornerShape(xl)
    val sheet = RoundedCornerShape(xxl)
    val sheetTop = RoundedCornerShape(topStart = xxl, topEnd = xxl)
    val pill = RoundedCornerShape(pillRadius)
    val circle = CircleShape
    val progress = RoundedCornerShape(2.dp)

    val borderWidth = 1.dp
    val strongStroke = 2.dp
    val focusRing = 2.dp
    val focusOffset = 2.dp
    val focusHalo = 4.dp
}

/** Shadows supplement borders; body content has no elevation or tonal tint. */
object GeoElevation {
    val none = 0.dp
    val small = 2.dp
    val large = 16.dp
    const val darkOutlineAlpha = 0.10f

    fun shadowSmall(palette: GeoPalette): GeoShadow = if (palette.isDark) {
        GeoShadow(Color.White.copy(alpha = 0.07f))
    } else {
        GeoShadow(navy.copy(alpha = 0.06f), navy.copy(alpha = 0.06f), 1.dp, 2.dp)
    }

    fun shadowLarge(palette: GeoPalette): GeoShadow = if (palette.isDark) {
        GeoShadow(Color.White.copy(alpha = darkOutlineAlpha), Color.Black.copy(alpha = 0.45f), 16.dp, 40.dp)
    } else {
        GeoShadow(navy.copy(alpha = 0.06f), navy.copy(alpha = 0.18f), 20.dp, 48.dp)
    }

    fun shadowSwitch(palette: GeoPalette) = GeoShadow(
        outline = Color.Transparent,
        color = if (palette.isDark) Color.Black.copy(alpha = 0.5f) else navy.copy(alpha = 0.25f),
        offsetY = 1.dp, blur = 2.dp
    )

    private val navy = Color(0xFF121A3D)
}

/** KIT shadow geometry, for components that draw a shadow beyond platform elevation. */
@Immutable
data class GeoShadow(
    val outline: Color,
    val color: Color = Color.Transparent,
    val offsetY: Dp = 0.dp,
    val blur: Dp = 0.dp,
    val outlineWidth: Dp = 1.dp
)
