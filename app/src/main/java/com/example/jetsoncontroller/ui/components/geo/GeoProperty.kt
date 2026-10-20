package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

internal fun geoPropertyListStacks(fontScale: Float, valueWidthDp: Float, labelLineCounts: List<Int>): Boolean =
    fontScale >= 1.5f || valueWidthDp < 160f || labelLineCounts.any { it > 2 }

private val LocalPropertyStacked = staticCompositionLocalOf<Boolean?> { null }

/** Labels are measured together so one long label switches the entire list. */
@Composable
fun GeoPropertyList(
    labels: List<String>,
    modifier: Modifier = Modifier,
    hasIcons: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LocalGeoType.current.propertyLabel
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelWidth = with(density) { (if (hasIcons) 78.dp else 104.dp).roundToPx() }
        val lines = labels.map { measurer.measure(AnnotatedString(it), style,
            constraints = Constraints(maxWidth = labelWidth)).lineCount }
        val stacked = geoPropertyListStacks(density.fontScale, (maxWidth - 112.dp).value, lines)
        CompositionLocalProvider(LocalPropertyStacked provides stacked) {
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
    BoxWithConstraints(modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.contentTarget(enabled, onClick) else Modifier)
        .semantics(mergeDescendants = true) {}) {
        val lines = measurer.measure(AnnotatedString(label), type.propertyLabel,
            constraints = Constraints(maxWidth = with(density) { (if (icon == null) 104.dp else 78.dp).roundToPx() })).lineCount
        val stacked = listStacked ?: geoPropertyListStacks(density.fontScale, (maxWidth - 112.dp).value, listOf(lines))
        val labelContent: @Composable (Modifier) -> Unit = { labelModifier ->
            Row(labelModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                icon?.let { Icon(it, null, Modifier.size(18.dp), tint = colors.iconSubtle) }
                Text(label, style = type.propertyLabel, color = colors.textSubtle)
            }
        }
        val valueBlock: @Composable (Modifier) -> Unit = { valueModifier ->
            Column(valueModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    valueContent != null -> valueContent()
                    tone != null || variant == GeoBadgeVariant.TAG -> GeoBadge(value, tone ?: Tone.INFO, variant = variant)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(value, Modifier.weight(1f),
                            style = if (numeric) type.propertyValue.copy(fontFeatureSettings = "tnum") else type.propertyValue,
                            color = if (enabled) colors.text else colors.disabledText)
                        if (onClick != null && enabled) Icon(GeoIcons.chevronRight, null, Modifier.size(20.dp), tint = colors.iconSubtle)
                    }
                }
                supportingText?.let { Text(it, style = type.caption, color = colors.textSubtle) }
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
                labelContent(Modifier.width(104.dp))
                valueBlock(Modifier.weight(1f))
            }
        }
    }
}
