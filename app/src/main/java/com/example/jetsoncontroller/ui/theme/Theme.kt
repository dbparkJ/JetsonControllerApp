package com.example.jetsoncontroller.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Bridges the GEO& semantic roles onto Material 3's scheme so that stock components
 * (dialogs, chips, text fields, navigation bar) inherit the product palette without
 * every call site restating colours.
 */
private fun geoScheme(dark: Boolean): ColorScheme {
    val c = if (dark) GeoDark else GeoLight
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val opposite = if (dark) GeoLight else GeoDark
    return base.copy(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.accent, onPrimaryContainer = c.onAccent,
        inversePrimary = opposite.primary,
        secondary = c.primary, onSecondary = c.onPrimary,
        secondaryContainer = c.brandSoft, onSecondaryContainer = c.onBrandSoft,
        tertiary = c.accentGreen, onTertiary = c.onPrimary,
        tertiaryContainer = c.sectionRaised, onTertiaryContainer = c.ink,
        background = c.canvas, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.sectionSoft, onSurfaceVariant = c.muted,
        surfaceTint = c.primary,
        inverseSurface = opposite.surface, inverseOnSurface = opposite.ink,
        surfaceDim = c.surfaceSunken,
        surfaceBright = c.surfaceRaised,
        surfaceContainerLowest = c.surface,
        surfaceContainerLow = c.sectionSoft,
        surfaceContainer = c.sectionBase,
        surfaceContainerHigh = c.sectionSoft,
        surfaceContainerHighest = c.sectionRaised,
        outline = c.controlBorder, outlineVariant = c.border,
        error = c.danger, onError = c.onDanger,
        errorContainer = c.dangerBg, onErrorContainer = c.danger,
        scrim = Color.Black
    )
}

/**
 * Corner radii are deliberately tighter than the previous 20/24/28dp set. Heavily
 * rounded surfaces read as a consumer app; this is an operations tool that has to look
 * trustworthy at a glance and pack dense status information without wasting the
 * corners of every row.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(GeoRadius.xs),
    small = RoundedCornerShape(GeoRadius.sm),
    medium = RoundedCornerShape(GeoRadius.md),
    large = RoundedCornerShape(GeoRadius.lg),
    extraLarge = RoundedCornerShape(GeoRadius.xl)
)

@Composable
fun JetsonControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = geoScheme(darkTheme)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The platform draws this app edge to edge; the system bar *colours* are
            // no longer settable from here, so only the icon appearance is configured
            // and every screen pads itself with the window insets.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalGeoColors provides if (darkTheme) GeoDark else GeoLight,
        // Separation comes from borders and explicit surface roles, not from tonal
        // tinting. Tonal elevation made "raised" surfaces drift toward the primary
        // hue, which fought the status colours.
        LocalTonalElevationEnabled provides false
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
