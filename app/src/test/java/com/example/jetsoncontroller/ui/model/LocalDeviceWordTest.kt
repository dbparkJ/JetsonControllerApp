package com.example.jetsoncontroller.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDeviceWordTest {
    @Test
    fun `uses phone below the tablet smallest-width breakpoint`() {
        assertEquals("휴대폰", localDeviceWord(0))
        assertEquals("휴대폰", localDeviceWord(599))
    }

    @Test
    fun `uses tablet at and above the smallest-width breakpoint`() {
        assertEquals("태블릿", localDeviceWord(600))
        assertEquals("태블릿", localDeviceWord(840))
    }
}
