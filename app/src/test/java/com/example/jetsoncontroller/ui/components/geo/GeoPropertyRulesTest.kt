package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.*
import org.junit.Test

class GeoPropertyRulesTest {
    @Test fun `all acceptance scales switch at one point even with ample width`() {
        listOf(1f to false, 1.1f to false, 1.3f to false, 1.5f to true, 2f to true).forEach { (scale, stacked) ->
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
        assertFalse(geoPropertyListStacks(1.3f, 252f, listOf(1, 2, 1)))
        assertTrue(geoPropertyListStacks(1.3f, 252f, listOf(1, 3, 1)))
        assertFalse(geoPropertyListStacks(1f, 252f, emptyList()))
    }
}
