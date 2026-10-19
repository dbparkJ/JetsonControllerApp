package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Parents place sections consecutively; only this component owns the section gap. */
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
    require(count == null || actionLabel == null) { "A section has a count or an action" }
    val colors = LocalGeoColors.current
    Column(modifier.fillMaxWidth().padding(top = if (first) 0.dp else if (danger) 28.dp else 24.dp)) {
        val titleContent: @Composable () -> Unit = {
            Text(title, color = colors.text, style = LocalGeoType.current.sectionTitle, modifier = Modifier.semantics { heading() })
        }
        val trailing: @Composable () -> Unit = {
            if (actionLabel != null && onAction != null) GeoButton(actionLabel, onAction, style = GeoButtonStyle.Tertiary)
            count?.let { Text(it, color = colors.textSecondary, style = LocalGeoType.current.caption.copy(fontFeatureSettings = "tnum")) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { titleContent(); trailing() }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { titleContent() }
                    Box(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * 0.5f)) { trailing() }
                }
            }
        }
        note?.let { Text(it, color = colors.textSecondary, style = LocalGeoType.current.caption) }
        Spacer(Modifier.height(8.dp))
        content()
    }
}
