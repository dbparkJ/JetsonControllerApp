package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun GeoSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null
) {
    GeoSelectionRow(title, description, enabled, disabledReason, modifier,
        trailing = true, selected = checked, role = Role.Switch, onSelect = { onCheckedChange(!checked) }
    ) { focused ->
        val c = geoControlColors()
        val position by animateDpAsState(
            if (checked) 20.dp else 0.dp,
            tween(180, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)), label = "geoSwitch"
        )
        Box(Modifier.size(52.dp, 32.dp).geoFocusRing(CircleShape, focused)
            .background(if (!enabled) c.disabledFill else if (checked) c.primary else c.switchTrackOff, CircleShape)
            .padding(4.dp)) {
            Box(Modifier.offset(x = position).size(24.dp).shadow(1.dp, CircleShape)
                .background(if (!enabled) c.disabledText else if (checked) c.switchThumbOn else c.switchThumbOff, CircleShape))
        }
    }
}

@Composable
fun GeoCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null
) {
    GeoSelectionRow(title, description, enabled, disabledReason, modifier,
        trailing = false, selected = checked, role = Role.Checkbox, onSelect = { onCheckedChange(!checked) }
    ) { focused ->
        val c = geoControlColors()
        Canvas(Modifier.size(20.dp).geoFocusRing(RoundedCornerShape(6.dp), focused)) {
            val fill = if (checked) { if (enabled) c.primary else c.disabledFill } else c.surface
            val stroke = if (!enabled) c.disabledText else if (checked) c.primary else c.borderStrong
            val inset = 1.dp.toPx()
            val extent = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            drawRoundRect(fill, cornerRadius = CornerRadius(6.dp.toPx()))
            drawRoundRect(stroke, topLeft = Offset(inset, inset), size = extent,
                cornerRadius = CornerRadius(5.dp.toPx()), style = Stroke(2.dp.toPx()))
            if (checked) {
                val color = if (enabled) c.onPrimary else c.disabledText
                drawLine(color, Offset(size.width * 0.25f, size.height * 0.5f),
                    Offset(size.width * 0.43f, size.height * 0.68f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.43f, size.height * 0.68f),
                    Offset(size.width * 0.75f, size.height * 0.32f), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
fun GeoRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null
) {
    GeoSelectionRow(title, description, enabled, disabledReason, modifier,
        trailing = false, selected = selected, role = Role.RadioButton, onSelect = onClick
    ) { focused ->
        val c = geoControlColors()
        Canvas(Modifier.size(20.dp).geoFocusRing(CircleShape, focused)) {
            val color = if (!enabled) c.disabledText else if (selected) c.primary else c.borderStrong
            drawCircle(color, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (selected) drawCircle(color, radius = 5.dp.toPx())
        }
    }
}

/** Group labels belong to the selectable group, including when its rows wrap. */
@Composable
fun GeoRadioGroup(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null
) {
    require(options.isNotEmpty() && selectedIndex in options.indices)
    val c = geoControlColors()
    Column(modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = label }) {
        Text(label, style = geoLabelTextStyle(), color = c.text)
        options.forEachIndexed { index, option ->
            GeoRadioRow(option, index == selectedIndex, { onSelect(index) }, enabled = enabled, disabledReason = disabledReason)
        }
    }
}

@Composable
private fun GeoSelectionRow(
    title: String,
    description: String?,
    enabled: Boolean,
    disabledReason: String?,
    modifier: Modifier,
    trailing: Boolean,
    selected: Boolean,
    role: Role,
    onSelect: () -> Unit,
    indicator: @Composable (focused: Boolean) -> Unit
) {
    require(enabled || !disabledReason.isNullOrBlank()) { "Disabled controls require a visible reason" }
    val c = geoControlColors()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val interaction = if (role == Role.RadioButton) {
        Modifier.selectable(selected, source, indication = null, enabled = enabled, role = role, onClick = onSelect)
    } else {
        Modifier.toggleable(selected, source, indication = null, enabled = enabled, role = role, onValueChange = { onSelect() })
    }
    val meta = if (enabled) description else listOfNotNull(description, disabledReason).joinToString(" ")
    Row(
        modifier.fillMaxWidth().heightIn(min = if (meta.isNullOrEmpty()) 56.dp else 72.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(if (pressed) c.fieldPressed else Color.Transparent)
            .then(interaction)
            .padding(vertical = if (meta.isNullOrEmpty()) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!trailing) indicator(focused)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
                color = if (enabled) c.text else c.disabledText)
            if (!meta.isNullOrEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (trailing) indicator(focused)
    }
}

/** [onOpenPicker] opens the owning screen's choice sheet when the segments cannot fit. */
@Composable
fun GeoSegmented(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null
) {
    require(options.size in 2..4 && selectedIndex in options.indices)
    require(enabled || !disabledReason.isNullOrBlank())
    val c = geoControlColors()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = geoLabelTextStyle()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val innerWidth = (maxWidth - 8.dp).coerceAtLeast(0.dp)
        val required = options.map {
            maxOf(with(density) { 72.dp.roundToPx() },
                measurer.measure(AnnotatedString(it), style.copy(fontWeight = FontWeight.SemiBold), softWrap = false, maxLines = 1).size.width +
                    with(density) { 32.dp.roundToPx() })
        }
        val fits = geoActionsFit(with(density) { innerWidth.roundToPx() }, required, 0, GeoActionLayout.EqualShares)
        if (density.fontScale >= 1.5f || !fits) {
            GeoPickerField(label, options[selectedIndex], onOpenPicker, enabled = enabled, disabledReason = disabledReason)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = style, color = c.text)
                val segmentWidth = innerWidth / options.size
                val position by animateDpAsState(segmentWidth * selectedIndex,
                    tween(180, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)), label = "geoSegment")
                val pill = RoundedCornerShape(50)
                Box(Modifier.fillMaxWidth().background(c.field, pill).border(1.dp, c.borderStrong, pill)
                    .selectableGroup().semantics { contentDescription = label }) {
                    Box(Modifier.matchParentSize().padding(4.dp)) {
                        Box(Modifier.offset(x = position).width(segmentWidth).fillMaxHeight()
                            .background(c.surface, pill).border(1.dp, c.borderStrong, pill))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        options.forEachIndexed { index, option ->
                            val source = remember { MutableInteractionSource() }
                            val pressed by source.collectIsPressedAsState()
                            var focused by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                                .onFocusChanged { focused = it.isFocused }
                                .selectable(index == selectedIndex, source, indication = null, enabled = enabled,
                                    role = Role.Tab, onClick = { onSelect(index) })
                                .padding(vertical = 4.dp)
                                .geoFocusRing(pill, focused)
                                .background(if (pressed) c.fieldPressed else Color.Transparent, pill)
                                .padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center
                            ) {
                                Text(option, style = style.copy(fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium),
                                    color = if (enabled) c.text else c.disabledText, maxLines = 1)
                            }
                        }
                    }
                }
                if (!enabled && disabledReason != null) Text(disabledReason, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoSliderRow(
    title: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    step: Float = 1f,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    require(valueRange.start.isFinite() && valueRange.endInclusive.isFinite() && valueRange.endInclusive > valueRange.start)
    require(step.isFinite() && step > 0f && value.isFinite())
    require(enabled || !disabledReason.isNullOrBlank())
    val intervals = ((valueRange.endInclusive - valueRange.start) / step).roundToInt()
    require(intervals >= 1 && kotlin.math.abs(intervals * step - (valueRange.endInclusive - valueRange.start)) < 0.001f)
    val c = geoControlColors()
    val current = value.coerceIn(valueRange)
    var focused by remember { mutableStateOf(false) }
    fun change(newValue: Float) {
        onValueChange((valueRange.start + ((newValue - valueRange.start) / step).roundToInt() * step).coerceIn(valueRange))
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (enabled) c.text else c.disabledText)
            Text(valueLabel, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = if (enabled) c.text else c.disabledText,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GeoIconButton(GeoIcons.zoomOut, "$title 감소", {
                change(current - step)
                onValueChangeFinished?.invoke()
            }, enabled = enabled && current > valueRange.start)
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Slider(current, ::change, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .onFocusChanged { focused = it.hasFocus }
                    .geoFocusRing(GeoControlShape, focused)
                    .semantics { contentDescription = title; stateDescription = valueLabel },
                    enabled = enabled, valueRange = valueRange, steps = intervals - 1,
                    onValueChangeFinished = onValueChangeFinished,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary,
                        inactiveTrackColor = c.field, activeTickColor = c.onPrimary, inactiveTickColor = c.borderStrong,
                        disabledThumbColor = c.disabledText, disabledActiveTrackColor = c.disabledFill,
                        disabledInactiveTrackColor = c.disabledFill, disabledActiveTickColor = c.disabledText,
                        disabledInactiveTickColor = c.disabledText))
            }
            GeoIconButton(GeoIcons.zoomIn, "$title 증가", {
                change(current + step)
                onValueChangeFinished?.invoke()
            }, enabled = enabled && current < valueRange.endInclusive)
        }
        if (!enabled && disabledReason != null) Text(disabledReason, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}
