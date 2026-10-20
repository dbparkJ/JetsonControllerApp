package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.GeoShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

internal val GeoControlShape = GeoShape.control

@Composable
internal fun geoButtonTextStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(
    fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp
)

@Composable
internal fun geoInputTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp
)

@Composable
internal fun geoLabelTextStyle(): TextStyle = MaterialTheme.typography.labelMedium.copy(
    fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp
)

/** The ring is outside the control; callers leave room for its 8 dp outer extent. */
@Composable
internal fun Modifier.geoFocusRing(
    shape: Shape,
    focused: Boolean,
    chrome: Boolean = false,
    tint: Color? = null
): Modifier {
    val c = LocalGeoColors.current
    val ring = tint ?: if (chrome) c.navFocus else c.focus
    return drawWithContent {
        if (focused) {
            fun outline(outset: Float, width: Float, color: Color) {
                val expanded = Size(size.width + outset * 2, size.height + outset * 2)
                withTransform({ translate(-outset, -outset) }) {
                    drawOutline(shape.createOutline(expanded, layoutDirection, this), color, style = Stroke(width))
                }
            }
            outline(6.dp.toPx(), 4.dp.toPx(), tint?.copy(alpha = .24f) ?: c.focusHalo)
            outline(3.dp.toPx(), 2.dp.toPx(), ring)
        }
        drawContent()
    }
}

enum class GeoButtonStyle { Primary, Secondary, Tertiary, DangerOutline, DangerFilled }

/** Lines a tertiary label up with the content edge (KIT §3.9): its 12 dp padding hangs into the gutter. */
enum class GeoButtonEdge { None, Start, End }

private fun tertiaryEdgeOffset(style: GeoButtonStyle, edge: GeoButtonEdge): Modifier =
    if (style != GeoButtonStyle.Tertiary) Modifier else when (edge) {
        GeoButtonEdge.None -> Modifier
        GeoButtonEdge.Start -> Modifier.offset(x = (-12).dp)
        GeoButtonEdge.End -> Modifier.offset(x = 12.dp)
    }

