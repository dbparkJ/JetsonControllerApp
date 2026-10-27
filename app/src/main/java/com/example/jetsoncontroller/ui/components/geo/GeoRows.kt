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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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

internal fun Modifier.contentTarget(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    geoClickable(enabled = enabled, onClick = onClick)

internal fun Modifier.contentDivider(tone: Tone? = null, startIndent: Dp = 0.dp): Modifier = composed {
    val colors = LocalGeoColors.current
    val color = when (tone) {
        Tone.WARNING -> colors.warningBorder
        Tone.ERROR -> colors.dangerBorder
        else -> colors.border
    }
    drawBehind { drawLine(color, Offset(startIndent.toPx(), size.height), Offset(size.width, size.height), 1f) }
}

enum class GeoRowTrailing { None, Status }

private val LocalGeoRowTrailing = staticCompositionLocalOf<GeoRowTrailing?> { null }
private val LocalGeoStatusWidths = staticCompositionLocalOf<MutableMap<Any, Dp>?> { null }
internal val LocalGeoStatusWidthReporter = staticCompositionLocalOf<((Dp) -> Unit)?> { null }
internal val LocalGeoStatusWrap = staticCompositionLocalOf { false }

internal fun rowStatusWidth(widthsDp: Collection<Float>): Float = (widthsDp.maxOrNull() ?: 72f).coerceIn(72f, 120f)

internal fun rowStatusStacks(availableDp: Float, fontScale: Float, requiredDp: Float): Boolean =
    fontScale >= 1.3f || availableDp < 280f || requiredDp > 120f

/** Lists declare the column once, including rows with a temporarily missing status. */
@Composable
fun GeoRowList(
    trailingColumn: GeoRowTrailing,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val widths = remember { mutableStateMapOf<Any, Dp>() }
    CompositionLocalProvider(LocalGeoRowTrailing provides trailingColumn, LocalGeoStatusWidths provides widths) {
        Column(modifier.fillMaxWidth().clip(com.example.jetsoncontroller.ui.theme.GeoShape.card)
            .background(LocalGeoColors.current.surface), content = content)
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    navigable: Boolean = true,
    leadingIsPhoto: Boolean = false
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
    val stackedWidth = maxOf(sharedWidth, widths?.values?.maxOrNull() ?: requiredWidth)
    val firstLine = with(density) { type.titleSm.lineHeight.toDp() }
    val titleAlignment = rememberFirstLineAlignment()
    val hasMeta = !(if (enabled) meta else disabledReason).isNullOrBlank()
    val anatomy = geoRowAnatomy(hasMeta, leadingIsPhoto)
    BoxWithConstraints(modifier.fillMaxWidth().then(if (navigable) Modifier.contentTarget(enabled, onClick) else Modifier)
        .then(if (divider) Modifier.contentDivider(startIndent = anatomy.dividerXDp.dp) else Modifier)
        .semantics(mergeDescendants = true) {}) {
        val tabletScale = com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow.current.widthDp >= 600f && density.fontScale >= 1.3f && maxWidth < 480.dp
        val stacked = hasStatus && (tabletScale || rowStatusStacks(maxWidth.value, density.fontScale, requiredWidth.value))
        val gap = 8.dp
        val titleHeight = firstLine
        Column(Modifier.fillMaxWidth().heightIn(min = anatomy.minimumHeightDp.dp).padding(contentPadding),
            verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = if (hasMeta) Alignment.Top else Alignment.CenterVertically) {
                Box(Modifier.width((anatomy.textXDp - 16 - 8).dp).height(anatomy.leadingSizeDp.dp),
                    contentAlignment = Alignment.CenterStart) { leading?.invoke() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = if (enabled) colors.text else colors.disabledText, style = type.titleSm,
                        onTextLayout = titleAlignment::onTextLayout)
                    (if (enabled) meta else disabledReason)?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = colors.textSecondary, style = type.supporting)
                    }
                }
                if (hasStatus && !stacked) RowStatusCell(titleHeight, sharedWidth, enabled, value, badge,
                    Modifier.alignToFirstTitleLine(titleAlignment, titleCentered = !hasMeta)) { badgeWidth = it }
                Box(Modifier.alignToFirstTitleLine(titleAlignment, titleCentered = !hasMeta)
                    .width(24.dp).height(titleHeight), contentAlignment = Alignment.Center) {
                    if (enabled && navigable) RowChevron()
                }
            }
            if (stacked) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Spacer(Modifier.weight(1f))
                    RowStatusCell(firstLine, stackedWidth, enabled, value, badge,
                        wrap = true) { badgeWidth = it }
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
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    reportWidth: (Dp) -> Unit
) {
    Box(modifier.width(width).heightIn(min = firstLine), contentAlignment = Alignment.CenterEnd) {
        if (enabled) CompositionLocalProvider(LocalGeoRowStatus provides true, LocalGeoStatusWrap provides wrap,
            LocalGeoStatusWidthReporter provides reportWidth) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                if (value != null) Text(value, color = LocalGeoColors.current.textSecondary,
                    style = LocalGeoType.current.inlineStatus.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.End,
                    maxLines = if (wrap) Int.MAX_VALUE else 1, softWrap = wrap,
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
) {
    require(actionEnabled || !disabledReason.isNullOrBlank()) { "Disabled actions need a visible reason" }
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val density = LocalDensity.current
    val titleAlignment = rememberFirstLineAlignment()
    BoxWithConstraints(modifier.fillMaxWidth().then(if (divider) Modifier.contentDivider(tone, 56.dp) else Modifier)) {
        val stacked = density.fontScale >= 1.3f || maxWidth < 280.dp ||
            (com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow.current.widthDp >= 600f &&
                density.fontScale >= 1.3f && maxWidth < 480.dp)
        Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(contentPadding),
            verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Row(Modifier.weight(1f).contentTarget(onClick = onOpen)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val shift = if (stacked) 0 else (-titleAlignment.offset(56.dp.roundToPx(), false)).coerceAtLeast(0)
                        layout(placeable.width, maxOf(48.dp.roundToPx(), placeable.height + shift)) {
                            placeable.placeRelative(0, shift)
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(title, meta, "상세 보기").joinToString(", ")
                    }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(24.dp).alignToFirstTitleLine(titleAlignment), contentAlignment = Alignment.TopCenter) { leading?.invoke() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, color = tone?.let(colors::tone) ?: colors.text, style = type.titleSm,
                            onTextLayout = titleAlignment::onTextLayout)
                        meta?.let { Text(it, color = tone?.let(colors::tone) ?: colors.textSecondary, style = type.supporting) }
                        if (!actionEnabled) Text(disabledReason.orEmpty(), color = colors.textSecondary, style = type.supporting)
                    }
                }
                if (!stacked) FixedRowAction(actionLabel, actionEnabled, onAction, tone = tone)
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
        .geoClickable(enabled = enabled, onClick = onClick)
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
    androidx.compose.material3.Icon(GeoIcons.chevronRight, null, Modifier.size(24.dp).testTag("geo-row-chevron"), tint = LocalGeoColors.current.textSubtle)
}

@Composable
private fun RowStatusIcon(tone: Tone) = GeoToneIcon(tone)
