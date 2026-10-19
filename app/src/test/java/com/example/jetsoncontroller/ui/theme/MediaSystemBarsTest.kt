package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class MediaSystemBarsTest {
    @Test fun mediaUsesBlackSystemAreasAndLightIconsInEveryPalette() {
        GeoPalette.entries.forEach { palette ->
            val media = systemBarAppearance(palette, navigationBarUsesSurface = true, media = true)
            assertEquals(Color.Black, media.statusBar)
            assertEquals(Color.Black, media.navigationBar)
            assertFalse(media.darkStatusIcons)
            assertFalse(media.darkNavigationIcons)
            assertEquals(palette.colors.topBar, systemBarAppearance(palette, false).statusBar)
            assertEquals(palette.colors.nav, systemBarAppearance(palette, false).navigationBar)
        }
    }
}
