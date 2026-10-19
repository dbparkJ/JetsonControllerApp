package com.example.jetsoncontroller.ui.network

import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.data.transport.TransportType

data class NetworkSettingsUiState(
    val ssid: String = "",
    val password: String = "",
    val hidden: Boolean = false,
    val selectedAccessPointSsid: String? = null,
    val currentWifiSsid: String? = null,
    val mobileWifiSsid: String? = null,
    val wifiConnected: Boolean = false,
    val transportType: TransportType? = null,
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val scanningAccessPoints: Boolean = false,
    val accessPointError: String? = null,
    val sending: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val deviceId: String? = null,
    val connectionGeneration: Long = 0L
)

internal fun NetworkSettingsUiState.isCurrentJetsonWifi(candidateSsid: String): Boolean =
    wifiConnected && !currentWifiSsid.isNullOrBlank() && currentWifiSsid == candidateSsid

internal val NetworkSettingsUiState.sameWifi: Boolean
    get() = wifiConnected && !mobileWifiSsid.isNullOrBlank() &&
        mobileWifiSsid == currentWifiSsid

/** No credential is retained in a confirmation or a saved-state bundle. */
data class WifiApplyTarget(val deviceId: String, val ssid: String, val hidden: Boolean, val generation: Long)

internal fun NetworkSettingsUiState.applyTarget(): WifiApplyTarget? =
    deviceId?.takeIf { wifiApplyUnavailableReason() == null }?.let {
        WifiApplyTarget(it, ssid, hidden, connectionGeneration)
    }

internal fun NetworkSettingsUiState.matches(target: WifiApplyTarget): Boolean =
    deviceId != null && deviceId.equals(target.deviceId, true) && ssid == target.ssid && hidden == target.hidden &&
        connectionGeneration == target.generation && wifiApplyUnavailableReason() == null

internal fun NetworkSettingsUiState.wifiApplyUnavailableReason(): String? {
    val selected = accessPoints.firstOrNull { it.ssid == selectedAccessPointSsid }
    val ssidBytes = ssid.toByteArray(Charsets.UTF_8).size
    val passwordBytes = password.toByteArray(Charsets.UTF_8).size
    return when {
        deviceId.isNullOrBlank() || transportType == null -> "연결되면 쓸 수 있습니다."
        sending -> "적용 중입니다."
        ssid.isBlank() || ssidBytes !in 1..32 -> "네트워크 이름은 UTF-8 기준 1–32바이트로 입력하세요."
        isCurrentJetsonWifi(ssid) -> "이미 연결된 네트워크입니다."
        selectedAccessPointSsid != null && selected == null -> "네트워크를 다시 선택하세요."
        selected != null && !selected.provisionable -> "이 네트워크의 인증 방식은 지원하지 않습니다."
        selected?.requiresPassword == true && passwordBytes !in 8..63 -> "비밀번호는 UTF-8 기준 8–63바이트로 입력하세요."
        selected == null && password.isNotEmpty() && passwordBytes !in 8..63 -> "비밀번호는 UTF-8 기준 8–63바이트로 입력하세요."
        else -> null
    }
}
