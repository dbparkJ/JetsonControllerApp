package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.model.RoutePoint
import com.example.jetsoncontroller.ui.field.LocationQualityBand
import org.junit.Assert.assertEquals
import org.junit.Test

class QualityRouteStrokeTest {
    @Test fun `route never bridges invalid coordinates or segment boundaries`() {
        val route = listOf(
            RoutePoint(37.0, 127.0, 0, segment = 1, fixState = "FIXED", sensorState = "ACTIVE"),
            RoutePoint(37.1, 127.1, 1, segment = 1, fixState = "FLOAT", sensorState = "ACTIVE"),
            RoutePoint(Double.NaN, 127.2, 2, segment = 1, fixState = "FLOAT", sensorState = "ACTIVE"),
            RoutePoint(37.3, 127.3, 3, segment = 1, fixState = "FIXED", sensorState = "ACTIVE"),
            RoutePoint(37.4, 127.4, 4, segment = 2, fixState = "FIXED", sensorState = "ACTIVE"),
            RoutePoint(37.5, 127.5, 5, segment = 2, fixState = "NO_FIX", sensorState = "ACTIVE")
        )

        val strokes = qualityRouteStrokes(route)

        assertEquals(1, strokes.size)
        assertEquals(LocationQualityBand.APPROXIMATE, strokes[0].band)
        assertEquals(listOf(0L, 1L), strokes[0].points.map { it.timestamp })
    }
}
