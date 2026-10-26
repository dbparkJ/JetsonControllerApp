package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

internal fun Modifier.contentTarget(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val colors = LocalGeoColors.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    this
        // KIT §3.7: a disabled row keeps the row look (disabledText title, reason line); only buttons take disabledFill.
        .background(if (enabled && pressed) colors.fieldPressed else Color.Transparent)
        .drawBehind {
            if (focused) {
                val offset = 3.dp.toPx()
                drawRect(colors.surface, Offset(-6.dp.toPx(), -6.dp.toPx()),
                    androidx.compose.ui.geometry.Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()),
                    style = Stroke(4.dp.toPx()))
                drawRect(colors.focus, Offset(-offset, -offset),
                    androidx.compose.ui.geometry.Size(size.width + 2 * offset, size.height + 2 * offset),
                    style = Stroke(2.dp.toPx()))
            }
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled,
            role = Role.Button, onClick = onClick)
}

internal fun Modifier.contentDivider(tone: Tone? = null): Modifier = composed {
    val colors = LocalGeoColors.current
    val color = when (tone) {
        Tone.WARNING -> colors.warningBorder
        Tone.ERROR -> colors.dangerBorder
        else -> colors.border
    }
    drawBehind { drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
}

enum class GeoRowTrailing { None, Status }

private val LocalGeoRowTrailing = staticCompositionLocalOf<GeoRowTrailing?> { null }
private val LocalGeoStatusWidths = staticCompositionLocalOf<MutableMap<Any, Dp>?> { null }
internal val LocalGeoStatusWidthReporter = staticCompositionLocalOf<((Dp) -> Unit)?> { null }

internal fun rowStatusWidth(widthsDp: Collection<Float>): Float = (widthsDp.maxOrNull() ?: 72f).coerceIn(72f, 120f)

internal fun rowStatusStacks(availableDp: Float, fontScale: Float, requiredDp: Float): Boolean =
    fontScale >= 1.5f || availableDp < 280f || requiredDp > 120f

/** Lists declare the column once, including rows with a temporarily missing status. */
@Composable
fun GeoRowList(
    trailingColumn: GeoRowTrailing,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val widths = remember { mutableStateMapOf<Any, Dp>() }
    CompositionLocalProvider(LocalGeoRowTrailing provides trailingColumn, LocalGeoStatusWidths provides widths) {
        Column(modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun GeoRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    value: String? = null,
    badge: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    divider: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 16.dp),
    navigable: Boolean = true
) {
    require(enabled || !disabledReason.isNullOrBlank()) { "Disabled rows need a visible reason" }
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val column = LocalGeoRowTrailing.current ?:
        if (badge != null || value != null) GeoRowTrailing.Status else GeoRowTrailing.None
    val hasStatus = column == GeoRowTrailing.Status
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val widths = LocalGeoStatusWidths.current
    val rowKey = remember { Any() }
    var badgeWidth by remember { mutableStateOf(0.dp) }
    val valueWidth = value?.let { with(density) {
        measurer.measure(AnnotatedString(it), type.inlineStatus, softWrap = false).size.width.toDp()
    } } ?: 0.dp
    val requiredWidth = if (enabled) maxOf(valueWidth, if (badge != null) badgeWidth else 0.dp) else 0.dp
    SideEffect { widths?.set(rowKey, requiredWidth) }
    DisposableEffect(widths, rowKey) { onDispose { widths?.remove(rowKey) } }
    val sharedWidth = rowStatusWidth(widths?.values?.map { it.value } ?: listOf(requiredWidth.value)).dp
    val firstLine = with(density) { type.titleSm.lineHeight.toDp() }
    BoxWithConstraints(modifier.fillMaxWidth().then(if (navigable) Modifier.contentTarget(enabled, onClick) else Modifier)
        .then(if (divider) Modifier.contentDivider() else Modifier).semantics(mergeDescendants = true) {}) {
        val stacked = hasStatus && rowStatusStacks(maxWidth.value, density.fontScale, requiredWidth.value)
        val gap = if (hasStatus) 8.dp else 12.dp
        val hasMeta = !(if (enabled) meta else disabledReason).isNullOrBlank()
        val titleHeight = if (hasMeta) firstLine else maxOf(40.dp, firstLine)
        Column(Modifier.fillMaxWidth().padding(contentPadding).heightIn(min = 48.dp),
            verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = if (hasMeta) Alignment.Top else Alignment.CenterVertically) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading?.invoke() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = if (enabled) colors.text else colors.disabledText, style = type.titleSm)
                    (if (enabled) meta else disabledReason)?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = colors.textSecondary, style = type.supporting)
                    }
                }
                if (hasStatus && !stacked) RowStatusCell(titleHeight, sharedWidth, enabled, value, badge) { badgeWidth = it }
                Box(Modifier.width(24.dp).height(titleHeight), contentAlignment = Alignment.Center) {
                    if (enabled && navigable) RowChevron()
                }
            }
            if (stacked) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Spacer(Modifier.weight(1f))
                    RowStatusCell(firstLine, maxOf(sharedWidth, requiredWidth), enabled, value, badge) { badgeWidth = it }
                    Spacer(Modifier.width(24.dp))
                }
            }
            if (extra != null) Column(Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = extra)
        }
    }
}

