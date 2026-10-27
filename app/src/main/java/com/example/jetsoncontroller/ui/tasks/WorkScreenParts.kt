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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.theme.*

/** Shared surfaces for the work, preparation and result screens. */
@Composable
internal fun WorkSurfaceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().clip(GeoShape.card)
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
    Column(modifier.fillMaxWidth().clip(GeoShape.card).background(chosen.background)
        .padding(if (LocalGeoWindow.current.widthDp >= 600f) 24.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompositionLocalProvider(LocalGeoColors provides colors.copy(
            text = chosen.content, textSecondary = chosen.content
        )) { content() }
    }
}

/** Result metrics use the same neutral grid and enlarged-text reflow as device metrics. */
@Composable
internal fun WorkFigureCards(figures: List<GeoFigure>, modifier: Modifier = Modifier) {
    GeoFigureGrid(figures, modifier, contained = true)
}

@Composable
internal fun WorkFigureValue(value: String, unknown: Boolean = false, warning: Boolean = false, large: Boolean = false) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val parts = Regex("^([+-]?[\\d.,]+)\\s*([A-Za-z°%가-힣]+)$").matchEntire(value)
    val number = parts?.groupValues?.get(1) ?: value
    val unit = if (unknown) null else parts?.groupValues?.get(2)
    val style = (if (unknown) type.body else if (large) type.clock else if (LocalGeoWindow.current.widthDp >= 600f)
        type.tabletFigure else type.phoneFigure).copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints {
        val required = measurer.measure(AnnotatedString(number), style, softWrap = false).size.width +
            (unit?.let { measurer.measure(AnnotatedString(it), type.unit, softWrap = false).size.width } ?: 0) + with(density) { 2.dp.roundToPx() }
        if (required > constraints.maxWidth) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(number, style = style, color = if (warning) colors.warning else colors.text)
            unit?.let { Text(it, style = type.unit, color = colors.text) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            Text(number, Modifier.alignByBaseline(), style = style, color = if (warning) colors.warning else colors.text)
            unit?.let { Text(it, Modifier.alignByBaseline(), style = type.unit, color = colors.text) }
        }
    }
}
