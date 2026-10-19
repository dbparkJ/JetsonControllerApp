package com.example.jetsoncontroller.ui.dev

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.FanStatus
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.dashboard.metricIsValid
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.model.Formats
import java.text.NumberFormat
import java.util.Locale

internal enum class FanPageState { CHECKING, OFFLINE, ERROR, UNAVAILABLE, READY }

internal fun fanPageState(state: DashboardUiState): FanPageState = when {
    !state.fullControlAvailable -> FanPageState.OFFLINE
    !state.capabilities.fanControl -> FanPageState.UNAVAILABLE
    state.fanLoading -> FanPageState.CHECKING
    state.fanError != null -> FanPageState.ERROR
    state.fanStatus?.available == false -> FanPageState.UNAVAILABLE
    state.fanStatus == null -> FanPageState.CHECKING
    else -> FanPageState.READY
}

internal fun fanManualValues(minimum: Int): List<Int> =
    (listOf(minimum.coerceIn(0, 100)) + (0..100 step 10).filter { it >= minimum.coerceIn(0, 100) }).distinct()

internal fun fanManualPercent(percent: Int, minimum: Int): Int =
    fanManualValues(minimum).minBy { kotlin.math.abs(it - percent) }

@Composable
internal fun DevFanScreen(state: DashboardUiState, onBack: () -> Unit, onRefresh: () -> Unit,
    onAuto: () -> Unit, onManual: (Int) -> Unit, deviceId: String? = null) {
    val selection = rememberFanSelection(state.fanStatus, deviceId)
    DevPage("팬 제어", onBack, footer = if (selection.manual.value && fanPageState(state) == FanPageState.READY) ({
        GeoButton("적용", { onManual(fanManualPercent(selection.percent.value, state.fanStatus!!.minimumManualPercent)) },
            Modifier.fillMaxWidth(), actionArea = true)
    }) else null) {
        FanBody(state, selection, onRefresh, onAuto)
    }
}

@Composable
internal fun FanControls(state: DashboardUiState, onRefresh: () -> Unit, onAuto: () -> Unit,
    onManual: (Int) -> Unit, deviceId: String? = null) {
    val selection = rememberFanSelection(state.fanStatus, deviceId)
    FanBody(state, selection, onRefresh, onAuto)
    if (selection.manual.value && fanPageState(state) == FanPageState.READY) {
        GeoButton("적용", { onManual(fanManualPercent(selection.percent.value, state.fanStatus!!.minimumManualPercent)) }, Modifier.fillMaxWidth())
    }
}

private data class FanSelection(val manual: MutableState<Boolean>, val percent: MutableState<Int>)

@Composable
private fun rememberFanSelection(fan: FanStatus?, deviceId: String?): FanSelection {
    val manual = rememberSaveable(deviceId, fan?.mode) { mutableStateOf(fan?.mode == "MANUAL") }
    val percent = rememberSaveable(deviceId, fan?.percent, fan?.minimumManualPercent) {
        mutableStateOf(fanManualPercent(fan?.percent ?: 40, fan?.minimumManualPercent ?: 20))
    }
    return FanSelection(manual, percent)
}

@Composable
private fun FanBody(state: DashboardUiState, selection: FanSelection, onRefresh: () -> Unit, onAuto: () -> Unit) {
    val fan = state.fanStatus
    var manual by selection.manual
    var percent by selection.percent
    val temperature = state.status.temperatureC.toDouble().takeIf {
        state.statusFreshness != StatusFreshness.UNKNOWN && state.status.metricIsValid("temperatureC")
    }
    DevIdentity(state.deviceName, "${Formats.temperature(temperature)} · ${Formats.freshness(state.statusAgeSeconds?.let(java.time.Duration::ofSeconds))}",
        status = if (state.isOnline) "연결됨" else "연결 안 됨", tone = if (state.isOnline) Tone.SUCCESS else Tone.UNKNOWN)
    when (fanPageState(state)) {
        FanPageState.CHECKING -> DevText("확인 중", secondary = true)
        FanPageState.OFFLINE -> DevEmpty("팬 상태를 확인할 수 없습니다", "장치를 같은 네트워크나 직접 연결로 연결하면 사용할 수 있습니다.")
        FanPageState.ERROR -> DevEmpty("팬 상태를 확인하지 못했습니다.", "${state.deviceName}에서 팬 정보를 받지 못했습니다.", "다시 시도", onRefresh)
        FanPageState.UNAVAILABLE -> DevEmpty("이 장치에서는 팬 제어를 사용할 수 없습니다", "${state.deviceName}은 팬 속도 조회와 제어를 지원하지 않습니다.")
        FanPageState.READY -> {
            val status = requireNotNull(fan)
            Spacer(Modifier.height(8.dp))
            GeoSection("현재 상태") {
                GeoFactRow("제어 방식", if (status.mode == "AUTO") "자동 온도 제어" else "수동 ${status.percent?.let { "$it%" } ?: "미확인"}")
                GeoFactRow("회전 속도", status.rpm?.let { "${NumberFormat.getIntegerInstance(Locale.KOREA).format(it)} RPM" } ?: "미확인", numeric = true)
            }
            GeoSection("제어 방식") {
                DevChoice("제어 방식", listOf("자동", "수동"), if (manual) 1 else 0, { index ->
                    manual = index == 1
                    if (!manual && status.mode != "AUTO" && status.autoAvailable) onAuto()
                })
                if (!manual) {
                    DevText("온도에 따라 장치가 팬 속도를 정합니다.")
                    if (!status.autoAvailable) DevFeedback("이 장치에서는 자동 온도 제어를 사용할 수 없습니다.")
                } else {
                    val values = fanManualValues(status.minimumManualPercent)
                    if (values.size > 1) GeoSliderRow("팬 속도", values.indexOf(percent).coerceAtLeast(0).toFloat(), "$percent%",
                        { percent = values[it.toInt().coerceIn(values.indices)] }, 0f..values.lastIndex.toFloat())
                    else GeoFactRow("팬 속도", "$percent%", numeric = true)
                    DevText("${status.minimumManualPercent}%부터 100%까지 10%씩 바꿀 수 있습니다.", secondary = true)
                }
            }
        }
    }
}
