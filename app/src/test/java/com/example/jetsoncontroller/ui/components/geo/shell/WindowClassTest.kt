package com.example.jetsoncontroller.ui.components.geo.shell

import org.junit.Assert.*
import org.junit.Test

class WindowClassTest {
    @Test
    fun `rail starts at 600 in any orientation and classes use current window`() {
        assertFalse(geoWindow(599f, 900f, 1f).rail)
        assertTrue(geoWindow(600f, 900f, 1f).rail)
        assertTrue(geoWindow(882f, 412f, 1f).rail)
        assertEquals(GeoWidthClass.MEDIUM, geoWindow(839f, 900f, 1f).widthClass)
        assertEquals(GeoWidthClass.EXPANDED, geoWindow(840f, 900f, 1f).widthClass)
        assertEquals(GeoWidthClass.COMPACT, geoWindow(412f, 440f, 1f).widthClass)
        assertFalse(geoWindow(900f, 900f, 1f, navigationVisible = false).rail)
    }

    @Test
    fun `gutters come from the pane after the rail and safe drawing`() {
        assertEquals(802f, geoWindow(882f, 412f, 1f).paneWidthDp, 0f)
        assertEquals(24f, paneGutter(390f), 0f)
        assertEquals(20f, paneGutter(389f), 0f)
        assertEquals(20f, paneGutter(340f), 0f)
        assertEquals(16f, paneGutter(339f), 0f)
        assertEquals(320f, geoWindow(320f, 640f, 2f).paneWidthDp, 0f)
    }

    @Test
    fun `two panes require expanded width measured minimums height and font scale`() {
        assertFalse(geoWindow(839f, 900f, 1f).twoPane)
        assertFalse(geoWindow(848f, 900f, 1f).twoPane)
        assertTrue(geoWindow(849f, 900f, 1f).twoPane)
        assertFalse(geoWindow(1200f, 479f, 1f).twoPane)
        assertTrue(geoWindow(1200f, 480f, 1f).twoPane)
        assertFalse(geoWindow(1200f, 900f, 1.5f).twoPane)
        assertFalse(geoWindow(1064f, 900f, 1.3f).twoPane)
        assertTrue(geoWindow(1065f, 900f, 1.3f).twoPane)
        assertFalse(geoWindow(849f, 900f, 1.1f).twoPane)
    }
}
