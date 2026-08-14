package com.example.jetsoncontroller.ui

import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationWorkflowTest {
    @Test
    fun `only the transport requested by the user completes connection navigation`() {
        val ble = TransportState.Connected(type = TransportType.BLE)
        val direct = TransportState.Connected(type = TransportType.WIFI_DIRECT)

        assertFalse(connectionAttemptCompleted(null, ble))
        assertFalse(connectionAttemptCompleted(TransportType.WIFI_DIRECT, ble))
        assertTrue(connectionAttemptCompleted(TransportType.WIFI_DIRECT, direct))
    }

    @Test
    fun `existing LAN connection does not reopen dashboard after back navigation`() {
        val lan = TransportState.Connected(type = TransportType.LAN)

        assertFalse(connectionAttemptCompleted(null, lan))
    }
}
