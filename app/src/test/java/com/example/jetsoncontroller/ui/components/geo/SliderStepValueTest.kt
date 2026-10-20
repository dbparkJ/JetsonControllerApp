package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderStepValueTest {
    @Test
    fun `temperature and storage thresholds move by one step`() {
        assertEquals(81f, sliderStepValue(80f + 1f, 40f..110f, 1f), 0f)
        assertEquals(79f, sliderStepValue(80f - 1f, 40f..110f, 1f), 0f)
        assertEquals(86f, sliderStepValue(85f + 1f, 50f..99f, 1f), 0f)
        assertEquals(84f, sliderStepValue(85f - 1f, 50f..99f, 1f), 0f)
    }

    @Test
    fun `dragging snaps relative to the range minimum`() {
        assertEquals(25f, sliderStepValue(26f, 15f..95f, 10f), 0f)
        assertEquals(35f, sliderStepValue(30f, 15f..95f, 10f), 0f)
        assertEquals(85f, sliderStepValue(84.7f, 50f..99f, 1f), 0f)
    }

    @Test
    fun `stepping at either end stays within the supported range`() {
        assertEquals(50f, sliderStepValue(49f, 50f..99f, 1f), 0f)
        assertEquals(99f, sliderStepValue(100f, 50f..99f, 1f), 0f)
        assertEquals(15f, sliderStepValue(5f, 15f..95f, 10f), 0f)
        assertEquals(95f, sliderStepValue(105f, 15f..95f, 10f), 0f)
    }

    @Test
    fun `fractional and negative ranges retain their exact steps`() {
        assertEquals(0.75f, sliderStepValue(0.7f, 0.25f..1.75f, 0.25f), 0f)
        assertEquals(1.25f, sliderStepValue(1.3f, 0.25f..1.75f, 0.25f), 0f)
        assertEquals(-1.5f, sliderStepValue(-1.4f, -2f..2f, 0.5f), 0f)
    }
}
