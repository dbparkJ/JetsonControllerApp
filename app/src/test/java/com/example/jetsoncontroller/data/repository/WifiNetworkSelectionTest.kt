package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.EndpointTransport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiNetworkSelectionTest {
    @Test
    fun `LAN eligibility is independent of redacted or different wifi names`() {
        assertTrue(isLanEndpointCandidate(endpoint("111.111.111.67")))
        assertTrue(isLanEndpointCandidate(endpoint("192.168.1.102")))
        assertTrue(isLanEndpointCandidate(endpoint("2001:db8::1234")))
    }

    @Test
    fun `P2P owner cannot be mislabeled as a LAN connection`() {
        assertFalse(isLanEndpointCandidate(endpoint("192.168.49.1"), "192.168.49.1"))
        assertTrue(isLanEndpointCandidate(endpoint("111.111.111.67"), "192.168.49.1"))
        assertFalse(isLanEndpointCandidate(endpoint("111.111.111.67").copy(transport = EndpointTransport.WIFI_DIRECT)))
    }

    @Test
    fun `LAN discovery rejects non-unicast and malformed endpoints`() {
        listOf("127.0.0.1", "::1", "0.0.0.0", "::", "224.0.0.251", "ff02::fb", "fe80::1234%wlan0", "jetson.local", "999.1.2.3")
            .forEach { assertFalse(it, isLanEndpointCandidate(endpoint(it))) }
        assertFalse(isLanEndpointCandidate(endpoint("111.111.111.67").copy(port = 0)))
        assertFalse(isLanEndpointCandidate(endpoint("111.111.111.67").copy(port = 65536)))
    }

    private fun endpoint(host: String) = DeviceEndpoint(
        deviceId = "device", displayName = "MMS", host = host,
        port = 8765, transport = EndpointTransport.LAN
    )
}
