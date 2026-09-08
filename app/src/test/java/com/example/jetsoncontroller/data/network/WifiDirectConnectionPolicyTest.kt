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
    fun `timeout or cancellation blocks retry until cleanup completes and rejects late success`() {
        val session = WifiDirectConnectionSession()
        val attempt = requireNotNull(session.begin("02:00:00:00:00:01"))
        val cleanup = requireNotNull(session.beginCleanup())

        assertNull(session.begin("02:00:00:00:00:02"))
        assertNull(session.beginCleanup())
        assertFalse(session.isConnecting(attempt))
        assertFalse(
            session.acceptGroup(attempt, "02:00:00:00:00:01", emptyList())
        )
        assertFalse(
            session.acceptGroup(cleanup, "02:00:00:00:00:01", emptyList())
        )
        assertTrue(session.finishCleanup(cleanup))

        val retry = requireNotNull(session.begin("02:00:00:00:00:02"))
        assertFalse(session.finishCleanup(cleanup))
        assertFalse(session.acceptGroup(attempt, "02:00:00:00:00:01", emptyList()))
        assertFalse(session.acceptGroup(retry, "02:00:00:00:00:01", emptyList()))
        assertTrue(session.isConnecting(retry))
        assertTrue(session.acceptGroup(retry, "02:00:00:00:00:02", emptyList()))
    }

    @Test
    fun `disconnect retains the target for cleanup and stale callbacks cannot clear reconnect`() {
        val session = WifiDirectConnectionSession()
        val attempt = requireNotNull(session.begin("02:00:00:00:00:ab"))
        assertTrue(session.acceptGroup(attempt, "02:00:00:00:00:AB", emptyList()))
        assertFalse(session.isConnecting(attempt))
        val cleanup = requireNotNull(session.beginCleanup())
        assertEquals("02:00:00:00:00:ab", session.peerAddress)
        assertTrue(wifiDirectGroupBelongsToPeer(session.peerAddress, null, listOf("02:00:00:00:00:AB")))
        assertFalse(wifiDirectGroupBelongsToPeer(session.peerAddress, "02:00:00:00:00:cd", emptyList()))
        assertFalse(wifiDirectGroupBelongsToPeer(null, "02:00:00:00:00:ab", emptyList()))

        // Cleanup completion and the deadline share this same guarded transition.
        assertTrue(session.finishCleanup(cleanup))
        val retry = requireNotNull(session.begin("02:00:00:00:00:ab"))
        assertFalse(session.isCleaningUp(cleanup))
        assertFalse(session.finishCleanup(cleanup))
        assertTrue(session.isConnecting(retry))
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
