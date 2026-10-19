package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class GeoStateKind { EMPTY, ERROR, MISSING, PERMISSION, OFFLINE, UNSUPPORTED }

data class GeoStateContent(
    val kind: GeoStateKind,
    val title: String,
    val explanation: String,
    val actionLabel: String? = null
)

@Composable
fun GeoStateBlock(
    state: GeoStateContent,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
    onAction: (() -> Unit)? = null
) {
    val colors = contentColors
    Column(modifier.fillMaxWidth().padding(top = if (fullScreen) 56.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(state.title, color = colors.text, style = ContentType.section, modifier = Modifier.semantics { heading() })
        Text(state.explanation, color = colors.secondary, style = ContentType.body)
        if (state.actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            StateAction(state.actionLabel, onAction)
        }
    }
}

// TODO(3-B merge): use the shared tertiary button.
@Composable
private fun StateAction(label: String, onClick: () -> Unit) {
    Box(Modifier.contentTarget(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(label, color = contentColors.primary, style = ContentType.button)
    }
}
