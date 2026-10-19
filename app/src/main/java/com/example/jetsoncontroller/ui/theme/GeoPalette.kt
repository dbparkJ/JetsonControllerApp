package com.example.jetsoncontroller.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Palette identity only; persistence and the picker belong to Phase 4-B. */
enum class GeoPalette(val label: String, val isDark: Boolean) {
    STANDARD("표준", false),
    NAVY_LIGHT("네이비 (밝게)", false),
    HIGH_CONTRAST("고대비", false),
    GRAPHITE("그래파이트", true),
    NAVY_DARK("네이비 (어둡게)", true),
    MIST("흐림", true);

    val colors: GeoColors
        get() = when (this) {
            STANDARD -> GeoStandard
            NAVY_LIGHT -> GeoNavyLight
            HIGH_CONTRAST -> GeoHighContrast
            GRAPHITE -> GeoGraphite
            NAVY_DARK -> GeoNavyDark
            MIST -> GeoMist
        }

    val highContrast: Boolean get() = this == HIGH_CONTRAST
    val lightStatusBarIcons: Boolean get() = isDark || this == NAVY_LIGHT
    val lightNavigationBarIcons: Boolean get() = lightStatusBarIcons
    val lightSurfaceBarIcons: Boolean get() = isDark

    companion object {
        fun defaultFor(darkTheme: Boolean): GeoPalette = if (darkTheme) GRAPHITE else STANDARD

    }
}

val LocalGeoPalette = staticCompositionLocalOf { GeoPalette.STANDARD }
