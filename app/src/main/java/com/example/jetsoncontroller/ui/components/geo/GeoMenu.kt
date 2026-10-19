package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class GeoMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val disabledReason: String? = null
)

/** Place inside the opener's Box so the popup is anchored to it. */
@Composable
fun GeoMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<GeoMenuItem>,
    modifier: Modifier = Modifier,
    openerFocusRequester: FocusRequester? = null
) {
    val t = OverlayAdapter.tokens
    if (expanded) RestoreOverlayFocus(openerFocusRequester)
    DropdownMenu(
        expanded, onDismissRequest,
        modifier = modifier.widthIn(min = 200.dp, max = 280.dp),
        shape = RoundedCornerShape(12.dp), containerColor = t.surface,
        tonalElevation = 0.dp, shadowElevation = 16.dp
    ) {
        val normal = items.filterNot { it.destructive }
        val destructive = items.filter { it.destructive }
        normal.forEach { OverlayMenuItem(it, onDismissRequest) }
        if (normal.isNotEmpty() && destructive.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = t.border)
        }
        destructive.forEach { OverlayMenuItem(it, onDismissRequest) }
    }
}

@Composable
private fun OverlayMenuItem(item: GeoMenuItem, onDismissRequest: () -> Unit) {
    val t = OverlayAdapter.tokens
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(if (pressed) t.pressed else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, t.focus, RoundedCornerShape(2.dp)) else Modifier)
            .clickable(interactionSource = interactions, indication = null, enabled = item.enabled, role = Role.Button) {
                onDismissRequest()
                item.onClick()
            }.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(item.label, style = OverlayAdapter.body,
            color = if (!item.enabled) t.disabledText else if (item.destructive) t.danger else t.text)
        if (!item.enabled && item.disabledReason != null) {
            Text(item.disabledReason, color = t.textSecondary, style = OverlayAdapter.body)
        }
    }
}
