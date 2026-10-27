package com.example.jetsoncontroller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalGeoDarkTheme = compositionLocalOf { false }

internal data class GeoSystemBarAppearance(
    val statusBar: Color,
    val navigationBar: Color,
    val darkStatusIcons: Boolean,
    val darkNavigationIcons: Boolean
)

internal fun systemBarAppearance(palette: GeoPalette, navigationBarUsesSurface: Boolean, media: Boolean = false) = GeoSystemBarAppearance(
    statusBar = if (media) GeoFixedColors.mediaStage else palette.colors.canvas,
    navigationBar = if (media) GeoFixedColors.mediaStage else if (navigationBarUsesSurface) palette.colors.surface else palette.colors.canvas,
    darkStatusIcons = !media && !palette.isDark,
    darkNavigationIcons = !media && !palette.isDark
)

private data class SystemBarRequest(val surface: Boolean, val media: Boolean)

private class SystemBarRequests {
    var navigationSurfaces by mutableStateOf<Map<Any, SystemBarRequest>>(emptyMap())
}

private val LocalSystemBarRequests = staticCompositionLocalOf<SystemBarRequests?> { null }

/** R8 chrome borrows canvas/surface and their matching inks from the original six palettes. */
internal fun geoR8Colors(palette: GeoPalette): GeoColors = with(palette.colors) {
    copy(topBar = canvas, onTopBar = text, onTopBarIcon = textSecondary, topBarBorder = border,
        nav = surface, navSelected = primary, navUnselected = textSubtle, navBorder = Color.Transparent,
        navFocus = focus, chromeSuccess = success, chromeWarning = warning, chromeDanger = danger,
        chromeInfo = primary, chromePending = pending, chromeNeutral = neutral)
}

/** Shells/overlays declare the surface below their navigation inset; the theme owns the window. */
@Composable
fun GeoSystemBars(navigationBarUsesSurface: Boolean, media: Boolean = false) {
    val requests = LocalSystemBarRequests.current ?: return
    val token = remember { Any() }
    DisposableEffect(requests, token, navigationBarUsesSurface, media) {
        requests.navigationSurfaces = requests.navigationSurfaces + (token to SystemBarRequest(navigationBarUsesSurface, media))
        onDispose { requests.navigationSurfaces = requests.navigationSurfaces - token }
    }
}

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

/** Existing callers retain their signature; their resolved mode uses its saved palette. */
@Composable
fun JetsonControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val palette = if (view.isInEditMode) GeoPalette.defaultFor(darkTheme) else {
        val store = rememberAppearanceStore()
        val appearance = rememberAppearance(store)
        LaunchedEffect(store) { store.reconcile() }
        appearance.paletteForDarkTheme(darkTheme)
    }
    JetsonControllerTheme(palette = palette, content = content)
}

/** Explicit palette choice; the selected palette determines content mode and weights. */
@Composable
fun JetsonControllerTheme(
    palette: GeoPalette,
    navigationBarUsesSurface: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val type = GeoType.forPalette(palette)
    val reducedMotion = geoReducedMotion()
    val barRequests = remember { SystemBarRequests() }
    val request = barRequests.navigationSurfaces.values.lastOrNull()
    val bars = systemBarAppearance(palette, request?.surface ?: navigationBarUsesSurface, request?.media == true)
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.appearanceActivity()?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
                window.isNavigationBarContrastEnforced = false
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = bars.darkStatusIcons
                    isAppearanceLightNavigationBars = bars.darkNavigationIcons
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalSystemBarRequests provides barRequests,
        LocalGeoColors provides geoR8Colors(palette),
        LocalGeoPalette provides palette,
        LocalGeoDarkTheme provides palette.isDark,
        LocalGeoType provides type,
        LocalGeoReducedMotion provides reducedMotion,
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
