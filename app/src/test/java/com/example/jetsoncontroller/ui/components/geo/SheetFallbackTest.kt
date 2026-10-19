package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetFallbackTest {
    @Test
    fun exactly160DpOfBodyKeepsTheSheet() {
        assertFalse(sheetNeedsFullScreen(440f, 92f, 92f, 96f))
        assertTrue(sheetNeedsFullScreen(439.9f, 92f, 92f, 96f))
    }

    @Test
    fun portraitFitsButCompactLandscapeFallsBack() {
        assertFalse(sheetNeedsFullScreen(882f, 144f, 132f, 84f))
        assertTrue(sheetNeedsFullScreen(412f, 144f, 132f, 84f))
    }

    @Test
    fun measuredTextGrowthAndStackedFooterCanTriggerFallback() {
        assertFalse(sheetNeedsFullScreen(640f, 120f, 76f, 84f))
        assertTrue(sheetNeedsFullScreen(640f, 240f, 172f, 84f))
    }

    @Test
    fun keyboardIsAccountedForOnceInTheAvailableViewport() {
        assertFalse(sheetNeedsFullScreen(780f, 140f, 76f, 84f))
        assertTrue(sheetNeedsFullScreen(780f - 340f, 140f, 76f, 84f))
    }

    @Test
    fun bodyContentLengthDoesNotDecideFallback() {
        // No content-height input: a short choice list still has 300 dp available.
        assertFalse(sheetNeedsFullScreen(500f, 120f, 80f))
        assertTrue(sheetNeedsFullScreen(200f, 240f, 80f))
    }

    @Test
    fun dialogActionsFitAtNaturalWidthsAndOtherwiseStack() {
        assertTrue(overlayActionsFit(listOf(64, 128), 200, 8, equal = false))
        assertFalse(overlayActionsFit(listOf(64, 128), 199, 8, equal = false))
    }

    @Test
    fun sheetActionsMustEachFitTheirEqualShare() {
        assertTrue(overlayActionsFit(listOf(64, 128), 264, 8, equal = true))
        assertFalse(overlayActionsFit(listOf(64, 128), 263, 8, equal = true))
        assertFalse(overlayActionsFit(listOf(64, 128), 200, 8, equal = true))
    }
}
