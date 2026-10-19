package com.example.jetsoncontroller.ui.dev

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.field.FieldState
import com.example.jetsoncontroller.ui.components.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DevNetworkScreen(state: FieldState, onRefresh: () -> Unit, onBack: () -> Unit,
    deviceName: String = state.deviceId ?: "선택한 장치") {
    LaunchedEffect(state.deviceId, state.online) { if (state.online) onRefresh() }
    DevPage("네트워크 주소", onBack, footer = {
        GeoButton("다시 조회", onRefresh, Modifier.fillMaxWidth(), enabled = state.online && !state.networkBusy,
            disabledReason = if (!state.online) "장치에 연결하면 조회할 수 있습니다." else "주소를 조회하고 있습니다.", actionArea = true)
    }) {
        DevIdentity(deviceName, state.network?.let { "조회 ${devTime(it.checkedAtEpochMillis)}" },
            if (state.online) "연결됨" else "연결 안 됨", if (state.online) StatusTone.SUCCESS else StatusTone.UNKNOWN)
        Spacer(Modifier.height(8.dp))
        DevNetworkContent(state)
    }
}

@Composable
internal fun DevNetworkContent(state: FieldState) {
    GeoSection("주소") {
        if (state.networkBusy) DevText("확인 중")
        if (!state.online) DevText("장치를 같은 네트워크나 직접 연결로 연결하면 조회할 수 있습니다.")
        state.network?.interfaces?.forEach { address ->
            val name = when (address.kind.lowercase()) {
                "wifi", "wi-fi", "wlan" -> "Wi-Fi"
                "ethernet", "wired", "lan" -> "유선"
                "wifi_direct", "wi-fi direct", "wifi-direct", "p2p", "direct" -> "직접 연결"
                "tailscale" -> "Tailscale"
                else -> address.interfaceName
            }
            DevCopyValue(name, address.address)
        }
        if (state.network?.interfaces?.isEmpty() == true) DevText("활성 네트워크 주소가 없습니다.")
        DevFeedback(state.networkError, true)
    }
}

@Composable
internal fun DevSensorTestScreen(state: FieldState, onTest: (String) -> Unit,
    onCamera: () -> Unit, onGps: () -> Unit, onBack: () -> Unit,
    deviceName: String = state.deviceId ?: "선택한 장치") {
    DevPage("카메라·GPS 테스트", onBack) {
        DevIdentity(deviceName, status = if (state.online) "연결됨" else "연결 안 됨",
            tone = if (state.online) StatusTone.SUCCESS else StatusTone.UNKNOWN)
        Spacer(Modifier.height(16.dp))
        DevText("5초 동안 장치에서 새 카메라 프레임과 GPS 데이터 수신을 확인합니다.")
        DevSensorContent(state, onTest, onCamera, onGps)
    }
}

@Composable
internal fun DevSensorContent(state: FieldState, onTest: (String) -> Unit, onCamera: () -> Unit, onGps: () -> Unit) {
    GeoSection("결과") {
        for ((sensor, label) in listOf("camera" to "카메라", "gps" to "GPS")) {
            val result = state.sensorTests[sensor]
            GeoFactRow(label, when {
                state.testingSensor == sensor -> "확인 중"
                result?.state == "PASSED" -> "수신 확인"
                else -> "미확인"
            })
            result?.let {
                DevText("${devTime(it.checkedAtEpochMillis)} 테스트 · ${it.durationSeconds}초", secondary = true)
                DevText(it.message)
            }
            GeoButton("$label 테스트", { onTest(sensor) }, Modifier.fillMaxWidth(), style = GeoButtonStyle.Secondary,
                enabled = state.online && state.testingSensor == null,
                disabledReason = if (!state.online) "장치에 연결하면 테스트할 수 있습니다." else "센서를 테스트하고 있습니다.")
        }
        DevFeedback(state.sensorTestError, true)
    }
    GeoSection("보기") {
        GeoRow("카메라 프리뷰", onCamera, enabled = state.online, disabledReason = "장치에 연결하면 볼 수 있습니다.")
        GeoRow("GPS 지도", onGps, enabled = state.online, disabledReason = "장치에 연결하면 볼 수 있습니다.")
    }
}

private fun devTime(value: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(value))
