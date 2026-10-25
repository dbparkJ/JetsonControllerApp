package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.geoCardShadow
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun snackbarTimeoutMillis(
    hasAction: Boolean,
    hasDismissAction: Boolean = false,
    recommend: (original: Long, icons: Boolean, text: Boolean, controls: Boolean) -> Long = { original, _, _, _ -> original }
): Long = recommend(if (hasAction) 8_000L else 4_000L, hasDismissAction, true, hasAction || hasDismissAction)

internal fun snackbarAction(label: String?, onAction: (() -> Unit)?): GeoOverlayAction? =
    if (label.isNullOrBlank() || onAction == null) null else GeoOverlayAction(label, onAction)

private data class OverlaySnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    // GeoSnackbar owns the exact 4/8-second accessibility-aware timer.
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
) : SnackbarVisuals

suspend fun SnackbarHostState.showGeoSnackbar(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false
): SnackbarResult = showSnackbar(OverlaySnackbarVisuals(message, actionLabel?.takeIf { it.isNotBlank() }, withDismissAction))

/** Submit with [showGeoSnackbar]; place in the shell/Scaffold slot above bottom chrome. */
@Composable
fun GeoSnackbar(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val accessibility = LocalAccessibilityManager.current
    SnackbarHost(hostState) { data ->
        LaunchedEffect(data, accessibility) {
            val timeout = snackbarTimeoutMillis(data.visuals.actionLabel != null, data.visuals.withDismissAction) { original, icons, text, controls ->
                accessibility?.calculateRecommendedTimeoutMillis(original, containsIcons = icons, containsText = text, containsControls = controls) ?: original
            }
            if (timeout != Long.MAX_VALUE) {
                delay(timeout)
                data.dismiss()
            }
        }
        OverlaySnackbar(data, modifier)
    }
}

@Composable
private fun OverlaySnackbar(data: SnackbarData, modifier: Modifier) {
    val label = data.visuals.actionLabel
    val color = LocalGeoColors.current.onInverse
    val actionColor = LocalGeoColors.current.inverseAction
    val measure = rememberTextMeasurer()
    val buttonStyle = LocalGeoType.current.button
    Surface(
        modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().geoCardShadow()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = GeoShape.card, color = LocalGeoColors.current.inverse, contentColor = color, shadowElevation = 0.dp
    ) {
        BoxWithConstraints(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            val density = LocalDensity.current
            val actionWidth = if (label == null) 0 else measure.measure(label, buttonStyle, softWrap = false, maxLines = 1).size.width +
                with(density) { 24.dp.roundToPx() }
            val controlsWidth = actionWidth + with(density) { (if (data.visuals.withDismissAction) 56.dp else 0.dp).roundToPx() }
            val inline = controlsWidth + with(density) { 140.dp.roundToPx() } <= constraints.maxWidth
            val actions: @Composable RowScope.() -> Unit = {
                if (label != null) GeoOverlayButton(GeoOverlayAction(label, data::performAction),
                    modifier = Modifier.widthIn(max = this@BoxWithConstraints.maxWidth - if (data.visuals.withDismissAction) 56.dp else 0.dp), color = actionColor)
                if (data.visuals.withDismissAction) {
                    if (label != null) Spacer(Modifier.width(8.dp))
                    OverlaySnackbarClose(color, data::dismiss)
                }
            }
            if (inline) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(data.visuals.message, Modifier.weight(1f).padding(vertical = 8.dp), color = color, style = LocalGeoType.current.body)
                    actions()
                }
            } else {
                Column {
                    Text(data.visuals.message, Modifier.padding(top = 8.dp, bottom = 8.dp, end = 12.dp), color = color, style = LocalGeoType.current.body)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

@Composable
private fun OverlaySnackbarClose(color: Color, onClose: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box(Modifier.size(48.dp).background(if (pressed) color.copy(alpha = .12f) else Color.Transparent, CircleShape)
        .drawBehind { if (focused) drawCircle(color, radius = size.minDimension / 2 + 4.dp.toPx(), style = Stroke(2.dp.toPx())) }
        .semantics { contentDescription = "메시지 닫기" }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(GeoIcons.close, null, Modifier.size(24.dp), tint = color)

    }
}
