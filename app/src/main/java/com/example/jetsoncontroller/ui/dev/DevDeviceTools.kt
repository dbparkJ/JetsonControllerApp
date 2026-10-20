package com.example.jetsoncontroller.ui.dev

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.field.FieldState
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.model.Formats
import java.time.Instant

@Composable
internal fun DevNetworkScreen(state: FieldState, onRefresh: () -> Unit, onBack: () -> Unit,
    deviceName: String = state.deviceId ?: "선택한 장치") {
    LaunchedEffect(state.deviceId, state.online) { if (state.online) onRefresh() }
    DevPage("네트워크 주소", onBack, footer = {
        GeoButton("다시 조회", onRefresh, Modifier.fillMaxWidth(), enabled = state.online && !state.networkBusy,
            disabledReason = if (!state.online) "장치에 연결하면 조회할 수 있습니다." else "주소를 조회하고 있습니다.", actionArea = true)
    }) {
        DevIdentity(deviceName, state.network?.let { "조회 ${devTime(it.checkedAtEpochMillis)}" },
            if (state.online) "연결됨" else "연결 안 됨", if (state.online) Tone.SUCCESS else Tone.UNKNOWN)
        Spacer(Modifier.height(8.dp))
        DevNetworkContent(state)
    }
}

@Composable
internal fun DevNetworkContent(state: FieldState) {
    GeoSection("주소", first = true, modifier = Modifier.padding(top = 24.dp)) {
        if (state.networkBusy) DevText("확인 중")
        if (!state.online) DevText("장치를 같은 네트워크나 직접 연결로 연결하면 조회할 수 있습니다.")
        state.network?.interfaces?.distinctBy { it.interfaceName to it.address }?.groupBy(::networkAddressLabel)?.forEach { (label, addresses) ->
            addresses.forEach { address ->
                val family = if (':' in address.address) "IPv6" else "IPv4"
                DevCopyValue(if (addresses.size > 1) "$label · $family" else label, address.address)
            }
        }
        if (state.network?.interfaces?.isEmpty() == true) DevText("활성 네트워크 주소가 없습니다.")
        DevFeedback(state.networkError, true)
    }
}

@Composable
internal fun DevSensorTestScreen(state: FieldState, onTest: (String) -> Unit,
    onCamera: () -> Unit, onGps: () -> Unit, onBack: () -> Unit,
    deviceName: String = state.deviceId ?: "선택한 장치") {
    var batch by remember(state.deviceId, state.online) { mutableStateOf<SensorTestBatch?>(null) }
    val active = batch
    LaunchedEffect(active, state.testingSensor, state.sensorTests, state.sensorTestError) {
        if (active != null) {
            val next = active.advance(state.online, state.testingSensor, state.sensorTests, state.sensorTestError)
            if (next != active) {
                batch = next
                if (next != null) onTest(next.sensor)
            }
        }
    }
    DevPage("카메라·GPS 테스트", onBack, footer = {
        GeoButton("테스트", {
            batch = SensorTestBatch("camera", state.sensorTests["camera"])
            onTest("camera")
        }, Modifier.fillMaxWidth(), enabled = state.online && state.testingSensor == null && batch == null,
            busy = batch != null || state.testingSensor != null, busyLabel = "테스트 중", actionArea = true,
            disabledReason = if (!state.online) "장치에 연결하면 테스트할 수 있습니다." else "센서를 테스트하고 있습니다.")
    }) {
        DevIdentity(deviceName, status = if (state.online) "연결됨" else "연결 안 됨",
            tone = if (state.online) Tone.SUCCESS else Tone.UNKNOWN)
        Spacer(Modifier.height(16.dp))
        DevText("카메라와 GPS를 차례로 테스트합니다. 센서마다 5초 동안 새 데이터 수신을 확인합니다.")
        DevSensorContent(state)
    }
}

@Composable
internal fun DevSensorContent(state: FieldState) {
    GeoSection("결과", first = true, modifier = Modifier.padding(top = 24.dp), note = state.sensorTests.values.maxByOrNull { it.checkedAtEpochMillis }?.let {
        "${devTime(it.checkedAtEpochMillis)} 테스트 · ${it.durationSeconds.toBigDecimal().stripTrailingZeros().toPlainString()}초"
    }) {
        for ((sensor, label) in listOf("camera" to "카메라", "gps" to "GPS")) {
            val result = state.sensorTests[sensor]
            GeoFactRow(label, when {
                state.testingSensor == sensor -> "확인 중"
                result?.state == "PASSED" -> "수신 확인"
                else -> "미확인"
            })
            result?.let {
                DevText(it.message)
            }
        }
        DevFeedback(state.sensorTestError, true)
    }
}

private fun devTime(value: Long): String = Formats.time(value.takeIf { it > 0 }?.let(Instant::ofEpochMilli))
