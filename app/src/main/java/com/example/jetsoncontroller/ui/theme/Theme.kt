package com.example.jetsoncontroller.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalGeoDarkTheme = compositionLocalOf { false }

internal fun geoScheme(palette: GeoPalette): ColorScheme {
    val c = palette.colors
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.primaryTint, onPrimaryContainer = c.primaryTintText,
        inversePrimary = c.inverseAction,
        secondary = c.primary, onSecondary = c.onPrimary,
        secondaryContainer = c.primaryTint, onSecondaryContainer = c.primaryTintText,
        tertiary = c.primary, onTertiary = c.onPrimary,
        tertiaryContainer = c.primaryTint, onTertiaryContainer = c.primaryTintText,
        background = c.canvas, onBackground = c.text,
        surface = c.surface, onSurface = c.text,
        surfaceVariant = c.sunken, onSurfaceVariant = c.textSecondary,
        surfaceTint = c.primary,
        inverseSurface = c.inverse, inverseOnSurface = c.onInverse,
        surfaceDim = c.sunken, surfaceBright = c.surface,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.sunken,
        surfaceContainer = c.surface, surfaceContainerHigh = c.field,
        surfaceContainerHighest = c.fieldPressed,
        outline = c.borderStrong, outlineVariant = c.border,
        error = c.danger, onError = c.onDanger,
        errorContainer = c.dangerSoft, onErrorContainer = c.danger,
        scrim = c.scrim
    )
}

private val AppShapes = Shapes(
    extraSmall = GeoShape.checkbox,
    small = GeoShape.control,
    medium = GeoShape.card,
    large = GeoShape.dialog,
    extraLarge = GeoShape.sheet
)

/** Existing callers retain their signature and use the first-install defaults. */
@Composable
fun JetsonControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = JetsonControllerTheme(palette = GeoPalette.defaultFor(darkTheme), content = content)

/** Explicit palette choice; the selected palette determines content mode and weights. */
@Composable
fun JetsonControllerTheme(
    palette: GeoPalette,
    navigationBarUsesSurface: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val type = GeoType.forPalette(palette)
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !palette.lightStatusBarIcons
                    isAppearanceLightNavigationBars = if (navigationBarUsesSurface) {
                        !palette.lightSurfaceBarIcons
                    } else {
                        !palette.lightNavigationBarIcons
                    }
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalGeoColors provides palette.colors,
        LocalGeoPalette provides palette,
        LocalGeoDarkTheme provides palette.isDark,
        LocalGeoType provides type,
        LocalTonalElevationEnabled provides false
    ) {
        MaterialTheme(
            colorScheme = geoScheme(palette),
            typography = geoMaterialTypography(type),
            shapes = AppShapes,
            content = content
        )
    }
}
