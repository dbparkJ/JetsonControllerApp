package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
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
import com.example.jetsoncontroller.ui.model.Tone

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
    val colors = LocalGeoColors.current
    val shown = figurePresentation(figure)
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (shown.warning) androidx.compose.material3.Icon(GeoIcons.warning, null, Modifier.size(16.dp), tint = colors.warning)
            Text(figure.label, color = colors.textSecondary, style = LocalGeoType.current.caption)
        }
        Spacer(Modifier.height(14.dp))
        Text(shown.value, Modifier.heightIn(min = 32.dp),
            color = if (shown.unknown) colors.tone(Tone.UNKNOWN) else colors.text,
            style = if (shown.unknown) LocalGeoType.current.title else LocalGeoType.current.figure)
        Text(shown.detail.orEmpty(), Modifier.heightIn(min = 20.dp),
            color = if (shown.warning) colors.tone(Tone.WARNING) else colors.textSecondary,
            style = LocalGeoType.current.caption)
        shown.staleDetail?.let { Text(it, color = colors.textSecondary, style = LocalGeoType.current.caption) }
    }
}
