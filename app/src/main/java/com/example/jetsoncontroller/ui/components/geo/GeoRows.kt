package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.GeoColors
import com.example.jetsoncontroller.ui.theme.GeoDark
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

// Phase 3-C adapter: only tokens already present before 3-A/3-B are read here.
// Fold this adapter and the private control/icon stand-ins into the shared kit after merge.
internal class ContentColors(private val colors: GeoColors) {
    val text get() = colors.ink
    val secondary get() = colors.muted
    val subtle get() = colors.inkSubtle
    val surface get() = colors.surface
    val border get() = colors.border
    val boundary get() = colors.controlBorder
    val pressed get() = colors.subtle
    val primary get() = colors.primary
    val focus get() = colors.focusRing
    val disabledFill get() = colors.disabled
    val disabledText get() = colors.onDisabled
    val mediaPlate get() = GeoDark.surface

    fun tone(tone: StatusTone): Color = when (tone) {
        StatusTone.SUCCESS -> colors.success
        StatusTone.WARNING -> colors.warning
        StatusTone.ERROR -> colors.danger
        StatusTone.INFO -> colors.info
        StatusTone.PENDING -> colors.pending
        StatusTone.UNKNOWN -> colors.unknown
    }

    fun soft(tone: StatusTone): Color = when (tone) {
        StatusTone.SUCCESS -> colors.successBg
        StatusTone.WARNING -> colors.warningBg
        StatusTone.ERROR -> colors.dangerBg
        StatusTone.INFO -> colors.infoBg
        StatusTone.PENDING -> colors.pendingBg
        StatusTone.UNKNOWN -> colors.unknownBg
    }

    fun onMedia(tone: StatusTone): Color = when (tone) {
        StatusTone.SUCCESS -> GeoDark.success
        StatusTone.WARNING -> GeoDark.warning
        StatusTone.ERROR -> GeoDark.danger
        StatusTone.UNKNOWN -> GeoDark.muted
        StatusTone.INFO -> GeoDark.info
        StatusTone.PENDING -> GeoDark.pending
    }
}

internal val contentColors: ContentColors
    @Composable get() = ContentColors(LocalGeoColors.current)

val LocalContentHighContrast = staticCompositionLocalOf { false }

internal object ContentType {
    val caption: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = (-0.039).sp,
            lineBreak = LineBreak.Paragraph,
            fontWeight = if (LocalContentHighContrast.current) FontWeight.Medium else FontWeight.Normal
        )
    val body: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = (-0.045).sp,
            lineBreak = LineBreak.Paragraph,
            fontWeight = if (LocalContentHighContrast.current) FontWeight.Medium else FontWeight.Normal
        )
    val row: TextStyle
        @Composable get() = body.copy(fontSize = 16.sp, letterSpacing = (-0.048).sp,
            fontWeight = if (LocalContentHighContrast.current) FontWeight.SemiBold else FontWeight.Medium)
    val section: TextStyle
        @Composable get() = row.copy(fontWeight = FontWeight.SemiBold)
    val title: TextStyle
        @Composable get() = section.copy(fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = (-0.27).sp)
    val heading: TextStyle
        @Composable get() = section.copy(fontSize = 24.sp, lineHeight = 32.sp,
            letterSpacing = (-0.6).sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val button: TextStyle
        @Composable get() = section.copy(lineHeight = 20.sp, letterSpacing = 0.sp)
    val mono: TextStyle
        @Composable get() = caption.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.sp)
}

internal fun Modifier.contentTarget(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val colors = contentColors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    this
        .background(if (!enabled) colors.disabledFill else if (pressed) colors.pressed else Color.Transparent)
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

internal fun Modifier.contentDivider(): Modifier = composed {
    val color = contentColors.border
    drawBehind { drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
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
    disabledReason: String? = null
) {
    require(enabled || !disabledReason.isNullOrBlank()) { "Disabled rows need a visible reason" }
    val colors = contentColors
    val shownMeta = if (enabled) meta else disabledReason
    val shownValue = if (enabled) value else null
    val shownBadge = if (enabled) badge else null
    BoxWithConstraints(modifier.fillMaxWidth().contentTarget(enabled, onClick).contentDivider()
        .semantics(mergeDescendants = true) {}) {
        val stacked = LocalDensity.current.fontScale >= 1.5f || maxWidth < 320.dp
        Row(Modifier.fillMaxWidth().heightIn(min = if (shownMeta == null) 56.dp else 72.dp)
            .padding(vertical = if (shownMeta == null) 16.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = if (enabled) colors.text else colors.disabledText, style = ContentType.row)
                shownMeta?.let { Text(it, color = colors.secondary, style = ContentType.caption) }
                if (stacked) {
                    shownValue?.let { Text(it, color = colors.secondary, style = ContentType.body) }
                    shownBadge?.invoke()
                }
            }
            if (!stacked) {
                shownValue?.let { Text(it, Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * 0.4f),
                    color = colors.secondary, style = ContentType.body, textAlign = TextAlign.End) }
                shownBadge?.let { Box(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * 0.45f)) { it() } }
            }
            if (enabled) RowChevron()
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
    disabledReason: String? = null
) {
    require(actionEnabled || !disabledReason.isNullOrBlank()) { "Disabled actions need a visible reason" }
    val colors = contentColors
    val measurer = rememberTextMeasurer()
    val style = ContentType.button
    BoxWithConstraints(modifier.fillMaxWidth().contentDivider()) {
        val density = LocalDensity.current
        val actionWidth = with(density) { measurer.measure(AnnotatedString(actionLabel), style).size.width.toDp() } + 32.dp
        val stacked = density.fontScale >= 1.5f || actionWidth > maxWidth * 0.45f
        val body: @Composable (Modifier) -> Unit = { bodyModifier ->
            Row(bodyModifier.contentTarget(onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(title, meta, "상세 보기").joinToString(", ")
                }.heightIn(min = 72.dp).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                leading?.invoke()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = colors.text, style = ContentType.row)
                    meta?.let { Text(it, color = colors.secondary, style = ContentType.caption) }
                }
            }
        }
        val action: @Composable (Modifier) -> Unit = { actionModifier ->
            Column(actionModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RowAction(actionLabel, actionEnabled, onAction, if (stacked) Modifier.fillMaxWidth() else Modifier)
                if (!actionEnabled) Text(disabledReason.orEmpty(), color = colors.secondary, style = ContentType.body)
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                body(Modifier.fillMaxWidth())
                action(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                body(Modifier.weight(1f))
                action(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * 0.45f))
            }
        }
    }
}

