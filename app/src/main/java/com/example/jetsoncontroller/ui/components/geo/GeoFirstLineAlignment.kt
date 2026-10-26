package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.roundToInt

/** Pixel offset only: retain the measured row height and the existing column widths. */
internal fun firstTitleLineOffset(
    lineTop: Float,
    lineBottom: Float,
    titleHeight: Int,
    trailingHeight: Int,
    titleCentered: Boolean
): Int = ((lineTop + lineBottom) / 2f -
    (if (titleCentered) titleHeight else trailingHeight) / 2f).roundToInt()

private data class FirstTitleLine(val top: Float, val bottom: Float, val titleHeight: Int)

@Stable
internal class GeoFirstLineAlignment {
    private var firstLine by mutableStateOf<FirstTitleLine?>(null)

    fun onTextLayout(result: TextLayoutResult) {
        firstLine = FirstTitleLine(result.getLineTop(0), result.getLineBottom(0), result.size.height)
    }

    fun offset(trailingHeight: Int, titleCentered: Boolean): Int = firstLine?.let {
        firstTitleLineOffset(it.top, it.bottom, it.titleHeight, trailingHeight, titleCentered)
    } ?: 0
}

@Composable
internal fun rememberFirstLineAlignment(): GeoFirstLineAlignment = remember { GeoFirstLineAlignment() }

/** The title column is either top aligned or centered with this trailing cell in its Row. */
internal fun Modifier.alignToFirstTitleLine(
    alignment: GeoFirstLineAlignment,
    titleCentered: Boolean = false
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, alignment.offset(placeable.height, titleCentered))
    }
}
