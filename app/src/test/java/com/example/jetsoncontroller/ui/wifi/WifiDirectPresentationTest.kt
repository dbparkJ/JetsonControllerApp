package com.example.jetsoncontroller.ui.wifi

import android.net.wifi.p2p.WifiP2pDevice
import com.example.jetsoncontroller.data.network.WifiDirectApiStatus
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectPresentationTest {
    private val peer = WifiDirectPeer("GEO-X-1", "peer-address", WifiP2pDevice.AVAILABLE)
    private val idle = WifiDirectState(enabled = true)

    @Test
    fun `discovery permission empty peers and unsupported are independent states`() {
        assertEquals(DirectStage.UNSUPPORTED, directStage(idle.copy(supported = false), false))
        assertEquals(DirectStage.PERMISSION, directStage(idle, false))
        assertEquals(DirectStage.RADIO_OFF, directStage(idle.copy(enabled = false), true))
        assertEquals(DirectStage.DISCOVERING, directStage(idle.copy(discovering = true), true))
        assertEquals(DirectStage.EMPTY, directStage(idle.copy(discoveryAttempted = true), true))
        assertEquals(DirectStage.PEERS, directStage(idle.copy(peers = listOf(peer)), true))
        assertEquals(DirectStage.DISCOVERY_ERROR, directStage(idle.copy(error = "discovery failed"), true))
    }

    @Test
    fun `joining a group is never shown as authenticated before the API answers`() {
        assertEquals(DirectStage.CONNECTING, directStage(idle.copy(connectingPeerAddress = peer.deviceAddress), true))
        val joined = idle.copy(connected = true)
        assertEquals(DirectStage.VERIFYING, directStage(joined, true))
        assertEquals(DirectStage.VERIFYING, directStage(joined.copy(apiStatus = WifiDirectApiStatus.CHECKING), true))
        assertEquals(DirectStage.AUTHENTICATION_ERROR, directStage(joined.copy(apiStatus = WifiDirectApiStatus.ERROR), true))
        assertEquals(DirectStage.READY, directStage(joined.copy(apiStatus = WifiDirectApiStatus.READY), true))
        assertNotEquals(DirectStage.READY, directStage(idle.copy(apiStatus = WifiDirectApiStatus.READY), true))
        assertEquals("장치 확인 중", peerStatusLabel(WifiP2pDevice.CONNECTED))
        assertEquals(Tone.PENDING, peerStatusTone(WifiP2pDevice.CONNECTED))
        assertEquals("연결됨", peerStatusLabel(WifiP2pDevice.CONNECTED, authenticated = true))
    }

    @Test
    fun `cleanup and explicit cancellation take priority over stale connected or API flags`() {
        val cleaning = idle.copy(cleaningUp = true, connected = true, apiStatus = WifiDirectApiStatus.READY)
        assertEquals(DirectStage.CLEANING_UP, directStage(cleaning, true))
        assertEquals(DirectStage.CANCELLING, directStage(cleaning, true, cancelling = true))
        assertFalse(canConnectDirectPeer(cleaning.copy(peers = listOf(peer)), true, peer.deviceAddress))
    }

    @Test
    fun `a pending confirmation cannot connect after permission loss or peer removal`() {
        val available = idle.copy(peers = listOf(peer))
        assertTrue(canConnectDirectPeer(available, true, peer.deviceAddress))
        assertTrue(canConnectDirectPeer(available.copy(discovering = true), true, peer.deviceAddress))
        assertFalse(canConnectDirectPeer(available, false, peer.deviceAddress))
        assertFalse(canConnectDirectPeer(available.copy(peers = emptyList()), true, peer.deviceAddress))
        assertFalse(canConnectDirectPeer(available.copy(connectingPeerAddress = "other"), true, peer.deviceAddress))
        assertFalse(canConnectDirectPeer(available.copy(connected = true), true, peer.deviceAddress))
        listOf(WifiP2pDevice.CONNECTED, WifiP2pDevice.INVITED, WifiP2pDevice.UNAVAILABLE, -1).forEach { status ->
            assertFalse(canConnectDirectPeer(available.copy(peers = listOf(peer.copy(status = status))), true, peer.deviceAddress))
        }
        assertTrue(canConnectDirectPeer(available.copy(peers = listOf(peer.copy(status = WifiP2pDevice.FAILED))), true, peer.deviceAddress))
    }

    @Test
    fun `peer labels preserve states and provide a fallback name without exposing an address`() {
        assertEquals("이름 없는 장치", peerDisplayName(peer.copy(name = " ")))
        assertEquals("연결 승인 대기 중", peerStatusLabel(WifiP2pDevice.INVITED))
        assertEquals("연결 가능", peerStatusLabel(WifiP2pDevice.AVAILABLE))
        assertEquals("이전 연결 실패", peerStatusLabel(WifiP2pDevice.FAILED))
        assertEquals(Tone.WARNING, peerStatusTone(WifiP2pDevice.FAILED))
        assertEquals(Tone.INFO, peerStatusTone(WifiP2pDevice.AVAILABLE))
        assertEquals("현재 연결할 수 없음", peerStatusLabel(WifiP2pDevice.UNAVAILABLE))
        assertEquals("상태 확인 중", peerStatusLabel(-1))
        assertEquals("휴대폰의 위치 서비스를 켠 뒤 다시 찾으세요.", directFailureText("위치 서비스"))
        assertFalse(directFailureText("technical-code peer-address").contains(peer.deviceAddress))
    }
}
