package com.example.jetsoncontroller.ui.components.geo

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
    val tone: GeoOverlayTone,
    val primaryAction: GeoOverlayAction,
    val secondaryAction: GeoOverlayAction? = null
) {
    init { require(tone != GeoOverlayTone.SUCCESS) { "Success belongs in a receipt, not a Focus block" } }
}

/** The screen supplies the selected NextAction; this component never recomputes its precedence. */
@Composable
fun GeoFocusBlock(state: GeoFocusBlockState, modifier: Modifier = Modifier) {
    val t = OverlayAdapter.tokens
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = t.soft(state.tone), contentColor = t.text) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverlayAdapter.StatusIcon(state.tone, Modifier.padding(top = 2.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.title, style = OverlayAdapter.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(state.body, style = OverlayAdapter.body)
                }
            }
            if (!state.primaryAction.enabled && state.primaryAction.disabledReason != null) {
                Text(state.primaryAction.disabledReason, color = t.textSecondary, style = OverlayAdapter.body)
            }
            OverlayAdapter.Action(state.primaryAction, Modifier.fillMaxWidth(), OverlayButtonStyle.PRIMARY)
            state.secondaryAction?.let { secondary ->
                if (!secondary.enabled && secondary.disabledReason != null) {
                    Text(secondary.disabledReason, color = t.textSecondary, style = OverlayAdapter.body)
                }
                OverlayAdapter.Action(secondary)
            }
        }
    }
}
