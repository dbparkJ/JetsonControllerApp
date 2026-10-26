package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.jetsoncontroller.ui.theme.GeoShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class FigureValueLayout(val numberSp: Int, val stackedUnit: Boolean)

/** Very long numbers may use half the normal inset; the tile boundary is always preserved. */
internal fun figureValuePadding(tilePx: Int, defaultPaddingPx: Int, minimumNumberPx: Int): Int =
    if (minimumNumberPx > tilePx - defaultPaddingPx * 2) defaultPaddingPx / 2 else defaultPaddingPx

/** Try every permitted size before moving the unit below the number. Widths use actual glyphs. */
internal fun figureValueLayout(
    availablePx: Int,
    unitPx: Int,
    gapPx: Int,
    numberWidth: (Int) -> Int
): FigureValueLayout {
    val sizes = 28 downTo 20 step 2
    sizes.firstOrNull { numberWidth(it) + unitPx + (if (unitPx > 0) gapPx else 0) <= availablePx }
        ?.let { return FigureValueLayout(it, false) }
    return FigureValueLayout(sizes.firstOrNull { numberWidth(it) <= availablePx } ?: 20, true)
}

/** Formatting, validity, freshness and threshold evaluation belong to the screen model. */
data class GeoFigure(
    val label: String,
    val value: String?,
    val valid: Boolean = true,
    val stale: Boolean = false,
    val detail: String? = null,
    val exceededThreshold: String? = null,
    val unit: String = ""
)

data class GeoFigurePresentation(
    val value: String,
    val unknown: Boolean,
    val warning: Boolean,
    val detail: String?,
    val staleDetail: String?
)

fun figureGridColumns(availableWidthDp: Float, fontScale: Float, contained: Boolean = false): Int {
    val contentWidth = availableWidthDp - if (contained) 32f else 0f
    return when {
        fontScale >= 2f || contentWidth < 200f -> 1
        fontScale >= 1.5f || contentWidth < 312f -> 2
        else -> 4
    }
}

fun figurePresentation(figure: GeoFigure): GeoFigurePresentation {
    if (!figure.valid || figure.value.isNullOrBlank()) {
        return GeoFigurePresentation("미확인", unknown = true, warning = false, detail = null, staleDetail = null)
    }
    val warning = !figure.exceededThreshold.isNullOrBlank()
    return GeoFigurePresentation(
        value = figure.value, unknown = false, warning = warning,
        detail = if (warning) "기준 ${figure.exceededThreshold} 초과" else if (figure.stale) "마지막 값 기준" else figure.detail,
        staleDetail = if (warning && figure.stale) "마지막 값 기준" else null
    )
}

@Composable
fun GeoFigureGrid(figures: List<GeoFigure>, modifier: Modifier = Modifier, contained: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = figureGridColumns(maxWidth.value, LocalDensity.current.fontScale, contained)
        Column(Modifier.fillMaxWidth().then(if (contained) Modifier.padding(16.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            figures.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { figure -> FigureCell(figure, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FigureCell(figure: GeoFigure, modifier: Modifier) {
    val colors = LocalGeoColors.current
    val shown = figurePresentation(figure)
    val type = LocalGeoType.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val parts = Regex("^([+-]?[\\d.,]+)\\s*(.*)$").matchEntire(shown.value)
    val number = if (figure.unit.isNotEmpty()) shown.value else parts?.groupValues?.get(1) ?: shown.value
    val unit = if (shown.unknown) "" else figure.unit.ifEmpty { parts?.groupValues?.get(2).orEmpty() }
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).background(colors.sunken, GeoShape.control).padding(vertical = 8.dp)) {
            Text(figure.label, Modifier.padding(horizontal = 8.dp), color = colors.textSecondary, style = LocalGeoType.current.caption)
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 36.dp)) {
                val padding = figureValuePadding(constraints.maxWidth, with(density) { 8.dp.roundToPx() },
                    measurer.measure(AnnotatedString(number),
                        if (shown.unknown) type.caption else type.figure.copy(fontSize = 20.sp), softWrap = false).size.width)
                val layout = figureValueLayout((constraints.maxWidth - padding * 2).coerceAtLeast(0),
                    if (unit.isEmpty()) 0 else measurer.measure(AnnotatedString(unit), type.unit, softWrap = false).size.width,
                    with(density) { 2.dp.roundToPx() }) { size ->
                    measurer.measure(AnnotatedString(number),
                        if (shown.unknown) type.caption else type.figure.copy(fontSize = size.sp), softWrap = false).size.width
                }
                val numberStyle = if (shown.unknown) type.caption else type.figure.copy(fontSize = layout.numberSp.sp)
                val inset = with(density) { padding.toDp() }
                if (layout.stackedUnit) {
                    Column(Modifier.padding(horizontal = inset)) {
                        Text(number, color = if (shown.warning) colors.warning else colors.text, style = numberStyle)
                        if (unit.isNotEmpty()) Text(unit, color = colors.textSecondary, style = type.unit)
                    }
                } else Row(Modifier.padding(horizontal = inset), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    Text(number, Modifier.alignByBaseline(), color = if (shown.warning) colors.warning else colors.text,
                        style = numberStyle, maxLines = 1, softWrap = false)
                    if (unit.isNotEmpty()) Text(unit, Modifier.alignByBaseline(), color = colors.textSecondary,
                        style = type.unit, maxLines = 1, softWrap = false)
                }
            }
        }
        if (!shown.detail.isNullOrEmpty()) Text(shown.detail, Modifier.heightIn(min = 20.dp),
            color = if (shown.warning) colors.warning else colors.textSecondary,
            style = LocalGeoType.current.caption)
        shown.staleDetail?.let { Text(it, color = colors.textSecondary, style = LocalGeoType.current.caption) }
    }
}
