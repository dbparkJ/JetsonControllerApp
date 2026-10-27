package com.example.jetsoncontroller.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMapLayoutTest {
    @Test fun legendUsesOneColumnForNarrowPanelsAndLargerText() {
        assertEquals(2, mapLegendColumns(364f, 1f))
        assertEquals(1, mapLegendColumns(272f, 1f))
        listOf(1.3f, 1.5f, 2f).forEach { assertEquals(1, mapLegendColumns(720f, it)) }
    }

    @Test fun summaryAndHandleNeverExceedTheExpandedSheet() {
        assertEquals(156f, mapSheetPeekHeight(800f, 128f), .01f)
        listOf(240f, 412f, 800f, 882f).forEach { viewport ->
            assertTrue(mapSheetPeekHeight(viewport, 2_000f) <= viewport * .85f)
        }
        assertEquals(0f, mapSheetPeekHeight(0f, 128f), .01f)
    }
}
