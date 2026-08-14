package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.GnssSensorStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GnssMapPresentationTest {
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
