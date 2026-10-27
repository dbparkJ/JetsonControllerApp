package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    val colors = LocalGeoColors.current
    // fullScreen adds vertical breathing room; only a media stage centers the content column.
    Box(modifier.fillMaxWidth().geoGroupInset(), contentAlignment =
        if (LocalGeoMediaStage.current) androidx.compose.ui.Alignment.TopCenter else androidx.compose.ui.Alignment.TopStart) {
        CompositionLocalProvider(LocalGeoGroupInset provides false) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = if (fullScreen) 56.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.title, color = colors.text,
                    style = LocalGeoType.current.emptyStateTitle,
                    modifier = Modifier.semantics { heading() })
                Text(state.explanation, color = colors.textSecondary, style = LocalGeoType.current.supporting)
                if (state.actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(4.dp))
                    GeoButton(state.actionLabel, onAction, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                }
            }
        }
    }
}
