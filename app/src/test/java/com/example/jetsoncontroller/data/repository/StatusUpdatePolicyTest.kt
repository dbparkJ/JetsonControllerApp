package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.transport.TransportType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusUpdatePolicyTest {
    @Test
    fun `BLE status is accepted only while BLE is the active transport`() {
        assertTrue(shouldApplyBleStatus(TransportType.BLE))
        assertFalse(shouldApplyBleStatus(TransportType.WIFI_DIRECT))
        assertFalse(shouldApplyBleStatus(TransportType.LAN))
        assertFalse(shouldApplyBleStatus(null))
    }
}
