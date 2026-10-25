package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.*
import org.junit.Test

class GeoSectionRulesTest {
    @Test fun `the first section has no divider or section spacing even when dangerous`() {
        listOf(false, true).forEach { danger ->
            assertEquals(GeoSectionRule(false, 0, 0), geoSectionRule(first = true, danger = danger))
        }
    }

    @Test fun `subsequent sections use 24 dp without a repeated border`() {
        assertEquals(GeoSectionRule(false, 24, 0), geoSectionRule(first = false, danger = false))
    }

    @Test fun `danger preserves the same section rhythm`() {
        assertEquals(GeoSectionRule(false, 24, 0), geoSectionRule(first = false, danger = true))
    }
}
