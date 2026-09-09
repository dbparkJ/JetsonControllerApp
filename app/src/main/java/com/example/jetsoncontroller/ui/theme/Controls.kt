package com.example.jetsoncontroller.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Draw inside the control bounds so parent clipping cannot cut off keyboard focus. */
private fun Modifier.controlFocusRing(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = LocalCobaltColors.current.focusRing
    val base = LocalCobaltColors.current.surface
    onFocusChanged { focused = it.hasFocus }.drawWithContent {
        drawContent()
        if (focused) {
            val inset = 3.dp.toPx()
            val ringSize = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
            // A neutral underlay keeps the ring distinct even on a filled primary button.
            drawRoundRect(base, Offset(inset, inset), ringSize, CornerRadius(5.dp.toPx()), style = Stroke(6.dp.toPx()))
            drawRoundRect(ring, Offset(inset, inset), ringSize, CornerRadius(5.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
fun slateTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LocalCobaltColors.current.surface,
    unfocusedContainerColor = LocalCobaltColors.current.surface,
    focusedBorderColor = LocalCobaltColors.current.focusRing,
    unfocusedBorderColor = LocalCobaltColors.current.controlBorder,
    disabledBorderColor = LocalCobaltColors.current.border,
    disabledTextColor = LocalCobaltColors.current.onDisabled,
    disabledLabelColor = LocalCobaltColors.current.onDisabled,
    disabledPlaceholderColor = LocalCobaltColors.current.onDisabled,
    disabledContainerColor = LocalCobaltColors.current.disabled
)

/** Forward behavior unchanged; use opaque disabled roles and a visible focus indicator. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalCobaltColors.current
    androidx.compose.material3.Button(
        onClick = onClick, modifier = modifier.controlFocusRing(), enabled = enabled,
        shape = shape, colors = colors.copy(disabledContainerColor = c.disabled, disabledContentColor = c.onDisabled),
        elevation = elevation, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content
    )
}

/** Forward behavior unchanged; use opaque disabled roles and a visible focus indicator. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = actionContentColor()),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalCobaltColors.current
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier.controlFocusRing(), enabled = enabled,
        shape = shape, colors = colors.copy(disabledContainerColor = c.disabled, disabledContentColor = c.onDisabled),
        elevation = elevation, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content
    )
}

/** Forward behavior unchanged; use opaque disabled roles and a visible focus indicator. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalCobaltColors.current
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick, modifier = modifier.controlFocusRing(), enabled = enabled,
        shape = shape, colors = colors.copy(disabledContainerColor = c.disabled, disabledContentColor = c.onDisabled),
        elevation = elevation, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content
    )
}

/** Forward behavior unchanged; use opaque disabled roles and a visible focus indicator. */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = actionContentColor()),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalCobaltColors.current
    androidx.compose.material3.TextButton(
        onClick = onClick, modifier = modifier.controlFocusRing(), enabled = enabled,
        shape = shape, colors = colors.copy(disabledContainerColor = c.disabled, disabledContentColor = c.onDisabled),
        elevation = elevation, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content
    )
}

@Composable
private fun actionContentColor(): Color {
    val c = LocalCobaltColors.current
    return if (LocalContentColor.current == c.onAccent) c.onAccent else c.primary
}
