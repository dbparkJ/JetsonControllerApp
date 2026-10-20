package com.example.jetsoncontroller.ui.components.geo.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.jetsoncontroller.ui.theme.GeoPalette
import org.junit.Assert.*
import org.junit.Test

class TabSelectedShapeTest {
    @Test fun `selected icon shape uses primary tint in five palettes`() {
        GeoPalette.entries.filterNot { it == GeoPalette.NAVY_LIGHT }.forEach { palette ->
            assertEquals(palette.name, palette.colors.primaryTint, geoTabSelectedShapeColor(palette))
        }
    }

    @Test fun `light navy composites selected white at fourteen percent over the navy bar`() {
        val fill = geoTabSelectedShapeColor(GeoPalette.NAVY_LIGHT)
        assertEquals(Color(0xFF333A58).toArgb(), fill.toArgb())
        assertEquals(1f, fill.alpha, 0f)
        assertNotEquals(GeoPalette.NAVY_LIGHT.colors.primaryTint, fill)
    }
}
