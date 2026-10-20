package com.example.jetsoncontroller.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertThresholdPresentationTest {
    @Test fun `storage thresholds retain whole percent steps and the supported boundaries`() {
        val range = 50..99
        assertEquals(50, alertThresholdValue(49f, range))
        assertEquals(99, alertThresholdValue(100f, range))
        assertEquals(85, alertThresholdValue(85.49f, range))
        assertEquals(86, alertThresholdValue(85.5f, range))
    }

    @Test fun `temperature thresholds retain the device limits and one degree steps`() {
        val range = 40..110
        assertEquals(40, alertThresholdValue(39f, range))
        assertEquals(110, alertThresholdValue(111f, range))
        assertEquals(80, alertThresholdValue(80.49f, range))
        assertEquals(81, alertThresholdValue(80.5f, range))
    }
}
