package com.example.jetsoncontroller.ui.wifi

import android.net.wifi.p2p.WifiP2pDevice
import com.example.jetsoncontroller.data.network.WifiDirectApiStatus
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState

internal enum class DirectStage {
    UNSUPPORTED, PERMISSION, RADIO_OFF, DISCOVERING, EMPTY, PEERS, DISCOVERY_ERROR,
    CLEANING_UP, PREPARING, PREPARATION_ERROR, CONNECTING, CANCELLING, VERIFYING, AUTHENTICATION_ERROR, READY
}

internal fun directStage(state: WifiDirectState, permissionGranted: Boolean, cancelling: Boolean = false): DirectStage = when {
    state.cleaningUp -> if (cancelling) DirectStage.CANCELLING else DirectStage.CLEANING_UP
    !state.supported -> DirectStage.UNSUPPORTED
    !permissionGranted -> DirectStage.PERMISSION
    !state.connected && state.bootstrapPeer != null ->
        if (state.apiStatus == WifiDirectApiStatus.ERROR) DirectStage.PREPARATION_ERROR else DirectStage.PREPARING
    state.connectingPeerAddress != null -> DirectStage.CONNECTING
    state.connected -> when (state.apiStatus) {
        WifiDirectApiStatus.READY -> DirectStage.READY
        WifiDirectApiStatus.ERROR -> DirectStage.AUTHENTICATION_ERROR
        else -> DirectStage.VERIFYING
    }
    !state.enabled -> DirectStage.RADIO_OFF
    state.error != null && !state.discovering -> DirectStage.DISCOVERY_ERROR
    state.discovering -> DirectStage.DISCOVERING
    state.peers.isNotEmpty() -> DirectStage.PEERS
    else -> DirectStage.EMPTY
}

internal fun peerDisplayName(peer: WifiDirectPeer): String = peer.name.takeIf { it.isNotBlank() } ?: "이름 없는 장치"

internal fun peerStatusLabel(status: Int, authenticated: Boolean = false): String = when (status) {
    WifiP2pDevice.CONNECTED -> if (authenticated) "연결됨" else "장치 확인 중"
    WifiP2pDevice.INVITED -> "연결 승인 대기 중"
    WifiP2pDevice.AVAILABLE -> "연결 가능"
    WifiP2pDevice.FAILED -> "이전 연결 실패"
    WifiP2pDevice.UNAVAILABLE -> "현재 연결할 수 없음"
    else -> "상태 확인 중"
}

internal fun canConnectDirectPeer(state: WifiDirectState, permissionGranted: Boolean, address: String): Boolean =
    directStage(state, permissionGranted) in setOf(DirectStage.PEERS, DirectStage.DISCOVERING) &&
        // After process recreation Android may retain the physical group while
        // our target-bound session is idle. connect() verifies that exact peer
        // against the existing group before the API authenticates the device.
        state.peers.any { it.deviceAddress == address && it.status in setOf(
            WifiP2pDevice.AVAILABLE, WifiP2pDevice.FAILED, WifiP2pDevice.CONNECTED
        ) }

internal fun directFailureText(message: String?): String = when {
    message?.contains("위치") == true -> "휴대폰의 위치 서비스를 켠 뒤 다시 찾으세요."
    message?.contains("Wi-Fi") == true || message?.contains("와이파이") == true -> "휴대폰의 Wi-Fi를 켠 뒤 다시 확인하세요."
    message?.contains("권한") == true -> "가까운 장치를 찾아 바로 연결하려면 권한을 허용하세요."
    else -> "장치에 연결하지 못했습니다. 장치 전원과 거리를 확인하세요."
}
