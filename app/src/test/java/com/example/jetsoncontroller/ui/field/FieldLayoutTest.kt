package com.example.jetsoncontroller.ui.field

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldLayoutTest {
    @Test fun clockAndCapacityShareARowOnlyWhenBothMeasuredValuesFit() {
        assertFalse(runClockStacks(364f, 1f, 192f, 96f))
        assertFalse(runClockStacks(304f, 1f, 192f, 96f))
        assertTrue(runClockStacks(303f, 1f, 192f, 96f))
        assertTrue(runClockStacks(364f, 1f, 224f, 160f))
    }

    @Test fun largerTextKeepsTheClockAndCapacityOnSeparateRows() {
        listOf(1.3f, 1.5f, 2f).forEach { scale ->
            assertTrue(runClockStacks(720f, scale, 192f * scale, 96f * scale))
        }
    }
}
