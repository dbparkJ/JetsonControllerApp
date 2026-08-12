package com.example.jetsoncontroller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFF89F8DF),
    secondary = BlueSecondaryDark,
    secondaryContainer = Color(0xFF244C59),
    onSecondaryContainer = Color(0xFFBFEAF8),
    tertiary = AmberTertiaryDark,
    tertiaryContainer = Color(0xFF673B00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    surfaceContainer = Color(0xFF19211F),
    surfaceContainerLow = Color(0xFF111816),
    surfaceContainerHigh = Color(0xFF232C29),
    outlineVariant = Color(0xFF3F4A46),
    outline = DarkOutline,
    onBackground = DarkText,
    onSurface = DarkText
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2DF),
    onPrimaryContainer = Color(0xFF00382F),
    secondary = BlueSecondary,
    secondaryContainer = Color(0xFFC2E9F7),
    onSecondaryContainer = Color(0xFF082F3B),
    tertiary = AmberTertiary,
    tertiaryContainer = Color(0xFFFFDCB7),
    onTertiaryContainer = Color(0xFF2D1600),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    surfaceContainer = Color(0xFFEAF0ED),
    surfaceContainerLow = Color(0xFFF7FAF8),
    surfaceContainerHigh = Color(0xFFE1E9E5),
    outlineVariant = Color(0xFFBEC9C4),
    outline = LightOutline,
    onBackground = LightText,
    onSurface = LightText
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JetsonControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
