package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.GnssSensorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssMapPresentationTest {
    @Test
    fun sidePanelIsLimitedToWideLandscapeTabletsAtNormalFontScale() {
        assertTrue(mapUsesSidePanel(600, 1280f, 720f, 1f))
        assertFalse(mapUsesSidePanel(411, 900f, 411f, 1f))
        assertFalse(mapUsesSidePanel(600, 1280f, 720f, 2f))
        assertFalse(mapUsesSidePanel(600, 800f, 600f, 1f))
    }

    @Test
    fun coordinatesUseStableSevenDecimalFormatting() {
        assertEquals(
            "37.5012346, 127.0398765",
            gnssCoordinateText(
                GnssSensorStatus(latitude = 37.50123456, longitude = 127.03987654)
            )
        )
    }
}
