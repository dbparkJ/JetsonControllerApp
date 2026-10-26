package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.*

/** Shared surfaces for the work, preparation and result screens. */
@Composable
internal fun WorkSurfaceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().geoCardShadow().clip(GeoShape.card)
        .background(LocalGeoColors.current.surface).padding(contentPadding), content = content)
}

/** A work card without an action, used for the focused clock and quality summary. */
@Composable
internal fun WorkSummaryCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalGeoColors.current
    val chosen = geoWorkCardColors(LocalGeoPalette.current)
    Column(modifier.fillMaxWidth().geoCardShadow().background(chosen.background, GeoShape.card)
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompositionLocalProvider(LocalGeoColors provides colors.copy(
            text = chosen.content, textSecondary = chosen.content
        )) { content() }
    }
}

/** Result records have two large surface tiles per row rather than the device's four small tiles. */
@Composable
internal fun WorkFigureCards(figures: List<GeoFigure>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (LocalDensity.current.fontScale >= 2f || maxWidth < 280.dp) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            figures.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { figure ->
                        WorkSurfaceCard(Modifier.weight(1f)) {
                            val shown = figurePresentation(figure)
                            val colors = LocalGeoColors.current
                            Column(Modifier.semantics(mergeDescendants = true) {},
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(figure.label, style = LocalGeoType.current.supporting, color = colors.textSecondary)
                                WorkFigureValue(shown.value, shown.unknown, shown.warning)
                                shown.detail?.let { Text(it, style = LocalGeoType.current.caption,
                                    color = if (shown.warning) colors.warning else colors.textSecondary) }
                                shown.staleDetail?.let { Text(it, style = LocalGeoType.current.caption, color = colors.textSecondary) }
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun WorkFigureValue(value: String, unknown: Boolean = false, warning: Boolean = false, large: Boolean = false) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val parts = Regex("^([+-]?[\\d.,]+)\\s*([A-Za-z°%가-힣]+)$").matchEntire(value)
    val number = parts?.groupValues?.get(1) ?: value
    val unit = if (unknown) null else parts?.groupValues?.get(2)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        Text(number, Modifier.weight(1f, fill = false).alignByBaseline(),
            style = if (unknown) type.body else if (large) type.clock else type.figure,
            color = if (warning) colors.warning else colors.text)
        unit?.let { Text(it, Modifier.alignByBaseline(), style = type.unit, color = colors.text) }
    }
}
