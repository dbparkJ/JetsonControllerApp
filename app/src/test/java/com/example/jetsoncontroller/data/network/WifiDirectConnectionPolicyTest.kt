package com.example.jetsoncontroller.data.network

import android.net.NetworkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectConnectionPolicyTest {
    @Test
    fun `negotiating states preserve the pending connection`() {
        val connectingStates = listOf(
            NetworkInfo.DetailedState.CONNECTING,
            NetworkInfo.DetailedState.AUTHENTICATING,
            NetworkInfo.DetailedState.OBTAINING_IPADDR,
            NetworkInfo.DetailedState.DISCONNECTING
        )

        connectingStates.forEach { detailedState ->
            assertEquals(
                WifiDirectLinkPhase.CONNECTING,
                wifiDirectLinkPhase(isConnected = false, detailedState = detailedState)
            )
        }
    }

    @Test
    fun `terminal states clear the pending connection`() {
        assertEquals(
            WifiDirectLinkPhase.DISCONNECTED,
            wifiDirectLinkPhase(
                isConnected = false,
                detailedState = NetworkInfo.DetailedState.DISCONNECTED
            )
        )
        assertEquals(
            WifiDirectLinkPhase.DISCONNECTED,
            wifiDirectLinkPhase(isConnected = false, detailedState = null)
        )
        assertEquals(
            WifiDirectLinkPhase.CONNECTED,
            wifiDirectLinkPhase(
                isConnected = true,
                detailedState = NetworkInfo.DetailedState.DISCONNECTED
            )
        )
    }

    @Test
    fun `an early disconnected broadcast does not restart group negotiation`() {
        assertEquals(
            true,
            shouldPreservePendingWifiDirectConnection(
                connectingPeerAddress = "02:00:00:00:00:01",
                phase = WifiDirectLinkPhase.DISCONNECTED
            )
        )
        assertEquals(
            false,
            shouldPreservePendingWifiDirectConnection(
                connectingPeerAddress = null,
                phase = WifiDirectLinkPhase.DISCONNECTED
            )
        )
    }

    @Test
    fun `initial disconnected broadcast preserves active discovery`() {
        assertTrue(
            shouldPreserveActiveWifiDirectDiscovery(
                discovering = true,
                preparing = false,
                phase = WifiDirectLinkPhase.DISCONNECTED
            )
        )
        assertTrue(
            shouldPreserveActiveWifiDirectDiscovery(
                discovering = false,
                preparing = true,
                phase = WifiDirectLinkPhase.DISCONNECTED
            )
        )
        assertFalse(
            shouldPreserveActiveWifiDirectDiscovery(
                discovering = false,
                preparing = false,
                phase = WifiDirectLinkPhase.DISCONNECTED
            )
        )
        assertFalse(
            shouldPreserveActiveWifiDirectDiscovery(
                discovering = true,
                preparing = false,
                phase = WifiDirectLinkPhase.CONNECTED
            )
        )
    }

    @Test
    fun `peer polling survives a missed peers changed broadcast`() {
        assertTrue(
            shouldContinueWifiDirectPeerPolling(
                currentGeneration = 4,
                callbackGeneration = 4,
                discovering = true,
                connected = false,
                connectingPeerAddress = null,
                attempt = 10,
                maxAttempts = 120
            )
        )
        assertFalse(
            shouldContinueWifiDirectPeerPolling(
                currentGeneration = 5,
                callbackGeneration = 4,
                discovering = true,
                connected = false,
                connectingPeerAddress = null,
                attempt = 10,
                maxAttempts = 120
            )
        )
        assertFalse(
            shouldContinueWifiDirectPeerPolling(
                currentGeneration = 4,
                callbackGeneration = 4,
                discovering = true,
                connected = false,
                connectingPeerAddress = "02:00:00:00:00:01",
                attempt = 10,
                maxAttempts = 120
            )
        )
        assertFalse(
            shouldContinueWifiDirectPeerPolling(
                currentGeneration = 4,
                callbackGeneration = 4,
                discovering = true,
                connected = false,
                connectingPeerAddress = null,
                attempt = 121,
                maxAttempts = 120
            )
        )
    }

    @Test
    fun `callbacks from an older attempt cannot clear a new attempt for the same peer`() {
        assertEquals(
            false,
            wifiDirectAttemptIsCurrent(
                currentGeneration = 3,
                callbackGeneration = 2,
                connectingPeerAddress = "02:00:00:00:00:01",
                callbackPeerAddress = "02:00:00:00:00:01",
                connected = false
            )
        )
        assertEquals(
            true,
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
    fun `discovery cannot restart while a connection is negotiating`() {
        assertEquals(
            false,
            shouldStartWifiDirectDiscovery(
                discovering = false,
                connected = false,
                connectingPeerAddress = "02:00:00:00:00:01"
            )
        )
        assertEquals(
            true,
            shouldStartWifiDirectDiscovery(
                discovering = false,
                connected = false,
                connectingPeerAddress = null
            )
        )
    }

    @Test
    fun `receiver stays registered until negotiation finishes`() {
        assertEquals(
            true,
            shouldKeepWifiDirectReceiver(
                connected = false,
                connectingPeerAddress = "02:00:00:00:00:01"
            )
        )
        assertEquals(
            false,
            shouldKeepWifiDirectReceiver(
                connected = false,
                connectingPeerAddress = null
            )
        )
    }

    @Test
    fun `terminal LAN handoff clears every stale direct field and peer`() {
        val staleState = WifiDirectState(
            enabled = true,
            preparing = true,
            discovering = true,
            peers = listOf(
                WifiDirectPeer(
                    name = "GEON-JETSON-1234",
                    deviceAddress = "02:00:00:00:00:01",
                    status = 4
                )
            ),
            connectingPeerAddress = "02:00:00:00:00:01",
            connected = true,
            groupOwnerAddress = "192.168.49.1",
            apiStatus = WifiDirectApiStatus.READY,
            apiDeviceName = "Jetson",
            apiError = "stale",
            error = "stale"
        )

        val cleared = wifiDirectDisconnectedState(staleState, clearPeers = true)

        assertTrue(cleared.enabled)
        assertFalse(cleared.preparing)
        assertFalse(cleared.discovering)
        assertTrue(cleared.peers.isEmpty())
        assertNull(cleared.connectingPeerAddress)
        assertFalse(cleared.connected)
        assertNull(cleared.groupOwnerAddress)
        assertEquals(WifiDirectApiStatus.IDLE, cleared.apiStatus)
        assertNull(cleared.apiDeviceName)
        assertNull(cleared.apiError)
        assertNull(cleared.error)
    }
}
