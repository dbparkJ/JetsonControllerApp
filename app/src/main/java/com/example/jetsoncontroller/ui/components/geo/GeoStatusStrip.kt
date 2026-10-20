package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Tone
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

enum class GeoStripKind(val priority: Int) {
    AUTHENTICATION_ERROR(0), DISCONNECTED(1), OUTSIDE_ZONE(2), COLLECTING(3), LAST_SYNC(4)
}

data class GeoStatusStripState(
    val kind: GeoStripKind,
    val deviceName: String,
    val message: String,
    val actions: List<GeoOverlayAction> = emptyList()
) {
    init {
        require(deviceName.isNotBlank())
        require(actions.size <= 2)
    }
    val text: String get() = "$deviceName · $message"
}

/** Input order breaks ties; one screen always renders at most one strip. */
fun highestPriorityStrip(candidates: List<GeoStatusStripState>): GeoStatusStripState? =
    candidates.minByOrNull { it.kind.priority }

private val GeoStripKind.tone: Tone get() = when (this) {
    GeoStripKind.AUTHENTICATION_ERROR -> Tone.ERROR
    GeoStripKind.DISCONNECTED, GeoStripKind.OUTSIDE_ZONE -> Tone.WARNING
    GeoStripKind.COLLECTING -> Tone.INFO
    GeoStripKind.LAST_SYNC -> Tone.UNKNOWN
}

@Composable
fun GeoStatusStrip(candidates: List<GeoStatusStripState>, modifier: Modifier = Modifier) {
    val selected = highestPriorityStrip(candidates)
    var lastShown by remember { mutableStateOf(selected) }
    SideEffect { if (selected != null) lastShown = selected }
    AnimatedVisibility(
        visible = selected != null, modifier = modifier,
        enter = expandVertically(tween(180), expandFrom = Alignment.Top),
        exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top)
    ) {
        (selected ?: lastShown)?.let { OverlayStrip(it) }
    }
}

@Composable
private fun OverlayStrip(state: GeoStatusStripState) {
    val t = LocalGeoColors.current
    val measure = rememberTextMeasurer()
    val buttonStyle = LocalGeoType.current.button
    BoxWithConstraints(Modifier.fillMaxWidth().background(t.soft(state.kind.tone))
        .semantics { liveRegion = LiveRegionMode.Polite }) {
        val gutter = overlayGutter(maxWidth.value).dp
        val density = LocalDensity.current
        val single = state.actions.singleOrNull()
        val actionWidth = if (single == null) 0 else measure.measure(single.label, buttonStyle, softWrap = false, maxLines = 1).size.width +
            with(density) { 24.dp.roundToPx() }
        val inline = single != null && constraints.maxWidth - actionWidth >= with(density) { (gutter * 2 + 32.dp + 132.dp).roundToPx() }
        Column(Modifier.padding(horizontal = gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GeoToneIcon(state.kind.tone)
                Text(state.text, Modifier.weight(1f), color = t.text, style = LocalGeoType.current.body)
                if (inline && single != null) GeoOverlayButton(single, edge = GeoButtonEdge.End)
            }
            if (!inline && state.actions.isNotEmpty()) {
                // Each action gets its natural one-line width, or its own wrapping row.
                StripActions(state.actions)
            }
            state.actions.filter { !it.enabled }.mapNotNull { it.disabledReason }.forEach {
                Text(it, color = t.textSecondary, style = LocalGeoType.current.body)
            }
        }
    }
}

@Composable
private fun StripActions(actions: List<GeoOverlayAction>) {
    val measure = rememberTextMeasurer()
    val style = LocalGeoType.current.button
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widths = actions.map { measure.measure(it.label, style, softWrap = false, maxLines = 1).size.width + with(density) { 24.dp.roundToPx() } }
        if (overlayActionsFit(widths, constraints.maxWidth, with(density) { 8.dp.roundToPx() }, equal = false)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                actions.forEachIndexed { index, action ->
                    GeoOverlayButton(action, edge = if (index == actions.lastIndex) GeoButtonEdge.End else GeoButtonEdge.None)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { GeoOverlayButton(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}
