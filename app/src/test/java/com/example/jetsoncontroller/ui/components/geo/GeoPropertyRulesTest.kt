package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.*
import org.junit.Test

class GeoPropertyRulesTest {
    @Test fun `measured labels share a column with a 104 dp minimum and a forty percent cap`() {
        assertEquals(104f, geoPropertyLabelWidth(608f, 0f), .001f)
        assertEquals(104f, geoPropertyLabelWidth(608f, 80f), .001f)
        assertEquals(136f, geoPropertyLabelWidth(608f, 136f), .001f)
        assertEquals(243.2f, geoPropertyLabelWidth(608f, 400f), .001f)
        assertEquals(145.6f, geoPropertyLabelWidth(364f, 200f), .001f)
    }

    @Test fun `value space uses the measured column and the eight dp gap`() {
        val labelWidth = geoPropertyLabelWidth(300f, 132f)
        assertFalse(geoPropertyListStacks(1f, 300f - labelWidth - 8f, listOf(2)))
        assertFalse(geoPropertyListStacks(1f, 280f - geoPropertyLabelWidth(280f, 132f) - 8f, listOf(2)))
        assertTrue(geoPropertyListStacks(1f, 279f - geoPropertyLabelWidth(279f, 132f) - 8f, listOf(2)))
        // Below 260 dp the minimum wins; the list must stack instead of shrinking its labels.
        assertEquals(104f, geoPropertyLabelWidth(240f, 200f), .001f)
        assertTrue(geoPropertyListStacks(1f, 240f - 104f - 8f, listOf(2)))
    }

    @Test fun `all acceptance scales switch at one point even with ample width`() {
        listOf(1f to false, 1.1f to false, 1.3f to true, 1.5f to true, 2f to true).forEach { (scale, stacked) ->
            assertEquals("fontScale=$scale", stacked, geoPropertyListStacks(scale, 400f, listOf(1, 2)))
        }
    }

    @Test fun `a value can use exactly 160 dp and stacks below that boundary`() {
        assertFalse(geoPropertyListStacks(1f, 160f, listOf(2)))
        assertTrue(geoPropertyListStacks(1f, 159.99f, listOf(1)))
        assertTrue(geoPropertyListStacks(1f, 0f, emptyList()))
        assertTrue(geoPropertyListStacks(1f, -1f, listOf(1)))
    }

    @Test fun `one label beyond two lines switches every property in the list`() {
        assertFalse(geoPropertyListStacks(1.1f, 252f, listOf(1, 2, 1)))
        assertTrue(geoPropertyListStacks(1.1f, 252f, listOf(1, 3, 1)))
        assertFalse(geoPropertyListStacks(1f, 252f, emptyList()))
    }
}
