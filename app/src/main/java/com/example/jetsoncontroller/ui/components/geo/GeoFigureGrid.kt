package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.jetsoncontroller.ui.theme.GeoShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class FigureValueLayout(val numberSp: Int, val stackedUnit: Boolean)

internal fun figureValueLayout(availablePx: Int, unitPx: Int, gapPx: Int, numberWidth: (Int) -> Int): FigureValueLayout =
    figureValueLayout(availablePx, unitPx, gapPx, 28, numberWidth)

/** Very long numbers may use half the normal inset; the tile boundary is always preserved. */
internal fun figureValuePadding(tilePx: Int, defaultPaddingPx: Int, minimumNumberPx: Int): Int =
    if (minimumNumberPx > tilePx - defaultPaddingPx * 2) defaultPaddingPx / 2 else defaultPaddingPx

/** Try every permitted size before moving the unit below the number. Widths use actual glyphs. */
internal fun figureValueLayout(
    availablePx: Int,
    unitPx: Int,
    gapPx: Int,
    preferredSp: Int,
    numberWidth: (Int) -> Int
): FigureValueLayout {
    val sizes = if (preferredSp <= 22) listOf(22) else listOf(28, 26)
    sizes.firstOrNull { numberWidth(it) + unitPx + (if (unitPx > 0) gapPx else 0) <= availablePx }
        ?.let { return FigureValueLayout(it, false) }
    return FigureValueLayout(sizes.firstOrNull { numberWidth(it) <= availablePx } ?: sizes.last(), true)
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

fun figureGridColumns(availableWidthDp: Float, fontScale: Float, contained: Boolean = false, tablet: Boolean = false): Int {
    val contentWidth = availableWidthDp - if (contained) 32f else 0f
    return when {
        fontScale >= 2f || contentWidth < 200f -> 1
        fontScale >= 1.3f || contentWidth < 312f -> 2
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
    val colors = LocalGeoColors.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = figureGridColumns(maxWidth.value, LocalDensity.current.fontScale, contained,
            tablet = com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow.current.widthDp >= 600f)
        Column(Modifier.fillMaxWidth().clip(GeoShape.card).background(colors.surface)
            .then(if (contained) Modifier.padding(16.dp) else Modifier)) {
            figures.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth().drawBehind {
                    if (rowIndex > 0) drawLine(colors.border, Offset.Zero, Offset(size.width, 0f), 1f)
                    (1 until row.size).forEach { index ->
                        val x = size.width * index / columns
                        drawLine(colors.border, Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                }) {
                    row.forEachIndexed { index, figure -> FigureCell(figure, Modifier.weight(1f)
                        .padding(horizontal = if (index == 0) 0.dp else 12.dp, vertical = 8.dp)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Four neutral metric cells with 1px dividers; values reflow at enlarged font scales. */
@Composable
fun GeoMetricStrip(figures: List<GeoFigure>, modifier: Modifier = Modifier) {
    require(figures.size == 4) { "A metric strip requires four figures" }
    GeoFigureGrid(figures, modifier)
}

@Composable
private fun FigureCell(figure: GeoFigure, modifier: Modifier) {
    val colors = LocalGeoColors.current
    val shown = figurePresentation(figure)
    val type = LocalGeoType.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val preferredSp = if (com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow.current.widthDp >= 600f) 28 else 22
    val figureStyle = if (preferredSp == 22) type.phoneFigure else type.tabletFigure
    val parts = Regex("^([+-]?[\\d.,]+)\\s*(.*)$").matchEntire(shown.value)
    val number = if (figure.unit.isNotEmpty()) shown.value else parts?.groupValues?.get(1) ?: shown.value
    val unit = if (shown.unknown) "" else figure.unit.ifEmpty { parts?.groupValues?.get(2).orEmpty() }
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(figure.label, color = colors.textSecondary, style = LocalGeoType.current.caption)
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = if (preferredSp == 22) 28.dp else 36.dp)) {
                val padding = figureValuePadding(constraints.maxWidth, with(density) { 0.dp.roundToPx() },
                    measurer.measure(AnnotatedString(number),
                        if (shown.unknown) type.caption else figureStyle, softWrap = false).size.width)
                val layout = figureValueLayout((constraints.maxWidth - padding * 2).coerceAtLeast(0),
                    if (unit.isEmpty()) 0 else measurer.measure(AnnotatedString(unit), type.unit, softWrap = false).size.width,
                    with(density) { 2.dp.roundToPx() }, preferredSp) { size ->
                    measurer.measure(AnnotatedString(number),
                        if (shown.unknown) type.caption else figureStyle.copy(fontSize = size.sp), softWrap = false).size.width
                }
                val numberStyle = if (shown.unknown) type.caption else figureStyle.copy(fontSize = layout.numberSp.sp,
                    lineHeight = if (layout.numberSp == 22) 28.sp else if (layout.numberSp == 26) 32.sp else 36.sp)
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
