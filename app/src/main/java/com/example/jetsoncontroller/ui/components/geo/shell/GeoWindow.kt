package com.example.jetsoncontroller.ui.components.geo.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.max
import com.example.jetsoncontroller.ui.theme.GeoSpace

enum class GeoWidthClass { COMPACT, MEDIUM, EXPANDED }

data class GeoWindow(
    val widthDp: Float,
    val heightDp: Float,
    val paneWidthDp: Float,
    val widthClass: GeoWidthClass,
    val compactHeight: Boolean,
    val rail: Boolean,
    val gutterDp: Float,
    val twoPane: Boolean
)

/** Width is measured after horizontal safe drawing and before navigation chrome. */
fun geoWindow(widthDp: Float, heightDp: Float, fontScale: Float, navigationVisible: Boolean = true): GeoWindow {
    val widthClass = when {
        widthDp < 600f -> GeoWidthClass.COMPACT
        widthDp < 840f -> GeoWidthClass.MEDIUM
        else -> GeoWidthClass.EXPANDED
    }
    val rail = navigationVisible && widthDp >= 600f
    val pane = (widthDp - if (rail) 80f else 0f).coerceAtLeast(0f)
    val gutter = paneGutter(pane)
    val scale = max(1f, fontScale)
    return GeoWindow(
        widthDp, heightDp, pane, widthClass, heightDp < 480f, rail, gutter,
        widthClass == GeoWidthClass.EXPANDED && heightDp >= 480f && fontScale < 1.5f &&
            pane >= 320f * scale + 1f + 400f * scale + 2f * gutter
    )
}

fun paneGutter(paneWidthDp: Float): Float = GeoSpace.gutterForPane(paneWidthDp).value

val LocalGeoWindow = staticCompositionLocalOf { geoWindow(412f, 882f, 1f) }

/** Screens opt into list/detail without treating two arbitrary columns as two panes. */
@Composable
fun GeoPaneLayout(
    modifier: Modifier = Modifier,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    val window = LocalGeoWindow.current
    val gutter = window.gutterDp.dp
    if (window.twoPane) {
        Row(modifier.fillMaxWidth().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            Box(Modifier.weight(4f)) { list() }
            Box(Modifier.weight(5f)) { detail() }
        }
    } else {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = gutter)) {
                list()
                detail()
            }
        }
    }
}
