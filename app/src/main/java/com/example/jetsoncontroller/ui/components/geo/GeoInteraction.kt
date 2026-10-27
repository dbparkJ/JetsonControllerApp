package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.example.jetsoncontroller.ui.theme.GeoMotion
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.geoReducedMotion

internal fun geoPressedScale(pressed: Boolean, reducedMotion: Boolean): Float =
    if (pressed && !reducedMotion) 0.97f else 1f

/** Shared pointer-down colour and 120ms scale feedback; pass the control's interaction source. */
fun Modifier.geoPressFeedback(
    source: MutableInteractionSource,
    enabled: Boolean = true,
    primary: Boolean = false,
    background: Color = Color.Transparent,
    shape: Shape = RectangleShape,
    pressedColor: Color = Color.Unspecified
): Modifier = composed {
    val c = LocalGeoColors.current
    val pressed by source.collectIsPressedAsState()
    val reduced = geoReducedMotion()
    val scale by animateFloatAsState(geoPressedScale(enabled && pressed, reduced),
        GeoMotion.transition(GeoMotion.fast, reduced), label = "geoPress")
    val fill = if (enabled && pressed) {
        if (pressedColor != Color.Unspecified) pressedColor else if (primary) c.primaryPressed else c.fieldPressed
    } else background
    // No synthetic hover: touch and stylus never inherit a mouse hover state.
    graphicsLayer { scaleX = scale; scaleY = scale }.background(fill, shape)
}

/** Accessible neutral/primary click target with the common R8 press and keyboard focus feedback. */
fun Modifier.geoClickable(
    enabled: Boolean = true,
    primary: Boolean = false,
    shape: Shape = RectangleShape,
    background: Color = Color.Transparent,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val fallback = remember { MutableInteractionSource() }
    val source = interactionSource ?: fallback
    val focused by source.collectIsFocusedAsState()
    geoFocusRing(shape, focused).geoPressFeedback(source, enabled, primary, background = background, shape = shape)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
}

/** Tab/chip selection shares exactly the same press response as rows and buttons. */
fun Modifier.geoSelectable(
    selected: Boolean,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    background: Color = Color.Transparent,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val fallback = remember { MutableInteractionSource() }
    val source = interactionSource ?: fallback
    val focused by source.collectIsFocusedAsState()
    geoFocusRing(shape, focused).geoPressFeedback(source, enabled, background = background, shape = shape)
        .selectable(selected, source, indication = null, enabled = enabled, role = Role.Tab, onClick = onClick)
}
