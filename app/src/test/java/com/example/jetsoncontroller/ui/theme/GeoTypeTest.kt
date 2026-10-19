@file:Suppress("DEPRECATION")

package com.example.jetsoncontroller.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class GeoTypeTest {
    @Test
    fun `eleven roles match R3 size line weight and tracking`() {
        val t = GeoType.standard
        assertRole(t.count, 12, 16, 600)
        assertRole(t.caption, 13, 20, 400, -0.003f)
        assertRole(t.label, 14, 20, 500)
        assertRole(t.body, 15, 24, 400, -0.003f)
        assertRole(t.titleSm, 16, 24, 500, -0.003f)
        assertRole(t.title, 18, 26, 600, -0.015f)
        assertRole(t.boardTitle, 22, 30, 700, -0.025f)
        assertRole(t.heading, 24, 32, 700, -0.025f)
        assertRole(t.button, 16, 20, 600)
        assertRole(t.input, 16, 24, 400)
        assertRole(t.mono, 13, 20, 400, monospace = true)
        assertEquals(FontWeight.SemiBold, t.badge.fontWeight)
        assertEquals(FontWeight.SemiBold, t.sectionTitle.fontWeight)
        assertEquals(FontWeight.Medium, t.tab.fontWeight)
        assertEquals(FontWeight.Bold, t.tabSelected.fontWeight)
        assertEquals("tnum", t.figure.fontFeatureSettings)
    }

    @Test
    fun `high contrast raises only the four specified reading weights`() {
        val standard = GeoType.standard
        val high = GeoType.forPalette(GeoPalette.HIGH_CONTRAST)
        assertEquals(standard.copy(
            caption = standard.caption.copy(fontWeight = FontWeight.Medium),
            label = standard.label.copy(fontWeight = FontWeight.SemiBold),
            body = standard.body.copy(fontWeight = FontWeight.Medium),
            titleSm = standard.titleSm.copy(fontWeight = FontWeight.SemiBold)
        ), high)
        GeoPalette.entries.filterNot { it.highContrast }.forEach {
            assertSame(standard, GeoType.forPalette(it))
        }
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
