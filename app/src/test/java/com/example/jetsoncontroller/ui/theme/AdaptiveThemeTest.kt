package com.example.jetsoncontroller.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveThemeTest {
    @Test fun `R8 chrome uses canvas surface and their semantic inks in six palettes`() {
        GeoPalette.entries.forEach { palette ->
            val raw = palette.colors
            val c = geoR8Colors(palette)
            assertEquals(raw.canvas, c.topBar)
            assertEquals(raw.text, c.onTopBar)
            assertEquals(raw.surface, c.nav)
            assertEquals(raw.primary, c.navSelected)
            assertEquals(raw.success, c.chromeSuccess)
            assertEquals(if (palette.highContrast) raw.borderStrong else raw.border, c.sectionDivider)
            assertEquals(0f, c.navBorder.alpha, 0f)
        }
    }
    @Test
    fun `R7 gutters remain 24 dp at every pane width`() {
        listOf(0f to 24f, 339.99f to 24f, 340f to 24f, 389.99f to 24f, 390f to 24f, 840f to 24f).forEach { (width, gutter) ->
            assertEquals(gutter, GeoSpace.gutterForPane(width).value, 0f)
        }
    }

    @Test
    fun `spacing shapes and minimums match the new scale`() {
        assertEquals(listOf(4f, 8f, 12f, 16f, 20f, 24f, 28f, 32f, 36f, 40f, 44f, 48f, 52f, 56f), listOf(
            GeoSpace.space1, GeoSpace.space2, GeoSpace.space3, GeoSpace.space4, GeoSpace.space5, GeoSpace.space6,
            GeoSpace.space7, GeoSpace.space8, GeoSpace.space9, GeoSpace.space10, GeoSpace.space11, GeoSpace.space12,
            GeoSpace.space13, GeoSpace.space14
        ).map { it.value })
        assertEquals(listOf(8f, 16f, 20f, 20f, 24f, 999f), listOf(
            GeoShape.sm, GeoShape.md, GeoShape.lg, GeoShape.xl, GeoShape.xxl, GeoShape.pillRadius
        ).map { it.value })
        assertEquals(48f, GeoSize.minTouchTarget.value, 0f)
        assertEquals(56f, GeoSize.primaryAction.value, 0f)
        assertEquals(16f, AppSpacing.cardInset.value, 0f)
        assertEquals(24f, AppSpacing.sectionGap.value, 0f)
        assertEquals(12f, AppSpacing.titleGap.value, 0f)
    }

    @Test
    fun `motion durations collapse to zero for reduced motion`() {
        assertEquals(listOf(120, 180, 240, 2400), listOf(GeoMotion.fast, GeoMotion.normal, GeoMotion.slow, GeoMotion.loaderCycle))
        listOf(GeoMotion.fast, GeoMotion.normal, GeoMotion.slow, GeoMotion.loaderCycle).forEach { duration ->
            assertEquals(duration, GeoMotion.duration(duration, false))
            assertEquals(0, GeoMotion.duration(duration, true))
        }
        assertEquals(24f, GeoMotion.screenShift.value, 0f)
        assertEquals(0f, GeoMotion.easing.transform(0f), 0f)
        assertEquals(1f, GeoMotion.easing.transform(1f), 0f)
    }

    @Test
    fun `Material scheme uses each selected palette without tonal substitutions`() {
        GeoPalette.entries.forEach { palette ->
            val c = palette.colors
            val scheme = geoScheme(palette)
            assertEquals(c.primary, scheme.primary)
            assertEquals(c.primaryTint, scheme.primaryContainer)
            assertEquals(c.primaryTintText, scheme.onPrimaryContainer)
            assertEquals(c.surface, scheme.surface)
            assertEquals(c.text, scheme.onSurface)
            assertEquals(c.borderStrong, scheme.outline)
            assertEquals(c.inverse, scheme.inverseSurface)
            assertEquals(c.inverseAction, scheme.inversePrimary)
            assertEquals(c.scrim, scheme.scrim)
            assertEquals(c.danger, scheme.error)
            assertTrue(c.mapPlate.alpha == 1f)
        }
    }

    @Test
    fun `every palette uses one text six percent shadow with no outline`() {
        assertEquals(0f, GeoElevation.none.value, 0f)
        GeoPalette.entries.forEach { palette ->
            val shadow = GeoElevation.shadow(palette)
            assertEquals(4f, shadow.offsetY.value, 0f)
            assertEquals(16f, shadow.blur.value, 0f)
            assertEquals(palette.colors.text.copy(alpha = .06f), shadow.color)
            assertEquals(0f, shadow.outline.alpha, 0f)
            assertEquals(shadow, GeoElevation.shadowSmall(palette))
            assertEquals(shadow, GeoElevation.shadowLarge(palette))
            assertEquals(shadow, GeoElevation.shadowSwitch(palette))
        }
    }
}
