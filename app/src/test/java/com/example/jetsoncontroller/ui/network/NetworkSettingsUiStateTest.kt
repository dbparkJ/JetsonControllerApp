package com.example.jetsoncontroller.ui.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSettingsUiStateTest {
    @Test
    fun `current Jetson wifi requires connected state and exact ssid`() {
        val state = NetworkSettingsUiState(
            currentWifiSsid = "Lab Wi-Fi",
            wifiConnected = true
        )

        assertTrue(state.isCurrentJetsonWifi("Lab Wi-Fi"))
        assertFalse(state.isCurrentJetsonWifi("lab wi-fi"))
        assertFalse(state.copy(wifiConnected = false).isCurrentJetsonWifi("Lab Wi-Fi"))
    }
}
