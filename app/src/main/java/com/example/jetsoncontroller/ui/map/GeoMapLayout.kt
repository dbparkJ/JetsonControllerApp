package com.example.jetsoncontroller.ui.map

internal fun mapLegendColumns(widthDp: Float, fontScale: Float): Int =
    if (fontScale >= 1.3f || widthDp < 320f) 1 else 2

/** Include the shared drag handle and keep the summary within the expanded sheet bounds. */
internal fun mapSheetPeekHeight(viewportDp: Float, summaryDp: Float): Float =
    (summaryDp + 28f).coerceIn(0f, (viewportDp * .85f).coerceAtLeast(0f))
