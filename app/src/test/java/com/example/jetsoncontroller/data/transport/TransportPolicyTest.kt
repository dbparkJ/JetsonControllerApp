package com.example.jetsoncontroller.data.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportPolicyTest {
    @Test
    fun `server upload can start only over LAN`() {
        assertTrue(canStartServerUpload(TransportType.LAN))
        assertFalse(canStartServerUpload(TransportType.WIFI_DIRECT))
        assertFalse(canStartServerUpload(TransportType.BLE))
        assertFalse(canStartServerUpload(null))
    }

    @Test
    fun `Wi-Fi Direct policy explains how to enable server upload`() {
        val message = serverUploadUnavailableMessage(TransportType.WIFI_DIRECT)

        assertTrue(message.contains("Wi-Fi Direct"))
        assertTrue(message.contains("LAN"))
    }
}
