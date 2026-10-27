package com.example.jetsoncontroller.ui.components.geo

internal fun geoTodayLabelStacks(widthDp: Float, fontScale: Float, labelWidthDp: Float, deadlineWidthDp: Float): Boolean =
    labelWidthDp + 8f + deadlineWidthDp > widthDp

/** Row geometry is relative to the edge of a zero-padding group surface. */
data class GeoRowAnatomy(
    val minimumHeightDp: Int,
    val leadingXDp: Int = 16,
    val leadingSizeDp: Int,
    val textXDp: Int,
    val dividerXDp: Int
)

fun geoRowAnatomy(twoLine: Boolean, photo: Boolean = false): GeoRowAnatomy =
    GeoRowAnatomy(if (twoLine) 72 else 56, leadingSizeDp = if (photo) 40 else 24,
        textXDp = if (photo) 68 else 56, dividerXDp = if (photo) 68 else 56)

/** Selection never changes chip typography, padding or measured width. */
fun geoFilterWidthPx(textWidthPx: Int, horizontalPaddingPx: Int, minimumPx: Int): Int =
    maxOf(minimumPx, textWidthPx.coerceAtLeast(0) + 2 * horizontalPaddingPx.coerceAtLeast(0))
