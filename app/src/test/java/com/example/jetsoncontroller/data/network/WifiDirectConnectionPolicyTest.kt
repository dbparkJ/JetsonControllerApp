package com.example.jetsoncontroller.data.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectConnectionPolicyTest {
    @Test
    fun `disconnected broadcast preserves an active negotiation`() {
        assertTrue(
            shouldPreservePendingWifiDirectConnection(
                connectingPeerAddress = "02:00:00:00:00:01",
                groupFormed = false
            )
        )
        assertFalse(
            shouldPreservePendingWifiDirectConnection(
                connectingPeerAddress = null,
                groupFormed = false
            )
        )
    }

    @Test
    fun `callbacks from an older attempt cannot clear a new attempt`() {
        assertFalse(
            wifiDirectAttemptIsCurrent(
                currentGeneration = 3,
                callbackGeneration = 2,
                connectingPeerAddress = "02:00:00:00:00:01",
                callbackPeerAddress = "02:00:00:00:00:01",
                connected = false
            )
        )
        assertTrue(
            wifiDirectAttemptIsCurrent(
                currentGeneration = 3,
                callbackGeneration = 3,
                connectingPeerAddress = "02:00:00:00:00:01",
                callbackPeerAddress = "02:00:00:00:00:01",
                connected = false
            )
        )
    }

    @Test
    fun `p2p interface address is available when connectivity manager hides its network`() {
        val owner = InetAddress.getByName("192.168.49.1")
        val mobile = InetAddress.getByName("192.168.49.71")

        assertEquals(
            mobile,
            selectWifiDirectInterfaceAddress(
                owner,
                listOf(
                    WifiDirectInterfaceAddressCandidate("wlan0", mobile, 24),
                    WifiDirectInterfaceAddressCandidate("p2p-wlan0-0", mobile, 24)
                )
            )
        )
    }

    @Test
    fun `non p2p and unrelated interface addresses are rejected`() {
        val owner = InetAddress.getByName("192.168.49.1")

        assertNull(
            selectWifiDirectInterfaceAddress(
                owner,
                listOf(
                    WifiDirectInterfaceAddressCandidate(
                        "wlan0",
                        InetAddress.getByName("192.168.49.71"),
                        24
                    ),
                    WifiDirectInterfaceAddressCandidate(
                        "p2p-wlan0-0",
                        InetAddress.getByName("192.168.50.71"),
                        24
                    )
                )
            )
        )
    }
}
