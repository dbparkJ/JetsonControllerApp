package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureGridRulesTest {
    @Test
    fun `R7 uses four equal columns at the design width`() {
        assertEquals(2, figureGridColumns(311f, 1f))
        assertEquals(4, figureGridColumns(312f, 1f))
        assertEquals(4, figureGridColumns(364f, 1.3f))
    }

    @Test
    fun `large text uses two then one column including a wide pane`() {
        assertEquals(4, figureGridColumns(720f, 1.49f))
        assertEquals(2, figureGridColumns(720f, 1.5f))
        assertEquals(1, figureGridColumns(720f, 2f))
    }

    @Test
    fun `contained padding is deducted exactly once before choosing columns`() {
        assertEquals(2, figureGridColumns(280f, 1f, contained = false))
        assertEquals(2, figureGridColumns(343f, 1f, contained = true))
        assertEquals(4, figureGridColumns(344f, 1f, contained = true))
    }

    @Test
    fun `narrow or zero width is one column`() {
        assertEquals(1, figureGridColumns(0f, 1f))
        assertEquals(1, figureGridColumns(120f, 1f, contained = true))
    }

    @Test
    fun `missing blank and invalid values show 미확인 without warning or stale facts`() {
        listOf(null, "", " ").forEach { value ->
            val shown = figurePresentation(GeoFigure("저장 공간", value, stale = true, exceededThreshold = "80°C"))
            assertEquals("미확인", shown.value)
            assertTrue(shown.unknown)
            assertFalse(shown.warning)
            assertNull(shown.detail)
            assertNull(shown.staleDetail)
        }
        assertEquals("미확인", figurePresentation(GeoFigure("온도", "54°C", valid = false)).value)
    }

    @Test
    fun `a legitimate zero is retained`() {
        val shown = figurePresentation(GeoFigure("사용률", "0%"))
        assertEquals("0%", shown.value)
        assertFalse(shown.unknown)
        assertFalse(shown.warning)
    }

    @Test
    fun `stale values retain their value and replace ordinary detail`() {
        val shown = figurePresentation(GeoFigure("온도", "54°C", stale = true, detail = "방금 갱신"))
        assertEquals("54°C", shown.value)
        assertEquals("마지막 값 기준", shown.detail)
        assertFalse(shown.unknown)
    }

    @Test
    fun `threshold keeps the value and supplies an explicit warning explanation`() {
        val shown = figurePresentation(GeoFigure("온도", "81°C", exceededThreshold = "80°C"))
        assertEquals("81°C", shown.value)
        assertTrue(shown.warning)
        assertFalse(shown.unknown)
        assertEquals("기준 80°C 초과", shown.detail)
    }

    @Test
    fun `stale and exceeded threshold are both visible`() {
        val shown = figurePresentation(GeoFigure("온도", "81°C", stale = true, exceededThreshold = "80°C"))
        assertEquals("기준 80°C 초과", shown.detail)
        assertEquals("마지막 값 기준", shown.staleDetail)
    }

    @Test
    fun `ordinary detail is preserved and a missing detail reserves an empty slot`() {
        assertEquals("57% 남음", figurePresentation(GeoFigure("저장 여유", "512 GB", detail = "57% 남음")).detail)
        assertNull(figurePresentation(GeoFigure("온도", "54°C")).detail)
        assertFalse(figurePresentation(GeoFigure("온도", "54°C", exceededThreshold = " ")).warning)
    }
}
