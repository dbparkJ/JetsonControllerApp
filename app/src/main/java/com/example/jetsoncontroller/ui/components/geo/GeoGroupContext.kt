package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Geometry belongs to the nearest zero-padding group, including mixed row types. */
internal class GeoGroupContext {
    val rowBottoms = mutableStateMapOf<Any, Float>()
    val navigation = mutableStateMapOf<Any, Boolean>()
    val actionWidths = mutableStateMapOf<Any, Float>()
}

internal val LocalGeoGroup = staticCompositionLocalOf<GeoGroupContext?> { null }
internal val LocalGeoGroupInset = staticCompositionLocalOf { false }
internal val LocalGeoInverseSurface = staticCompositionLocalOf { false }
internal val LocalGeoMediaStage = staticCompositionLocalOf { false }

@Composable
internal fun Modifier.geoGroupInset(): Modifier =
    if (LocalGeoGroupInset.current) padding(horizontal = 16.dp, vertical = 12.dp) else this

internal fun geoReserveChevron(navigable: Boolean, groupNavigation: Collection<Boolean>): Boolean =
    navigable || groupNavigation.any { it }

internal fun geoGroupLastRow(rowBottomPx: Float, lastBottomPx: Float): Boolean =
    rowBottomPx >= lastBottomPx - 1f

internal fun geoActionWidth(widths: Collection<Float>): Float = maxOf(88f, widths.maxOrNull() ?: 0f)

internal fun geoActionStacks(available: Float, action: Float, title: Float): Boolean =
    action + title + 48f > available

internal fun geoBreakableValue(value: String): String = buildString {
    value.forEach { char ->
        append(char)
        if (char in "/._-") append('\u200B')
    }
}

/** Keep a unit intact while allowing the ordinary space before it to wrap. */
internal fun geoBreakableStatus(value: String): String = value.split(' ').joinToString(" ") { token ->
    token.toList().joinToString("\u2060")
}
