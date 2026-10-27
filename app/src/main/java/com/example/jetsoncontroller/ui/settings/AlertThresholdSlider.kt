package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.GeoControlShape
import com.example.jetsoncontroller.ui.components.geo.GeoIconButton
import com.example.jetsoncontroller.ui.components.geo.GeoIcons
import com.example.jetsoncontroller.ui.components.geo.geoFocusRing
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

/** The switch row already supplies the visible title and threshold description. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeoAlertThresholdSlider(
    title: String,
    value: Int,
    valueLabel: String,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onStep: (Int) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val c = LocalGeoColors.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GeoIconButton(GeoIcons.zoomOut, "$title 감소", { onStep((value - 1).coerceIn(range)) },
                enabled = enabled && value > range.first)
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Slider(value.toFloat(), { onValueChange(alertThresholdValue(it, range)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .onFocusChanged { focused = it.hasFocus }
                        .geoFocusRing(GeoControlShape, focused)
                        .semantics { contentDescription = title; stateDescription = valueLabel },
                    enabled = enabled, valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = range.last - range.first - 1, onValueChangeFinished = onValueChangeFinished,
                    thumb = {
                        Canvas(Modifier.size(24.dp)) {
                            drawCircle(c.surface)
                            drawCircle(if (enabled) c.primary else c.disabledText, radius = 10.dp.toPx())
                        }
                    },
                    track = {
                        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                            val fraction = (value - range.first).toFloat() / (range.last - range.first)
                            val start = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
                            val end = if (layoutDirection == LayoutDirection.Rtl) size.width * (1f - fraction) else size.width * fraction
                            drawLine(if (enabled) c.borderStrong else c.disabledFill,
                                Offset(0f, center.y), Offset(size.width, center.y), size.height, StrokeCap.Round)
                            if (fraction > 0f) drawLine(if (enabled) c.primary else c.disabledText,
                                Offset(start, center.y), Offset(end, center.y), size.height, StrokeCap.Round)
                        }
                    })
            }
            GeoIconButton(GeoIcons.zoomIn, "$title 증가", { onStep((value + 1).coerceIn(range)) },
                enabled = enabled && value < range.last)
        }
        if (!enabled) Text("알림을 켜면 기준을 조정할 수 있습니다.",
            color = c.disabledText, style = LocalGeoType.current.caption)
    }
}
