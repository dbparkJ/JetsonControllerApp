package com.example.jetsoncontroller.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

internal fun localDeviceWord(smallestScreenWidthDp: Int): String =
    if (smallestScreenWidthDp >= 600) "태블릿" else "휴대폰"

@Composable
internal fun localDeviceWord(): String = localDeviceWord(LocalConfiguration.current.smallestScreenWidthDp)
