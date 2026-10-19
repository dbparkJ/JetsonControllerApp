package com.example.jetsoncontroller.ui.network

import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.data.network.WifiSecurity
import com.example.jetsoncontroller.data.transport.TransportType
import org.junit.Assert.*
import org.junit.Test

class WifiApplyTargetTest {
    private val accessPoint = WifiAccessPoint("GEON-2G", -50, WifiSecurity.PERSONAL)
    private val ready = NetworkSettingsUiState(deviceId = "A", connectionGeneration = 4,
        transportType = TransportType.LAN, ssid = accessPoint.ssid, selectedAccessPointSsid = accessPoint.ssid,
        password = "abcdefgh", accessPoints = listOf(accessPoint))

    @Test fun `apply binds device SSID hidden flag and connection generation`() {
        val target = requireNotNull(ready.applyTarget())
        assertTrue(ready.matches(target))
        assertFalse(ready.copy(deviceId = "B").matches(target))
        assertFalse(ready.copy(ssid = "Other").matches(target))
        assertFalse(ready.copy(hidden = true).matches(target))
        assertFalse(ready.copy(connectionGeneration = 5).matches(target))
        assertFalse(ready.copy(transportType = null).matches(target))
        assertFalse(ready.copy(sending = true).matches(target))
        assertFalse(target.toString().contains(ready.password))
    }

    @Test fun `personal networks need valid UTF8 password and network name lengths`() {
        assertNull(ready.copy(password = "가나다").wifiApplyUnavailableReason())
        assertNotNull(ready.copy(password = "가나").wifiApplyUnavailableReason())
        assertNotNull(ready.copy(password = "a".repeat(64)).wifiApplyUnavailableReason())
        assertNotNull(ready.copy(ssid = "가".repeat(11)).wifiApplyUnavailableReason())
        assertNotNull(ready.copy(ssid = "").wifiApplyUnavailableReason())
    }

    @Test fun `open hidden current and unsupported networks keep their distinct rules`() {
        assertNotNull(ready.copy(wifiConnected = true, currentWifiSsid = accessPoint.ssid).wifiApplyUnavailableReason())
        assertNotNull(ready.copy(accessPoints = listOf(accessPoint.copy(security = WifiSecurity.ENTERPRISE))).wifiApplyUnavailableReason())
        assertNotNull(ready.copy(accessPoints = listOf(accessPoint.copy(security = WifiSecurity.LEGACY_WEP))).wifiApplyUnavailableReason())
        assertNull(ready.copy(password = "", accessPoints = listOf(accessPoint.copy(security = WifiSecurity.OPEN))).wifiApplyUnavailableReason())
        assertNotNull(ready.copy(accessPoints = emptyList()).wifiApplyUnavailableReason())
        assertNull(ready.copy(deviceId = null).applyTarget())
        assertNull(ready.copy(selectedAccessPointSsid = null, hidden = true, password = "").wifiApplyUnavailableReason())
    }
}
