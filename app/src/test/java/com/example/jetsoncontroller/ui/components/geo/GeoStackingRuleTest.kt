package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoStackingRuleTest {
    @Test
    fun `equal shares stay in a row when every measured label fits`() {
        assertTrue(geoActionsFit(364, listOf(120, 178), 8, GeoActionLayout.EqualShares))
    }

    @Test
    fun `an exact fit stays in a row`() {
        assertTrue(geoActionsFit(364, listOf(178, 178), 8, GeoActionLayout.EqualShares))
        assertFalse(geoActionsFit(363, listOf(178, 178), 8, GeoActionLayout.EqualShares))
    }

    @Test
    fun `one wide label stacks even when the total width fits`() {
        assertFalse(geoActionsFit(364, listOf(48, 200), 8, GeoActionLayout.EqualShares))
    }

    @Test
    fun `dialog actions use natural widths`() {
        assertTrue(geoActionsFit(364, listOf(48, 300), 8, GeoActionLayout.Dialog))
        assertTrue(geoActionsFit(356, listOf(48, 300), 8, GeoActionLayout.Dialog))
        assertFalse(geoActionsFit(355, listOf(48, 300), 8, GeoActionLayout.Dialog))
    }

    @Test
    fun `font scaling is reflected by measurement without a font scale cutoff`() {
        assertTrue(geoActionsFit(364, listOf(120, 140), 8, GeoActionLayout.EqualShares))
        assertFalse(geoActionsFit(364, listOf(208, 248), 8, GeoActionLayout.EqualShares))
        assertTrue(geoActionsFit(720, listOf(208, 248), 8, GeoActionLayout.EqualShares))
    }

    @Test
    fun `icon spinner padding and target widths are part of each requirement`() {
        assertTrue(geoActionsFit(240, listOf(48 + 32 + 30, 48 + 32 + 26), 8, GeoActionLayout.EqualShares))
        assertFalse(geoActionsFit(220, listOf(48 + 32 + 30, 48 + 32 + 26), 8, GeoActionLayout.EqualShares))
    }

    @Test
    fun `all inter-button gaps count for three actions`() {
        assertTrue(geoActionsFit(316, listOf(100, 100, 100), 8, GeoActionLayout.EqualShares))
        assertFalse(geoActionsFit(315, listOf(100, 100, 100), 8, GeoActionLayout.EqualShares))
        assertTrue(geoActionsFit(316, listOf(100, 100, 100), 8, GeoActionLayout.Dialog))
    }

    @Test
    fun `empty and single action groups have no artificial gap`() {
        assertTrue(geoActionsFit(0, emptyList(), 8, GeoActionLayout.EqualShares))
        assertTrue(geoActionsFit(100, listOf(100), 8, GeoActionLayout.EqualShares))
        assertFalse(geoActionsFit(99, listOf(100), 8, GeoActionLayout.Dialog))
    }

    @Test
    fun `invalid or insufficient measurements cannot fit`() {
        assertFalse(geoActionsFit(0, listOf(48, 48), 8, GeoActionLayout.EqualShares))
        assertFalse(geoActionsFit(-1, listOf(48), 8, GeoActionLayout.EqualShares))
        assertFalse(geoActionsFit(100, listOf(-1), 8, GeoActionLayout.Dialog))
        assertFalse(geoActionsFit(100, listOf(48), -1, GeoActionLayout.Dialog))
    }

    @Test
    fun `unbounded measurements do not overflow`() {
        assertFalse(geoActionsFit(Int.MAX_VALUE, listOf(Int.MAX_VALUE, Int.MAX_VALUE), 8, GeoActionLayout.EqualShares))
        assertFalse(geoActionsFit(Int.MAX_VALUE, listOf(Int.MAX_VALUE, Int.MAX_VALUE), 8, GeoActionLayout.Dialog))
    }
}
