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

/** The ring sits 2 dp outside the control, followed by the 4 dp halo (R3 §3.7). */
private fun Modifier.controlFocusRing(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val colors = LocalGeoColors.current
    onFocusChanged { focused = it.hasFocus }.drawWithContent {
        drawContent()
        if (focused) {
            val ringOffset = (GeoShape.focusOffset + GeoShape.focusRing / 2).toPx()
            val haloOffset = (GeoShape.focusOffset + GeoShape.focusRing + GeoShape.focusHalo / 2).toPx()
            val radius = GeoShape.md.toPx()
            drawRoundRect(colors.focusHalo, Offset(-haloOffset, -haloOffset),
                Size(size.width + haloOffset * 2, size.height + haloOffset * 2),
                CornerRadius(radius + haloOffset), style = Stroke(GeoShape.focusHalo.toPx()))
            drawRoundRect(colors.focus, Offset(-ringOffset, -ringOffset),
                Size(size.width + ringOffset * 2, size.height + ringOffset * 2),
                CornerRadius(radius + ringOffset), style = Stroke(GeoShape.focusRing.toPx()))
        }
    }
}

@Composable
fun geoTextFieldColors(): TextFieldColors {
    val c = LocalGeoColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.field,
        unfocusedContainerColor = c.field,
        focusedTextColor = c.text, unfocusedTextColor = c.text,
        focusedLabelColor = c.primary, unfocusedLabelColor = c.textSecondary,
        focusedPlaceholderColor = c.textSubtle, unfocusedPlaceholderColor = c.textSubtle,
        focusedBorderColor = c.focus, unfocusedBorderColor = c.borderStrong,
        errorBorderColor = c.danger, errorContainerColor = c.field,
        errorTextColor = c.text, errorLabelColor = c.danger, errorSupportingTextColor = c.danger,
        disabledBorderColor = c.disabledText, disabledTextColor = c.disabledText,
        disabledLabelColor = c.disabledText, disabledPlaceholderColor = c.disabledText,
        disabledContainerColor = c.disabledFill
    )
}

@Deprecated("Use geoTextFieldColors", ReplaceWith("geoTextFieldColors()"))
@Composable
fun slateTextFieldColors(): TextFieldColors = geoTextFieldColors()

/** Forward behavior unchanged; use opaque disabled roles and a visible focus indicator. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalGeoColors.current
    androidx.compose.material3.Button(
        onClick = onClick, modifier = modifier.controlFocusRing(), enabled = enabled,
        shape = shape, colors = colors.copy(disabledContainerColor = c.disabledFill, disabledContentColor = c.disabledText),
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
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = actionContentColor()),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalGeoColors.current
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier.controlFocusRing(), enabled = enabled,
        shape = shape, colors = colors.copy(disabledContainerColor = c.disabledFill, disabledContentColor = c.disabledText),
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
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalGeoColors.current
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick, modifier = modifier.controlFocusRing(), enabled = enabled,
        shape = shape, colors = colors.copy(disabledContainerColor = c.disabledFill, disabledContentColor = c.disabledText),
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
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = actionContentColor()),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalGeoColors.current
    androidx.compose.material3.TextButton(
        onClick = onClick, modifier = modifier.controlFocusRing(), enabled = enabled,
        shape = shape, colors = colors.copy(disabledContainerColor = c.disabledFill, disabledContentColor = c.disabledText),
        elevation = elevation, border = border, contentPadding = contentPadding,
        interactionSource = interactionSource, content = content
    )
}

@Composable
private fun actionContentColor(): Color {
    val c = LocalGeoColors.current
    return if (LocalContentColor.current == c.primaryTintText) c.primaryTintText else c.primary
}
