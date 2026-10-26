package com.example.jetsoncontroller.ui.components.geo.shell

import kotlin.math.max
import kotlin.math.min

enum class GeoWidthClass { COMPACT, MEDIUM, EXPANDED }

data class GeoWindow(
    val widthDp: Float,
    val heightDp: Float,
    val paneWidthDp: Float,
    val widthClass: GeoWidthClass,
    val compactHeight: Boolean,
    val rail: Boolean,
    val gutterDp: Float,
    val twoPane: Boolean,
    val fontScale: Float = 1f,
    val landscapeLayout: Boolean = false
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
    val twoPane = widthClass == GeoWidthClass.EXPANDED && heightDp >= 480f && fontScale < 1.5f &&
        pane >= 320f * scale + 24f + 400f * scale + 2f * gutter
    return GeoWindow(
        widthDp, heightDp, pane, widthClass, heightDp < 480f, rail, gutter,
        twoPane, fontScale,
        geoLandscapeLayout(widthDp, heightDp, twoPane)
    )
}

/** Safe window dimensions, not device orientation; includes the exhibition tablet's 800dp height. */
fun geoLandscapeLayout(widthDp: Float, heightDp: Float, twoPane: Boolean): Boolean =
    twoPane && widthDp > heightDp && heightDp in 480f..840f

/** Split a device detail only when both internal columns have room for full-sized rows. */
fun geoDeviceDetailColumns(contentWidthDp: Float, fontScale: Float, landscape: Boolean): Int =
    if (landscape && contentWidthDp >= 560f * max(1f, fontScale) + 24f) 2 else 1

@Suppress("UNUSED_PARAMETER")
fun paneGutter(paneWidthDp: Float): Float = 24f


enum class GeoPanePolicy { LIST_DETAIL, WORK_HOME, ACTIVE_RUN }
enum class GeoPaneSelection { LIST, DETAIL, BOTH }

data class GeoPaneWidths(val listDp: Float, val detailDp: Float)

/** The same minimum-width check gates every screen; small fonts never lower it. */
fun geoPaneWidths(window: GeoWindow, policy: GeoPanePolicy = GeoPanePolicy.LIST_DETAIL): GeoPaneWidths? {
    if (!window.twoPane) return null
    val scale = max(1f, window.fontScale)
    val available = min(window.paneWidthDp, 1560f) - 48f - 24f
    val preferred = when (policy) {
        GeoPanePolicy.LIST_DETAIL -> 400f
        GeoPanePolicy.WORK_HOME -> 520f
        GeoPanePolicy.ACTIVE_RUN -> 360f
    }
    val upper = if (policy == GeoPanePolicy.WORK_HOME) Float.MAX_VALUE else 440f * scale
    val list = min(min(max(320f * scale, preferred), upper), available - 400f * scale)
    return GeoPaneWidths(list, available - list)
}

/** Selection belongs to navigation/state, never to the current size or orientation. */
fun geoVisiblePanes(twoPane: Boolean, selection: GeoPaneSelection): Set<GeoPaneSelection> =
    if (twoPane || selection == GeoPaneSelection.BOTH) setOf(GeoPaneSelection.LIST, GeoPaneSelection.DETAIL)
    else setOf(selection)


/** Tablet card grids use measured content widths, while phone policies stay in their R7 components. */
object GeoTabletRules {
    fun homeGrid(contentWidthDp: Float, fontScale: Float): Boolean = contentWidthDp >= 624f && fontScale <= 1f
    fun quickColumns(widthDp: Float, fontScale: Float): Int = when {
        fontScale >= 2f || widthDp < 104f -> 1
        fontScale >= 1.3f || widthDp < 400f -> 2
        else -> 5
    }
    fun themeColumns(widthDp: Float, fontScale: Float): Int = when {
        fontScale >= 2f || widthDp < 320f -> 1
        fontScale > 1f || widthDp < 560f -> 2
        else -> 3
    }
}
