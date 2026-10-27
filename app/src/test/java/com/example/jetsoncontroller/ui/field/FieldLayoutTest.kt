package com.example.jetsoncontroller.ui.field

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.jetsoncontroller.ui.components.geo.GeoFigure

class FieldLayoutTest {
    @Test fun clockAndCapacityShareARowOnlyWhenBothMeasuredValuesFit() {
        assertFalse(runClockStacks(364f, 1f, 192f, 96f))
        assertFalse(runClockStacks(304f, 1f, 192f, 96f))
        assertTrue(runClockStacks(303f, 1f, 192f, 96f))
        assertTrue(runClockStacks(364f, 1f, 224f, 160f))
    }

    @Test fun largerTextKeepsTheClockAndCapacityOnSeparateRows() {
        listOf(1.3f, 1.5f, 2f).forEach { scale ->
            assertTrue(runClockStacks(720f, scale, 192f * scale, 96f * scale))
        }
    }

    @Test fun capacitySeparatesFormattedAndExplicitUnitsWithoutLosingZero() {
        assertEquals("12.8" to "GB", runFigureParts(GeoFigure("수집 용량", "12.8\u00a0GB")))
        assertEquals("12.8" to "GB", runFigureParts(GeoFigure("수집 용량", "12.8 GB")))
        assertEquals("0" to "B", runFigureParts(GeoFigure("수집 용량", "0", unit = "B")))
        assertEquals("1,204" to "개", runFigureParts(GeoFigure("파일", "1,204개")))
    }

    @Test fun unknownAndInvalidCapacityNeverExposeAUnit() {
        listOf(
            GeoFigure("수집 용량", null, unit = "GB"),
            GeoFigure("수집 용량", "미확인"),
            GeoFigure("수집 용량", "12.8", valid = false, unit = "GB")
        ).forEach { assertEquals("미확인" to "", runFigureParts(it)) }
    }

    @Test fun pendingStartHeadingUsesItsTargetInsteadOfRepeatingTheStatus() {
        val state = ActiveRunState.START_CHECKING
        assertEquals("북측 보조 구간", runHeadingTitle(state, "북측 보조 구간", "도로대장"))
        assertEquals("도로대장", runHeadingTitle(state, null, "도로대장"))
        assertEquals("도로대장", runHeadingTitle(state, " ", "도로대장"))
        assertEquals("수집 구간", runHeadingTitle(state, null, null))
        assertEquals("수집 구간", runHeadingTitle(state, " ", " "))
    }
}