@Composable
private fun RowStatusCell(
    firstLine: androidx.compose.ui.unit.Dp,
    width: Dp,
    enabled: Boolean,
    value: String?,
    badge: (@Composable () -> Unit)?,
    reportWidth: (Dp) -> Unit
) {
    Box(Modifier.width(width).heightIn(min = firstLine), contentAlignment = Alignment.CenterEnd) {
        if (enabled) CompositionLocalProvider(LocalGeoRowStatus provides true, LocalGeoStatusWidthReporter provides reportWidth) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                if (value != null) Text(value, color = LocalGeoColors.current.textSecondary,
                    style = LocalGeoType.current.inlineStatus, textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.fillMaxWidth())
                badge?.invoke()
            }
        }
    }
}

@Composable
fun GeoActionRow(
    title: String,
    actionLabel: String,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actionEnabled: Boolean = true,
    disabledReason: String? = null,
    divider: Boolean = true,
    tone: Tone? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 16.dp)
) {
    require(actionEnabled || !disabledReason.isNullOrBlank()) { "Disabled actions need a visible reason" }
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val density = LocalDensity.current
    val firstLine = with(density) { type.titleSm.lineHeight.toDp() }
    BoxWithConstraints(modifier.fillMaxWidth().then(if (divider) Modifier.contentDivider(tone) else Modifier)) {
        val stacked = density.fontScale >= 1.5f || maxWidth < 280.dp
        Column(Modifier.fillMaxWidth().padding(contentPadding).heightIn(min = 48.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Row(Modifier.weight(1f).contentTarget(onClick = onOpen)
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(title, meta, "상세 보기").joinToString(", ")
                    }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.TopCenter) { leading?.invoke() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, color = tone?.let(colors::tone) ?: colors.text, style = type.titleSm)
                        meta?.let { Text(it, color = tone?.let(colors::tone) ?: colors.textSecondary, style = type.supporting) }
                        if (!actionEnabled) Text(disabledReason.orEmpty(), color = colors.textSecondary, style = type.supporting)
                    }
                }
                if (!stacked) FixedRowAction(actionLabel, actionEnabled, onAction,
                    Modifier.offset(y = (firstLine - 56.dp) / 2), tone)
            }
            if (stacked) FixedRowAction(actionLabel, actionEnabled, onAction, Modifier.align(Alignment.End).padding(top = 8.dp), tone)
        }
    }
}

@Composable
private fun FixedRowAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tone: Tone? = null) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.width(88.dp).heightIn(min = 56.dp)
        .onFocusChanged { focused = it.isFocused }
        .geoFocusRing(GeoControlShape, focused)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) tone?.let(LocalGeoColors.current::tone) ?: LocalGeoColors.current.primary else LocalGeoColors.current.disabledText,
            style = LocalGeoType.current.caption, textAlign = TextAlign.Center)
    }
}

@Composable
fun GeoFactRow(label: String, value: String, modifier: Modifier = Modifier, numeric: Boolean = false) {
    GeoPropertyRow(label, value, modifier, numeric = numeric)
}

