package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.StatusTone

/** Formatting, validity, freshness and threshold evaluation belong to the screen model. */
data class GeoFigure(
    val label: String,
    val value: String?,
    val valid: Boolean = true,
    val stale: Boolean = false,
    val detail: String? = null,
    val exceededThreshold: String? = null
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
    return if (fontScale >= 1.5f || (contentWidth - 16f) / 2f < 132f) 1 else 2
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
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            figures.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { figure -> FigureCell(figure, Modifier.weight(1f)) }
                    if (columns == 2 && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FigureCell(figure: GeoFigure, modifier: Modifier) {
    val colors = contentColors
    val shown = figurePresentation(figure)
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (shown.warning) FigureWarningIcon()
            Text(figure.label, color = colors.secondary, style = ContentType.caption)
        }
        Spacer(Modifier.height(14.dp))
        Text(shown.value, Modifier.heightIn(min = 32.dp),
            color = if (shown.unknown) colors.tone(StatusTone.UNKNOWN) else colors.text,
            style = if (shown.unknown) ContentType.title else ContentType.heading)
        Text(shown.detail.orEmpty(), Modifier.heightIn(min = 20.dp),
            color = if (shown.warning) colors.tone(StatusTone.WARNING) else colors.secondary,
            style = ContentType.caption)
        shown.staleDetail?.let { Text(it, color = colors.secondary, style = ContentType.caption) }
    }
}

// TODO(3-B merge): replace with GeoIcons.warning.
@Composable
private fun FigureWarningIcon() {
    val color = contentColors.tone(StatusTone.WARNING)
    Canvas(Modifier.size(16.dp)) {
        drawCircle(color, style = Stroke(1.75.dp.toPx()))
        drawLine(color, Offset(size.width / 2, size.height * .25f),
            Offset(size.width / 2, size.height * .55f), 1.75.dp.toPx(), StrokeCap.Round)
        drawCircle(color, 1.dp.toPx(), Offset(size.width / 2, size.height * .75f))
    }
}