@Composable
fun GeoButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GeoButtonStyle = GeoButtonStyle.Primary,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyLabel: String? = null,
    icon: ImageVector? = null,
    disabledReason: String? = null,
    actionArea: Boolean = false,
    contentColor: Color? = null,
    edge: GeoButtonEdge = GeoButtonEdge.None
) {
    require(!busy || !busyLabel.isNullOrBlank()) { "Busy buttons require a present-progressive label" }
    val c = LocalGeoColors.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val filled = style == GeoButtonStyle.Primary || style == GeoButtonStyle.DangerFilled
    val danger = style == GeoButtonStyle.DangerFilled || style == GeoButtonStyle.DangerOutline
    val fill = when {
        !enabled -> c.disabledFill
        style == GeoButtonStyle.Primary -> if (pressed) c.primaryPressed else c.primary
        style == GeoButtonStyle.DangerFilled -> if (pressed) c.dangerPressed else c.danger
        pressed && contentColor != null -> contentColor.copy(alpha = .12f)
        pressed -> c.fieldPressed
        style == GeoButtonStyle.Secondary -> c.surface
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> c.disabledText
        contentColor != null -> contentColor
        filled -> if (danger) c.onDanger else c.onPrimary
        danger -> c.danger
        style == GeoButtonStyle.Tertiary -> c.primary
        else -> c.text
    }
    val border = when (style) {
        GeoButtonStyle.Secondary -> BorderStroke(1.dp, if (enabled) c.borderControl else c.disabledText)
        GeoButtonStyle.DangerOutline -> BorderStroke(1.dp, if (enabled) c.danger else c.disabledText)
        else -> null
    }
    // The cap comes first so a caller's fillMaxWidth() fills up to 560 dp instead of overriding it.
    Column(Modifier.widthIn(max = 560.dp).then(modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .then(tertiaryEdgeOffset(style, edge))
                .then(if (style == GeoButtonStyle.Tertiary && !actionArea) Modifier.width(IntrinsicSize.Max) else Modifier)
                .heightIn(min = if (filled && actionArea) 52.dp else 48.dp)
                .defaultMinSize(minWidth = 48.dp)
                .onFocusChanged { focused = it.isFocused }
                .geoFocusRing(GeoControlShape, focused, tint = contentColor)
                .clip(GeoControlShape)
                .background(fill)
                .then(if (border != null) Modifier.border(border, GeoControlShape) else Modifier)
                .clickable(source, indication = null, enabled = enabled && !busy, role = Role.Button, onClick = onClick)
                .semantics { if (busy) stateDescription = busyLabel.orEmpty() }
                .padding(horizontal = if (style == GeoButtonStyle.Tertiary) 12.dp else 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) {
                GeoBusyIndicator(color = content)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            }
            Text(
                if (busy) busyLabel.orEmpty() else label,
                modifier = Modifier.weight(1f, fill = false),
                style = geoButtonTextStyle(), color = content, textAlign = TextAlign.Center
            )
        }
        if (!enabled && disabledReason != null) {
            Text(disabledReason, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

@Composable
fun GeoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    chrome: Boolean = false,
    disabledReason: String? = null
) {
    require(contentDescription.isNotBlank())
    val c = LocalGeoColors.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(48.dp)
                .onFocusChanged { focused = it.isFocused }
                .geoFocusRing(CircleShape, focused, chrome)
                .clip(CircleShape)
                .background(if (!enabled) c.disabledFill else if (pressed) c.fieldPressed else Color.Transparent)
                .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = when {
                !enabled -> c.disabledText
                chrome -> c.onTopBarIcon
                else -> c.textSecondary
            })
        }
        if (!enabled && disabledReason != null) {
            Text(disabledReason, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

enum class GeoActionLayout { EqualShares, Dialog }

data class GeoButtonAction(
    val label: String,
    val onClick: () -> Unit,
    val style: GeoButtonStyle = GeoButtonStyle.Primary,
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val busyLabel: String? = null,
    val icon: ImageVector? = null,
    val disabledReason: String? = null
)

/** Inputs include padding, icon/spinner and the minimum target width, in measured pixels. */
internal fun geoActionsFit(
    availableWidth: Int,
    requiredWidths: List<Int>,
    gap: Int,
    layout: GeoActionLayout
): Boolean {
    if (availableWidth < 0 || gap < 0 || requiredWidths.any { it < 0 }) return false
    if (requiredWidths.isEmpty()) return true
    val usable = availableWidth.toLong() - gap.toLong() * (requiredWidths.size - 1)
    if (usable < 0) return false
    return when (layout) {
        GeoActionLayout.EqualShares -> requiredWidths.all { it.toLong() * requiredWidths.size <= usable }
        GeoActionLayout.Dialog -> requiredWidths.sumOf { it.toLong() } <= usable
    }
}

/** Use for screen pairs, dialog actions and sheet footers; action order is reading order. */
@Composable
fun GeoButtonActions(
    actions: List<GeoButtonAction>,
    modifier: Modifier = Modifier,
    layout: GeoActionLayout = GeoActionLayout.EqualShares
) {
    if (actions.isEmpty()) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = geoButtonTextStyle()
    BoxWithConstraints(modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        val gap = with(density) { 8.dp.roundToPx() }
        val widths = actions.map { action ->
            val label = if (action.busy) action.busyLabel.orEmpty() else action.label
            val padding = if (action.style == GeoButtonStyle.Tertiary) 24.dp else 32.dp
            val adornment = if (action.busy) 26.dp else if (action.icon != null) 30.dp else 0.dp
            maxOf(
                with(density) { 48.dp.roundToPx() },
                measurer.measure(AnnotatedString(label), style, softWrap = false, maxLines = 1).size.width +
                    with(density) { (padding + adornment).roundToPx() }
            )
        }
        val row = geoActionsFit(constraints.maxWidth, widths, gap, layout)
        val pairedPrimary = actions.any { it.style == GeoButtonStyle.Primary || it.style == GeoButtonStyle.DangerFilled }
        @Composable
        fun action(action: GeoButtonAction, buttonModifier: Modifier) {
            GeoButton(
                action.label, action.onClick, buttonModifier, action.style,
                action.enabled, action.busy, action.busyLabel, action.icon, action.disabledReason,
                actionArea = pairedPrimary
            )
        }
        if (row) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                actions.forEachIndexed { index, item ->
                    action(item, if (layout == GeoActionLayout.EqualShares) Modifier.weight(1f)
                    else with(density) { Modifier.width(widths[index].toDp()) })
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.sortedBy {
                    if (it.style == GeoButtonStyle.Primary || it.style == GeoButtonStyle.DangerFilled) 0 else 1
                }.forEach { action(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}