@Composable
private fun GeoNumericFactRow(label: String, value: String, modifier: Modifier = Modifier, numeric: Boolean = false) {
    val colors = LocalGeoColors.current
    val measurer = rememberTextMeasurer()
    val style = LocalGeoType.current.body
    BoxWithConstraints(modifier.fillMaxWidth().contentDivider().semantics(mergeDescendants = true) {}) {
        val available = (constraints.maxWidth - with(LocalDensity.current) { 16.dp.roundToPx() }).coerceAtLeast(0)
        val labelLayout = measurer.measure(AnnotatedString(label), style,
            constraints = Constraints(maxWidth = (available * 0.4f).toInt()))
        val valueStyle = if (numeric) style.copy(fontFeatureSettings = "tnum") else style
        val valueLayout = measurer.measure(AnnotatedString(value), valueStyle,
            constraints = Constraints(maxWidth = (available * 0.6f).toInt()))
        val stacked = LocalDensity.current.fontScale >= 1.5f || maxWidth < 320.dp ||
            labelLayout.lineCount > 2 || valueLayout.lineCount > 2
        if (stacked) {
            Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = colors.textSecondary, style = style)
                Text(value, color = colors.text, style = valueStyle)
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Text(label, Modifier.weight(0.4f), color = colors.textSecondary, style = style)
                Text(value, Modifier.weight(0.6f), color = colors.text, style = valueStyle, textAlign = TextAlign.End)
            }
        }
    }
}

data class GeoNumericRow(val label: String, val value: String?, val unit: String = "")

/** Every value and unit is measured once for the whole list at the current font scale. */
@Composable
fun GeoNumericRows(rows: List<GeoNumericRow>, modifier: Modifier = Modifier) {
    val colors = LocalGeoColors.current
    val style = LocalGeoType.current.body.copy(fontFeatureSettings = "tnum")
    val fontScale = LocalDensity.current.fontScale
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val values = rows.mapIndexed { index, row ->
            subcompose("value-$index") { Text(row.value ?: "미확인", style = style) }
                .single().measure(Constraints()).width
        }
        val units = rows.mapIndexed { index, row ->
            subcompose("unit-$index") { Text(if (row.value == null) "" else row.unit, style = style) }
                .single().measure(Constraints()).width
        }
        val valueWidth = (values.maxOrNull() ?: 0).toDp()
        val unitWidth = (units.maxOrNull() ?: 0).toDp()
        val stack = fontScale >= 1.5f || valueWidth + unitWidth + 20.dp + 132.dp > constraints.maxWidth.toDp()
        val content = subcompose("rows") {
            Column {
                rows.forEach { row ->
                    if (stack) {
                        GeoNumericFactRow(row.label, row.value?.let { if (row.unit.isEmpty()) it else "$it\u00a0${row.unit}" }
                            ?: "미확인", numeric = true)
                    } else {
                        Row(Modifier.fillMaxWidth().contentDivider().heightIn(min = 56.dp)
                            .padding(vertical = 16.dp).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.Top) {
                            Text(row.label, Modifier.weight(1f).padding(end = 16.dp), color = colors.textSecondary, style = LocalGeoType.current.body)
                            Text(row.value ?: "미확인", Modifier.width(valueWidth),
                                color = if (row.value == null) colors.tone(Tone.UNKNOWN) else colors.text,
                                style = style, textAlign = TextAlign.End)
                            Spacer(Modifier.width(4.dp))
                            Text(if (row.value == null) "" else row.unit, Modifier.width(unitWidth), color = colors.text, style = style)
                        }
                    }
                }
            }
        }.single().measure(constraints.copy(minHeight = 0))
        layout(content.width, constraints.constrainHeight(content.height)) { content.placeRelative(0, 0) }
    }
}

@Composable
fun GeoCheckRow(
    title: String,
    value: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    failureReason: String? = null,
    remedyLabel: String? = null,
    onRemedy: (() -> Unit)? = null
) {
    val colors = LocalGeoColors.current
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).contentDivider().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        RowStatusIcon(tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, color = colors.text, style = LocalGeoType.current.titleSm)
                        Text(value, color = colors.textSecondary, style = LocalGeoType.current.body)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, Modifier.weight(1f), color = colors.text, style = LocalGeoType.current.titleSm)
                        Text(value, Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * 0.5f), color = colors.textSecondary,
                            style = LocalGeoType.current.body, textAlign = TextAlign.End)
                    }
                }
            }
            if (failureReason != null) {
                Text(failureReason, color = colors.text, style = LocalGeoType.current.body)
                if (remedyLabel != null && onRemedy != null) RowAction(remedyLabel, true, onRemedy)
            }
        }
    }
}

@Composable
private fun RowAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GeoButton(label, onClick, modifier, GeoButtonStyle.Secondary, enabled = enabled)
}

@Composable
private fun RowChevron() {
    androidx.compose.material3.Icon(GeoIcons.chevronRight, null, Modifier.size(24.dp), tint = LocalGeoColors.current.textSubtle)
}

@Composable
private fun RowStatusIcon(tone: Tone) = GeoToneIcon(tone)
