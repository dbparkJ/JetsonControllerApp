package com.example.jetsoncontroller.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiNetworkSelectionTest {
    @Test
    fun `automatic LAN requires an exact mobile and Jetson wifi match`() {
        assertFalse(wifiNetworksMatch("field-a", true, "field-b"))
        assertTrue(wifiNetworksMatch("field-a", true, "field-a"))
    }

    @Test
    fun `unknown wifi cannot trigger automatic LAN`() {
        assertFalse(wifiNetworksMatch(null, true, "field-a"))
        assertFalse(wifiNetworksMatch("field-a", false, null))
    }
}
