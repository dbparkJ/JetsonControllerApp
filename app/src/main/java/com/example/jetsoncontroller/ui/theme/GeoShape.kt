package com.example.jetsoncontroller.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** R8 radii; navigation indicators and filters have explicit pill roles. */
object GeoShape {
    val sm = 8.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 20.dp
    val xxl = 24.dp
    val pillRadius = 999.dp

    val tag = RoundedCornerShape(sm)
    val badge = tag
    val iconTile = RoundedCornerShape(md)
    val segmentedTrack = RoundedCornerShape(md)
    val segmentedThumb = RoundedCornerShape(sm)
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

/** One R7 shadow. Compatibility entry points all return the same geometry. */
object GeoElevation {
    val none = 0.dp
    val small = 16.dp
    val large = 16.dp
    const val darkOutlineAlpha = 0f
    fun shadow(palette: GeoPalette) = GeoShadow(
        outline = Color.Transparent,
        color = palette.colors.text.copy(alpha = 0.06f),
        offsetY = 4.dp, blur = 16.dp, outlineWidth = 0.dp
    )
    fun shadowSmall(palette: GeoPalette) = shadow(palette)
    fun shadowLarge(palette: GeoPalette) = shadow(palette)
    fun shadowSwitch(palette: GeoPalette) = shadow(palette)
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