@Composable
fun GeoFactRow(label: String, value: String, modifier: Modifier = Modifier, numeric: Boolean = false) {
    val colors = contentColors
    val measurer = rememberTextMeasurer()
    val style = ContentType.body
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
                Text(label, color = colors.secondary, style = style)
                Text(value, color = colors.text, style = valueStyle)
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Text(label, Modifier.weight(0.4f), color = colors.secondary, style = style)
                Text(value, Modifier.weight(0.6f), color = colors.text, style = valueStyle, textAlign = TextAlign.End)
            }
        }
    }
}

data class GeoNumericRow(val label: String, val value: String?, val unit: String = "")

/** Every value and unit is measured once for the whole list at the current font scale. */
@Composable
fun GeoNumericRows(rows: List<GeoNumericRow>, modifier: Modifier = Modifier) {
    val colors = contentColors
    val style = ContentType.body.copy(fontFeatureSettings = "tnum")
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
                        GeoFactRow(row.label, row.value?.let { if (row.unit.isEmpty()) it else "$it\u00a0${row.unit}" }
                            ?: "미확인", numeric = true)
                    } else {
                        Row(Modifier.fillMaxWidth().contentDivider().heightIn(min = 56.dp)
                            .padding(vertical = 16.dp).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.Top) {
                            Text(row.label, Modifier.weight(1f).padding(end = 16.dp), color = colors.secondary, style = ContentType.body)
                            Text(row.value ?: "미확인", Modifier.width(valueWidth),
                                color = if (row.value == null) colors.tone(StatusTone.UNKNOWN) else colors.text,
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
    tone: StatusTone,
    modifier: Modifier = Modifier,
    failureReason: String? = null,
    remedyLabel: String? = null,
    onRemedy: (() -> Unit)? = null
) {
    val colors = contentColors
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).contentDivider().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        RowStatusIcon(tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, color = colors.text, style = ContentType.row)
                        Text(value, color = colors.secondary, style = ContentType.body)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, Modifier.weight(1f), color = colors.text, style = ContentType.row)
                        Text(value, Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * 0.5f), color = colors.secondary,
                            style = ContentType.body, textAlign = TextAlign.End)
                    }
                }
            }
            if (failureReason != null) {
                Text(failureReason, color = colors.text, style = ContentType.body)
                if (remedyLabel != null && onRemedy != null) RowAction(remedyLabel, true, onRemedy)
            }
        }
    }
}

// TODO(3-B merge): replace these private stand-ins with GeoButtons / GeoIcons.
@Composable
private fun RowAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = contentColors
    Box(modifier.border(1.dp, colors.boundary, RoundedCornerShape(10.dp))
        .contentTarget(enabled, onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) colors.text else colors.disabledText, style = ContentType.button,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowChevron() {
    val color = contentColors.subtle
    Canvas(Modifier.size(20.dp)) {
        drawLine(color, Offset(size.width * .4f, size.height * .25f), Offset(size.width * .65f, size.height * .5f),
            1.75.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .4f, size.height * .75f),
            1.75.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun RowStatusIcon(tone: StatusTone) {
    val color = contentColors.tone(tone)
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.75.dp.toPx()
        if (tone == StatusTone.PENDING) {
            val inset = 2.dp.toPx()
            drawArc(color, -90f, 270f, false, topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(stroke))
        } else {
            drawCircle(color, style = Stroke(stroke))
            when (tone) {
                StatusTone.SUCCESS -> {
                    drawLine(color, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .43f, size.height * .68f), stroke, StrokeCap.Round)
                    drawLine(color, Offset(size.width * .43f, size.height * .68f), Offset(size.width * .76f, size.height * .33f), stroke, StrokeCap.Round)
                }
                StatusTone.UNKNOWN -> drawLine(color, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .7f, size.height * .5f), stroke, StrokeCap.Round)
                else -> {
                    val info = tone == StatusTone.INFO
                    drawCircle(color, 1.dp.toPx(), Offset(size.width / 2, size.height * if (info) .3f else .75f))
                    drawLine(color, Offset(size.width / 2, size.height * if (info) .45f else .25f),
                        Offset(size.width / 2, size.height * if (info) .75f else .55f), stroke, StrokeCap.Round)
                }
            }
        }
    }
}
