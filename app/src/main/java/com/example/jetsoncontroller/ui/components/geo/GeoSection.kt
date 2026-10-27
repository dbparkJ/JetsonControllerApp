package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer

internal data class GeoSectionRule(val divider: Boolean, val beforeLineDp: Int, val afterLineDp: Int)

internal fun geoSectionRule(first: Boolean, danger: Boolean): GeoSectionRule =
    if (first) GeoSectionRule(false, 0, 0) else GeoSectionRule(false, 24, 0)

/** Parents place sections consecutively; only this component owns the section gap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeoSection(
    title: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    danger: Boolean = false,
    count: String? = null,
    note: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val rule = geoSectionRule(first, danger)
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(rule.beforeLineDp.dp))
        if (rule.divider) {
            HorizontalDivider(thickness = 1.dp, color = colors.sectionDivider)
            Spacer(Modifier.height(rule.afterLineDp.dp))
        }
        val titleContent: @Composable () -> Unit = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = colors.text, style = type.sectionTitle, modifier = Modifier.semantics { heading() })
                count?.let { Text(it, color = colors.textSubtle,
                    style = type.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    modifier = Modifier.align(Alignment.CenterVertically)) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val titleWidth = measurer.measure(AnnotatedString(title), type.sectionTitle, softWrap = false).size.width
            val countWidth = count?.let { measurer.measure(AnnotatedString(it), type.badge, softWrap = false).size.width +
                with(density) { 6.dp.roundToPx() } } ?: 0
            val actionWidth = actionLabel?.takeIf { onAction != null }?.let {
                measurer.measure(AnnotatedString(it), type.button, softWrap = false).size.width + with(density) { 24.dp.roundToPx() }
            } ?: 0
            val hasAction = actionWidth > 0
            if (hasAction && (density.fontScale >= 1.5f ||
                    titleWidth + countWidth + actionWidth + with(density) { 8.dp.roundToPx() } > constraints.maxWidth)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    titleContent()
                    GeoButton(actionLabel!!, onAction!!, Modifier.align(Alignment.End), style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.End)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { titleContent() }
                    if (hasAction) GeoButton(actionLabel!!, onAction!!, Modifier.width(with(density) { actionWidth.toDp() }), style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.End)
                }
            }
        }
        note?.let { Text(it, color = colors.textSecondary, style = LocalGeoType.current.caption) }
        Spacer(Modifier.height(12.dp))
        val group = remember { GeoGroupContext() }
        CompositionLocalProvider(LocalGeoGroup provides group) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}
