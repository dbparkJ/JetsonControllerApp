package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Tone
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class GeoFocusBlockState(
    val title: String,
    val body: String,
    val tone: Tone,
    val primaryAction: GeoOverlayAction,
    val secondaryAction: GeoOverlayAction? = null
) {
    init { require(tone != Tone.SUCCESS) { "Success belongs in a receipt, not a Focus block" } }
}

/** The screen supplies the selected NextAction; this component never recomputes its precedence. */
@Composable
fun GeoFocusBlock(state: GeoFocusBlockState, modifier: Modifier = Modifier) {
    val t = LocalGeoColors.current
    Surface(modifier.fillMaxWidth(), shape = GeoShape.card, color = t.soft(state.tone), contentColor = t.text) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GeoToneIcon(state.tone, Modifier.padding(top = 2.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.title, style = LocalGeoType.current.focusTitle, modifier = Modifier.semantics { heading() })
                    Text(state.body, style = LocalGeoType.current.body)
                }
            }
            if (!state.primaryAction.enabled && state.primaryAction.disabledReason != null) {
                Text(state.primaryAction.disabledReason, color = t.textSecondary, style = LocalGeoType.current.body)
            }
            GeoOverlayButton(state.primaryAction, Modifier.fillMaxWidth(), GeoButtonStyle.Primary)
            state.secondaryAction?.let { secondary ->
                if (!secondary.enabled && secondary.disabledReason != null) {
                    Text(secondary.disabledReason, color = t.textSecondary, style = LocalGeoType.current.body)
                }
                GeoOverlayButton(secondary, edge = GeoButtonEdge.Start)
            }
        }
    }
}
