package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.example.jetsoncontroller.ui.theme.GeoShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
    val parts = Regex("^([+-]?[\\d.,]+)\\s*(.*)$").matchEntire(shown.value)
    val number = if (figure.unit.isNotEmpty()) shown.value else parts?.groupValues?.get(1) ?: shown.value
    val unit = if (shown.unknown) "" else figure.unit.ifEmpty { parts?.groupValues?.get(2).orEmpty() }
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).background(colors.sunken, GeoShape.control).padding(8.dp)) {
            Text(figure.label, color = colors.textSecondary, style = LocalGeoType.current.caption)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.heightIn(min = 36.dp).wrapContentWidth(Alignment.Start, unbounded = true),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                Text(number, modifier = Modifier.alignByBaseline(), color = if (shown.warning) colors.warning else colors.text,
                    style = if (shown.unknown) type.caption else type.figure,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                if (unit.isNotEmpty()) Text(unit, Modifier.alignByBaseline(), color = colors.textSecondary,
                    style = type.unit, maxLines = 1, softWrap = false)
            }
        }
        if (!shown.detail.isNullOrEmpty()) Text(shown.detail, Modifier.heightIn(min = 20.dp),
            color = if (shown.warning) colors.warning else colors.textSecondary,
            style = LocalGeoType.current.caption)
        shown.staleDetail?.let { Text(it, color = colors.textSecondary, style = LocalGeoType.current.caption) }
    }
}
