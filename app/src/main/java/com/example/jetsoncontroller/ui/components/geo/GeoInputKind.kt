package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

enum class GeoInputKind { TEXT, SERVER_ADDRESS, PORT, CODE, IDENTIFIER, SEARCH }

/** Server/ID/code fields never auto-correct or capitalize; IME action follows the form flow. */
fun geoKeyboardOptions(kind: GeoInputKind, imeAction: ImeAction = ImeAction.Done): KeyboardOptions =
    KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = kind == GeoInputKind.TEXT || kind == GeoInputKind.SEARCH,
        keyboardType = when (kind) {
            GeoInputKind.SERVER_ADDRESS -> KeyboardType.Uri
            GeoInputKind.PORT, GeoInputKind.CODE -> KeyboardType.Number
            GeoInputKind.IDENTIFIER -> KeyboardType.Ascii
            else -> KeyboardType.Text
        },
        imeAction = if (kind == GeoInputKind.SEARCH) ImeAction.Search else imeAction
    )
