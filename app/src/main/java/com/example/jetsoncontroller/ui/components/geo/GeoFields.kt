package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun GeoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    disabledReason: String? = null,
    password: Boolean = false,
    numeric: Boolean = false,
    unit: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    require(enabled || !disabledReason.isNullOrBlank()) { "Disabled fields require a visible reason" }
    val c = LocalGeoColors.current
    var focused by remember { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val textColor = if (enabled) c.text else c.disabledText
    val outline = when {
        !enabled -> c.borderControl
        error != null -> c.danger
        focused -> c.primary
        else -> c.borderControl
    }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = geoLabelTextStyle(), color = c.text)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.hasFocus }
                .geoFocusRing(GeoControlShape, focused)
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            enabled = enabled, readOnly = readOnly,
            textStyle = geoInputTextStyle().copy(color = textColor),
            cursorBrush = SolidColor(c.primary),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            keyboardOptions = keyboardOptions.copy(keyboardType = when {
                password -> KeyboardType.Password
                numeric -> KeyboardType.Decimal
                else -> keyboardOptions.keyboardType
            }),
            keyboardActions = keyboardActions,
            visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation()
                else VisualTransformation.None,
            decorationBox = { input ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .background(if (enabled) c.field else c.disabledFill, GeoControlShape)
                        .border(if (enabled && (focused || error != null)) 2.dp else 1.dp, outline, GeoControlShape)
                        .padding(start = 16.dp, end = if (password) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 16.dp)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = geoInputTextStyle(), color = if (enabled) c.textSecondary else c.disabledText)
                        }
                        input()
                    }
                    if (unit != null) {
                        Text(unit, style = geoInputTextStyle(), color = textColor)
                    }
                    if (password) {
                        GeoIconButton(
                            if (passwordVisible) GeoIcons.eyeOff else GeoIcons.eye,
                            if (passwordVisible) "비밀번호 숨기기" else "비밀번호 표시",
                            onClick = { passwordVisible = !passwordVisible }, enabled = enabled
                        )
                    }
                }
            }
        )
        GeoFieldSupportingText(if (!enabled) disabledReason else error ?: helper, enabled && error != null)
    }
}

@Composable
fun GeoPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    helper: String? = null,
    disabledReason: String? = null
) {
    require(enabled || !disabledReason.isNullOrBlank()) { "Disabled fields require a visible reason" }
    val c = LocalGeoColors.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .onFocusChanged { focused = it.isFocused }
                .geoFocusRing(GeoControlShape, focused)
                .clip(GeoControlShape)
                .background(when {
                    !enabled -> c.disabledFill
                    pressed -> c.fieldPressed
                    else -> c.field
                })
                .border(1.dp, c.borderControl, GeoControlShape)
                .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (enabled) c.textSecondary else c.disabledText)
                Text(value, style = geoInputTextStyle(), color = if (enabled) c.text else c.disabledText)
            }
            Icon(GeoIcons.chevronDown, null, Modifier.size(20.dp), tint = if (enabled) c.textSecondary else c.disabledText)
        }
        GeoFieldSupportingText(if (enabled) helper else disabledReason)
    }
}

@Composable
private fun GeoFieldSupportingText(text: String?, isError: Boolean = false) {
    if (text == null) return
    val c = LocalGeoColors.current
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        if (isError) Icon(GeoIcons.danger, null, Modifier.padding(top = 2.dp).size(16.dp), tint = c.danger)
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (isError) c.danger else c.textSecondary)
    }
}
