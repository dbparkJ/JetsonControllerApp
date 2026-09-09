package com.example.jetsoncontroller.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private fun cobaltScheme(dark: Boolean): androidx.compose.material3.ColorScheme {
    val c = if (dark) CobaltDark else CobaltLight
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.accent, onPrimaryContainer = c.onAccent,
        inversePrimary = if (dark) CobaltLight.primary else CobaltDark.primary,
        secondary = c.info, onSecondary = c.infoBg,
        secondaryContainer = c.infoBg, onSecondaryContainer = c.info,
        tertiary = c.warning, onTertiary = c.warningBg,
        tertiaryContainer = c.warningBg, onTertiaryContainer = c.warning,
        background = c.canvas, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.subtle, onSurfaceVariant = c.muted,
        surfaceTint = c.primary, inverseSurface = c.ink, inverseOnSurface = c.canvas,
        surfaceDim = c.subtle, surfaceBright = c.surface,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.subtle,
        surfaceContainerHighest = c.subtle,
        outline = c.muted, outlineVariant = c.border,
        error = c.danger, onError = c.dangerBg,
        errorContainer = c.dangerBg, onErrorContainer = c.danger,
        scrim = Color.Black
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JetsonControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = cobaltScheme(darkTheme)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.surface.toArgb()
            window.navigationBarDividerColor = colorScheme.surface.toArgb()
            window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalCobaltColors provides if (darkTheme) CobaltDark else CobaltLight) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
}
