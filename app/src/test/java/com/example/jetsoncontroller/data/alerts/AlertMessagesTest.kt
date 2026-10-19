package com.example.jetsoncontroller.data.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertMessagesTest {

    @Test
    fun `alert title names the device before the condition`() {
        assertEquals("MMS-4DE0 온도 높음", alertTitle("MMS-4DE0", "온도 높음"))
    }

    @Test
    fun `blank device name falls back to the neutral label`() {
        assertEquals("장치 온도 높음", alertTitle("   ", "온도 높음"))
        assertEquals("장치 온도 높음", alertTitle(null, "온도 높음"))
    }

    @Test
    fun `a UUID is never shown as the device name`() {
        val uuid = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        assertEquals("장치", readableDeviceName(uuid))
        assertEquals("장치 저장 공간 경고", alertTitle(uuid, "저장 공간 경고"))
    }

    @Test
    fun `temperature is formatted with one decimal and the degree sign`() {
        assertEquals("81.5°C", formatCelsius(81.5f))
        assertEquals("72.0°C", formatCelsius(72f))
    }

}
