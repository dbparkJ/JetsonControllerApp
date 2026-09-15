package com.example.jetsoncontroller.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveThemeTest {
    @Test
    fun `root theme modestly enlarges tablets without changing phone scale`() {
        assertEquals(1f, geoUiScaleForSmallestWidth(599), 0f)
        assertEquals(1.10f, geoUiScaleForSmallestWidth(600), 0f)
        assertEquals(1.10f, geoUiScaleForSmallestWidth(800), 0f)
    }
}
