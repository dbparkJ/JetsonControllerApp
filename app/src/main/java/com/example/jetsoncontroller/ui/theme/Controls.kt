package com.example.jetsoncontroller.ui.theme

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
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
