package com.example.jetsoncontroller.data.network

import org.junit.Assert.assertFalse
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
}
