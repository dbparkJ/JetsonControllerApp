@file:Suppress("DEPRECATION")

package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class GeoTypeTest {
    @Test
    fun `reading roles match the R7 size line weight and tracking`() {
        val t = GeoType.standard
        assertRole(t.count, 13, 20, 600)
        assertRole(t.caption, 13, 20, 500)
        assertRole(t.label, 14, 20, 500)
        assertRole(t.body, 16, 24, 400)
        assertRole(t.titleSm, 16, 24, 600)
        assertRole(t.title, 18, 28, 700)
        assertRole(t.boardTitle, 24, 32, 700)
        assertRole(t.heading, 24, 32, 700)
        assertRole(t.button, 16, 24, 600)
        assertRole(t.input, 16, 24, 400)
        assertRole(t.mono, 13, 20, 400, monospace = true)
        assertEquals(FontWeight.SemiBold, t.badge.fontWeight)
        assertRole(t.sectionTitle, 18, 28, 700)
        assertRole(t.deviceName, 18, 28, 700)
        assertRole(t.propertyLabel, 14, 20, 500)
        assertRole(t.propertyValue, 16, 24, 400)
        assertRole(t.emptyTitle, 16, 24, 400)
        assertRole(t.inlineStatus, 14, 20, 500)
        assertRole(t.stateTitle, 16, 24, 600)
        assertRole(t.focusTitle, 22, 32, 700)
        assertEquals(t.badge, t.tag)
        assertEquals(FontWeight.SemiBold, t.tab.fontWeight)
        assertEquals(FontWeight.Bold, t.tabSelected.fontWeight)
        assertEquals("tnum", t.figure.fontFeatureSettings)
        assertRole(t.brandTitle, 32, 40, 700)
        assertRole(t.workTitle, 22, 32, 700)
        assertRole(t.figure, 28, 36, 700, -1f / 28f)
        assertRole(t.clock, 40, 48, 700)
        assertRole(t.filter, 13, 20, 600)
        assertRole(t.unit, 13, 20, 500)
    }

    @Test
    fun `all palettes keep R7 reading weights and material mappings`() {
        val standard = GeoType.standard
        val high = GeoType.forPalette(GeoPalette.HIGH_CONTRAST)
        assertEquals(standard, high)
        GeoPalette.entries.filterNot { it.highContrast }.forEach {
            assertSame(standard, GeoType.forPalette(it))
        }
        assertEquals(FontWeight.Medium, high.propertyLabel.fontWeight)
        assertEquals(FontWeight.Normal, high.propertyValue.fontWeight)
        assertEquals(FontWeight.Normal, high.emptyTitle.fontWeight)
        assertEquals(FontWeight.Bold, high.focusTitle.fontWeight)
        assertEquals(standard.sectionTitle, high.sectionTitle)
        assertEquals(standard.deviceName, high.deviceName)
        assertEquals(standard.inlineStatus, high.inlineStatus)
        val material = geoMaterialTypography(high)
        assertEquals(high.body, material.bodyLarge)
        assertEquals(high.body, material.bodyMedium)
        assertEquals(high.caption, material.bodySmall)
        assertEquals(high.titleSm, material.titleMedium)
    }

    private fun assertRole(
        style: TextStyle, size: Int, line: Int, weight: Int,
        trackingEm: Float = 0f, monospace: Boolean = false
    ) {
        assertEquals(size.toFloat(), style.fontSize.value, 0f)
        assertEquals(line.toFloat(), style.lineHeight.value, 0f)
        assertEquals(FontWeight(weight), style.fontWeight)
        assertEquals(size * trackingEm, style.letterSpacing.value, 0.00001f)
        assertEquals(if (monospace) FontFamily.Monospace else PretendardFamily, style.fontFamily)
        assertTrue(style.fontSize.isSp)
        assertTrue(style.lineHeight.isSp)
    }
}
