package com.example.jetsoncontroller.ui.components.geo

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
    val colors = contentColors
    Column(modifier.fillMaxWidth().padding(top = if (first) 0.dp else if (danger) 28.dp else 24.dp)) {
        val titleContent: @Composable () -> Unit = {
            Text(title, color = colors.text, style = ContentType.section, modifier = Modifier.semantics { heading() })
        }
        val trailing: @Composable () -> Unit = {
            if (actionLabel != null && onAction != null) SectionAction(actionLabel, onAction)
            count?.let { Text(it, color = colors.secondary, style = ContentType.caption.copy(fontFeatureSettings = "tnum")) }
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
        note?.let { Text(it, color = colors.secondary, style = ContentType.caption) }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// TODO(3-B merge): use the shared tertiary button.
@Composable
private fun SectionAction(label: String, onClick: () -> Unit) {
    Box(Modifier.contentTarget(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(label, color = contentColors.primary, style = ContentType.button)
    }
}
