package com.example.jetsoncontroller.ui.components.geo.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.jetsoncontroller.ui.theme.GeoPalette
import org.junit.Assert.*
import org.junit.Test

class TabSelectedShapeTest {
    @Test fun `selected icon shape uses primary tint in all six palettes`() {
        GeoPalette.entries.forEach { palette ->
            assertEquals(palette.name, palette.colors.primaryTint, geoTabSelectedShapeColor(palette))
        }
    }

    @Test fun `light navy uses the same surface navigation contract`() {
        val fill = geoTabSelectedShapeColor(GeoPalette.NAVY_LIGHT)
        assertEquals(GeoPalette.NAVY_LIGHT.colors.primaryTint.toArgb(), fill.toArgb())
        assertEquals(1f, fill.alpha, 0f)
        assertEquals(GeoPalette.NAVY_LIGHT.colors.primaryTint, fill)
    }
}
