package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.*
import org.junit.Test

class GeoSectionRulesTest {
    @Test fun `the first section has no divider or section spacing even when dangerous`() {
        listOf(false, true).forEach { danger ->
            assertEquals(GeoSectionRule(false, 0, 0), geoSectionRule(first = true, danger = danger))
        }
    }

    @Test fun `subsequent sections leave 16 dp on either side of their line`() {
        assertEquals(GeoSectionRule(true, 16, 16), geoSectionRule(first = false, danger = false))
    }

    @Test fun `danger adds eight dp before the line and keeps the heading gap`() {
        assertEquals(GeoSectionRule(true, 24, 16), geoSectionRule(first = false, danger = true))
    }
}
