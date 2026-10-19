package com.example.jetsoncontroller.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveThemeTest {
    @Test
    fun `gutters follow measured pane width at both boundaries`() {
        listOf(0f to 16f, 339.99f to 16f, 340f to 20f, 389.99f to 20f, 390f to 24f, 840f to 24f).forEach { (width, gutter) ->
            assertEquals(gutter, GeoSpace.gutterForPane(width).value, 0f)
        }
    }

    @Test
    fun `spacing shapes and minimums match the new scale`() {
        assertEquals(listOf(2f, 4f, 6f, 8f, 10f, 12f, 14f, 16f, 20f, 24f, 28f, 32f, 40f, 56f), listOf(
            GeoSpace.space1, GeoSpace.space2, GeoSpace.space3, GeoSpace.space4, GeoSpace.space5, GeoSpace.space6,
            GeoSpace.space7, GeoSpace.space8, GeoSpace.space9, GeoSpace.space10, GeoSpace.space11, GeoSpace.space12,
            GeoSpace.space13, GeoSpace.space14
        ).map { it.value })
        assertEquals(listOf(6f, 10f, 12f, 14f, 20f, 999f), listOf(
            GeoShape.sm, GeoShape.md, GeoShape.lg, GeoShape.xl, GeoShape.xxl, GeoShape.pillRadius
        ).map { it.value })
        assertEquals(48f, GeoSize.minTouchTarget.value, 0f)
        assertEquals(52f, GeoSize.primaryAction.value, 0f)
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
    fun `dark overlays use white outlines and content has no elevation`() {
        assertEquals(0f, GeoElevation.none.value, 0f)
        val light = GeoElevation.shadowLarge(GeoPalette.STANDARD)
        val dark = GeoElevation.shadowLarge(GeoPalette.GRAPHITE)
        assertEquals(20f, light.offsetY.value, 0f)
        assertEquals(48f, light.blur.value, 0f)
        assertEquals(16f, dark.offsetY.value, 0f)
        assertEquals(40f, dark.blur.value, 0f)
        assertEquals(0.10f, dark.outline.alpha, 0.005f)
        assertEquals(0.07f, GeoElevation.shadowSmall(GeoPalette.GRAPHITE).outline.alpha, 0.005f)
    }
}
