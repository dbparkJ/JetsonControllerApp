package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticConnectionPolicyTest {
    private val first = RegisteredDevice(
        deviceId = "9b58f0b4-70bd-4ddb-a9a8-d3e879d9d137",
        deviceName = "MMS-D137"
    )
    private val second = RegisteredDevice(
        deviceId = "9b58f0b4-70bd-4ddb-a9a8-d3e879d91234",
        deviceName = "MMS-1234"
    )

    @Test
    fun `single registered device is the safe automatic target`() {
        assertEquals(first.deviceId, automaticTargetDeviceId(null, listOf(first)))
        assertNull(automaticTargetDeviceId(null, listOf(first, second)))
        assertEquals(second.deviceId, automaticTargetDeviceId(second.deviceId, listOf(first, second)))
        assertNull(automaticTargetDeviceId("not-registered-yet", listOf(first)))
    }

    @Test
    fun `direct fallback only starts while disconnected or on BLE`() {
        assertTrue(allowsAutomaticDirectFallback(TransportState.Disconnected))
        assertTrue(allowsAutomaticDirectFallback(TransportState.Connected(TransportType.BLE)))
        assertFalse(allowsAutomaticDirectFallback(TransportState.Connected(TransportType.LAN)))
        assertFalse(allowsAutomaticDirectFallback(TransportState.Connected(TransportType.WIFI_DIRECT)))
    }

    @Test
    fun `same target LAN can replace wifi direct`() {
        assertTrue(allowsAutomaticLanUpgrade(TransportState.Disconnected))
        assertTrue(allowsAutomaticLanUpgrade(TransportState.Connected(TransportType.BLE)))
        assertTrue(allowsAutomaticLanUpgrade(TransportState.Connected(TransportType.WIFI_DIRECT)))
        assertFalse(allowsAutomaticLanUpgrade(TransportState.Connected(TransportType.LAN)))
    }

    @Test
    fun `wifi direct fallback requires one exact device name`() {
        val exact = WifiDirectPeer("MMS-D137", "aa:bb:cc:dd:ee:ff", 3)
        val unrelated = WifiDirectPeer("MMS-1234", "00:11:22:33:44:55", 3)
        assertEquals(exact, chooseAutomaticWifiDirectPeer(listOf(unrelated, exact), first.deviceId))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(exact, exact.copy(deviceAddress = "11:22:33:44:55:66")), first.deviceId))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(unrelated), first.deviceId))
    }

    @Test
    fun `three consecutive IP failures mark the device offline`() {
        assertFalse(ipConnectionIsOffline(2))
        assertTrue(ipConnectionIsOffline(3))
    }
}
