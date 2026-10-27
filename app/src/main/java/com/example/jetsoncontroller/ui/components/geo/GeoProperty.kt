package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

internal fun geoPropertyListStacks(fontScale: Float, valueWidthDp: Float, labelLineCounts: List<Int>): Boolean =
    fontScale >= 1.3f || valueWidthDp < 160f || labelLineCounts.any { it > 2 }

internal fun geoPropertyLabelWidth(contentWidthDp: Float, longestLabelWidthDp: Float): Float =
    longestLabelWidthDp.coerceIn(104f, maxOf(104f, contentWidthDp * .4f))

private val LocalPropertyStacked = staticCompositionLocalOf<Boolean?> { null }
private val LocalPropertyLabelWidth = staticCompositionLocalOf<Dp?> { null }

/** Labels are measured together so one long label switches the entire list. */
@Composable
fun GeoPropertyList(
    labels: List<String>,
    modifier: Modifier = Modifier,
    hasIcons: Boolean = false,
    labelWidth: Dp? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LocalGeoType.current.propertyLabel
    BoxWithConstraints(modifier.fillMaxWidth()
        .padding(horizontal = if (LocalGeoGroupInset.current) 16.dp else 0.dp)) {
        val iconWidth = if (hasIcons) 26.dp else 0.dp
        val longestLabelWidth = labels.maxOfOrNull {
            with(density) { measurer.measure(AnnotatedString(it), style, softWrap = false).size.width.toDp() }
        } ?: 0.dp
        val sharedLabelWidth = labelWidth ?: geoPropertyLabelWidth(maxWidth.value,
            (longestLabelWidth + iconWidth + 1.dp).value).dp
        val textWidthPx = with(density) { (sharedLabelWidth - iconWidth).coerceAtLeast(0.dp).roundToPx() }
        val lines = labels.map { measurer.measure(AnnotatedString(it), style,
            constraints = Constraints(maxWidth = textWidthPx)).lineCount }
        val stacked = geoPropertyListStacks(density.fontScale, (maxWidth - sharedLabelWidth - 8.dp).value, lines)
        CompositionLocalProvider(LocalPropertyStacked provides stacked, LocalPropertyLabelWidth provides sharedLabelWidth,
            LocalGeoGroupInset provides false) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** Readable document facts; numeric tables deliberately use their own layout. */
@Composable
fun GeoPropertyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    icon: ImageVector? = null,
    supportingText: String? = null,
    tone: Tone? = null,
    variant: GeoBadgeVariant = GeoBadgeVariant.INLINE,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    valueContent: (@Composable () -> Unit)? = null
) {
    require(onClick == null || enabled || !disabledReason.isNullOrBlank()) { "Disabled properties need a visible reason" }
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val listStacked = LocalPropertyStacked.current
    val listLabelWidth = LocalPropertyLabelWidth.current
    BoxWithConstraints(modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.contentTarget(enabled, onClick) else Modifier)
        .semantics(mergeDescendants = true) {}
        .padding(horizontal = if (LocalGeoGroupInset.current) 16.dp else 0.dp)) {
        val labelWidth = listLabelWidth ?: 104.dp
        val iconWidth = if (icon == null) 0.dp else 26.dp
        val lines = measurer.measure(AnnotatedString(label), type.propertyLabel,
            constraints = Constraints(maxWidth = with(density) { (labelWidth - iconWidth).coerceAtLeast(0.dp).roundToPx() })).lineCount
        val structured = value.any { it in "/._-" }
        val stacked = (listStacked ?: geoPropertyListStacks(density.fontScale, (maxWidth - labelWidth - 8.dp).value, listOf(lines))) ||
            (structured && (density.fontScale >= 1.3f || maxWidth < 360.dp))
        val mediaLabel = if (colors.text == com.example.jetsoncontroller.ui.theme.GeoFixedColors.mediaText)
            colors.textSecondary else colors.textSubtle
        val labelContent: @Composable (Modifier) -> Unit = { labelModifier ->
            Row(labelModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                icon?.let { Icon(it, null, Modifier.size(18.dp), tint = mediaLabel) }
                Text(label, style = type.propertyLabel, color = mediaLabel)
            }
        }
        val valueBlock: @Composable (Modifier) -> Unit = { valueModifier ->
            Column(valueModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    valueContent != null -> valueContent()
                    tone != null || variant == GeoBadgeVariant.TAG -> GeoBadge(value, tone ?: Tone.INFO, variant = variant)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(geoBreakableValue(value),
                                style = if (numeric) type.propertyValue.copy(fontFeatureSettings = "tnum") else type.propertyValue,
                                color = if (enabled) colors.text else colors.disabledText)
                        }
                        if (onClick != null && enabled) Icon(GeoIcons.chevronRight, null, Modifier.size(20.dp), tint = colors.iconSubtle)
                    }
                }
                supportingText?.let { Text(it, style = type.caption, color = mediaLabel) }
                if (!enabled) disabledReason?.let { Text(it, style = type.caption, color = colors.textSecondary) }
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labelContent(Modifier.fillMaxWidth())
                valueBlock(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                labelContent(Modifier.width(labelWidth).alignByBaseline())
                valueBlock(Modifier.weight(1f).alignByBaseline())
            }
        }
    }
}
