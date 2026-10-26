package com.example.jetsoncontroller.ui.components.geo.shell

import org.junit.Assert.*
import org.junit.Test

class LandscapeLayoutPolicyTest {
    @Test fun exhibitionTabletUsesLandscapeLayoutWithAndWithoutRail() {
        listOf(true, false).forEach { navigation ->
            val window = geoWindow(1280f, 776f, 1.1f, navigation)
            assertTrue(window.landscapeLayout)
            // Pane-local width and twoPane change; the window decision must survive.
            assertTrue(window.copy(paneWidthDp = 728f, gutterDp = 0f, twoPane = false).landscapeLayout)
        }
        assertEquals(GeoPaneWidths(520f, 608f), geoPaneWidths(geoWindow(1280f, 800f, 1.1f), GeoPanePolicy.WORK_HOME))
        assertEquals(GeoPaneWidths(400f, 728f), geoPaneWidths(geoWindow(1280f, 800f, 1.1f)))
    }

    @Test fun phonePortraitTabletAndTallWindowsKeepExistingLayout() {
        listOf(geoWindow(412f, 882f, 1.1f), geoWindow(882f, 412f, 1.1f),
            geoWindow(800f, 1280f, 1.1f), geoWindow(1200f, 1280f, 1f),
            geoWindow(1280f, 841f, 1f)).forEach { assertFalse(it.landscapeLayout) }
    }

    @Test fun exactHeightAndWidthBoundariesHonorExistingAccessibilityFallback() {
        assertFalse(geoWindow(1280f, 479f, 1.1f).landscapeLayout)
        assertTrue(geoWindow(1280f, 480f, 1.1f).landscapeLayout)
        assertTrue(geoWindow(1280f, 600f, 1.1f).landscapeLayout)
        assertTrue(geoWindow(1280f, 840f, 1.1f).landscapeLayout)
        assertFalse(geoLandscapeLayout(800f, 800f, true))
        assertFalse(geoLandscapeLayout(1280f, 800f, false))
        // At 1.1 with rail: 80 + 48 + 24 + (320 + 400) * 1.1 = 944dp.
        assertFalse(geoWindow(943f, 800f, 1.1f).landscapeLayout)
        assertTrue(geoWindow(944f, 800f, 1.1f).landscapeLayout)
        listOf(1.5f, 2f).forEach { assertFalse(geoWindow(1280f, 800f, it).landscapeLayout) }
        assertTrue(geoWindow(1280f, 800f, 1.3f).landscapeLayout)
    }

    @Test fun detailGridUsesItsMeasuredContentWidthAndFullSizedText() {
        assertEquals(2, geoDeviceDetailColumns(728f, 1.1f, true))
        assertEquals(1, geoDeviceDetailColumns(639f, 1.1f, true))
        assertEquals(2, geoDeviceDetailColumns(640f, 1.1f, true))
        assertEquals(1, geoDeviceDetailColumns(728f, 1.3f, true))
        assertEquals(2, geoDeviceDetailColumns(752f, 1.3f, true))
        assertEquals(1, geoDeviceDetailColumns(1000f, 1f, false))
        assertEquals(1, geoDeviceDetailColumns(583f, .8f, true))
        assertEquals(2, geoDeviceDetailColumns(584f, .8f, true))
    }
}
