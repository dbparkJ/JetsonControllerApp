package com.example.jetsoncontroller.data.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportPolicyTest {
    @Test
    fun `server upload can start only over Wi-Fi Direct`() {
        assertTrue(canStartServerUpload(TransportType.WIFI_DIRECT))
        assertFalse(canStartServerUpload(TransportType.LAN))
        assertFalse(canStartServerUpload(TransportType.BLE))
        assertFalse(canStartServerUpload(null))
    }

    @Test
    fun `LAN policy explains how to enable server upload`() {
        val message = serverUploadUnavailableMessage(TransportType.LAN)

        assertTrue(message.contains("LAN"))
        assertTrue(message.contains("Wi-Fi Direct"))
    }
}
