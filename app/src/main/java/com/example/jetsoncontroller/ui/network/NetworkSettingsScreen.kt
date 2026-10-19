package com.example.jetsoncontroller.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoChrome
import com.example.jetsoncontroller.ui.devices.DeviceTitleBar
import com.example.jetsoncontroller.ui.devices.deviceTransportLabel
import com.example.jetsoncontroller.ui.devices.rememberDeviceOverlayHost
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Composable
fun NetworkSettingsScreen(
    state: NetworkSettingsUiState,
    onBack: () -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    wifiScanPermissionGranted: Boolean,
    onRequestWifiScanPermission: () -> Unit,
    onScanAccessPoints: () -> Unit,
    onSelectAccessPoint: (WifiAccessPoint) -> Unit,
    deviceName: String = "선택한 장치",
    onApply: (WifiApplyTarget) -> Unit = { onSubmit() },
    selectedDeviceId: String? = state.deviceId,
    ongoingStrip: GeoStatusStripState? = null
) {
    val c = LocalGeoColors.current
    val type = LocalGeoType.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val chrome = LocalGeoChrome.current
    var titleHeight by remember { mutableFloatStateOf(56f) }
    var manual by rememberSaveable(state.deviceId) { mutableStateOf(false) }
    var menu by rememberSaveable(state.deviceId) { mutableStateOf(false) }
    var info by rememberSaveable(state.deviceId) { mutableStateOf(false) }
    var confirmation by remember(state.deviceId) { mutableStateOf<WifiApplyTarget?>(null) }
    val host = rememberDeviceOverlayHost(selectedDeviceId, menu || info || confirmation != null) {
        menu = false; info = false; confirmation = null
    }
    val connected = state.transportType != null && state.deviceId.equals(selectedDeviceId, true)
    val selected = state.accessPoints.firstOrNull { it.ssid == state.selectedAccessPointSsid }
    val editing = manual || selected != null
    val unavailable = state.wifiApplyUnavailableReason()
    val requestApply = {
        focus.clearFocus()
        confirmation = state.applyTarget()
    }
    Column(Modifier.fillMaxSize()) {
        DeviceTitleBar("장치 Wi-Fi", onBack, modifier = Modifier.onSizeChanged { titleHeight = it.height / density.density }) {
            Box {
                GeoIconButton(GeoIcons.more, "더 보기", { menu = true }, chrome = true)
                GeoMenu(menu, { menu = false }, listOf(GeoMenuItem("연결 상태", { info = true })))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        GeoActionArea(
            visibilityKey = state.deviceId to editing,
            modifier = Modifier.fillMaxSize(),
            chrome = chrome.copy(topDp = chrome.topDp + titleHeight),
            action = {
                if (editing) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$deviceName · ${deviceTransportLabel(state.transportType)} · ${unavailable ?: "적용할 수 있습니다."}",
                        style = type.body, color = c.textSecondary)
                    GeoButton("적용", { requestApply() }, Modifier.testTag("wifi-connect-button"), enabled = unavailable == null,
                        busy = state.sending, busyLabel = "적용 중", actionArea = true)
                }
            }
        ) { inlineAction ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { GeoStatusStrip(listOfNotNull(ongoingStrip)) }
                item {
                    Column(Modifier.padding(horizontal = LocalGeoWindow.current.gutterDp.dp).padding(top = 16.dp)) {
                        Text(deviceName, style = type.caption, color = c.textSecondary)
                        GeoSection("현재 네트워크") {
                            GeoFactRow(state.currentWifiSsid.takeIf { connected && state.wifiConnected } ?: "연결된 Wi-Fi 없음",
                                if (connected && state.wifiConnected) "연결됨" else "미확인",
                                Modifier.testTag("wifi-access-point-${state.currentWifiSsid}").semantics { disabled() })
                            if (connected && state.sameWifi) Text("$deviceName · 휴대폰과 같은 네트워크",
                                style = type.caption, color = c.textSecondary)
                        }
                        GeoSection("주변 네트워크", actionLabel = if (wifiScanPermissionGranted) "다시 검색" else null,
                            onAction = if (wifiScanPermissionGranted) onScanAccessPoints else null) {
                            if (!wifiScanPermissionGranted) {
                                GeoStateBlock(GeoStateContent(GeoStateKind.PERMISSION, "위치 권한이 꺼져 있습니다",
                                    "주변 네트워크를 찾으려면 위치 권한이 필요합니다.", "권한 허용"),
                                    onAction = onRequestWifiScanPermission)
                            } else {
                                if (state.scanningAccessPoints) Text("주변 네트워크를 찾고 있습니다.", style = type.body, color = c.textSecondary)
                                state.accessPointError?.let {
                                    GeoStateBlock(GeoStateContent(GeoStateKind.ERROR, "네트워크를 찾지 못했습니다",
                                        "연결을 확인하고 다시 시도하세요.", "다시 시도"), onAction = onScanAccessPoints)
                                }
                                if (!state.scanningAccessPoints && state.accessPointError == null && state.accessPoints.isEmpty()) {
                                    Text("주변 네트워크가 없습니다.", style = type.body, color = c.textSecondary)
                                }
                                Column(Modifier.selectableGroup()) {
                                    state.accessPoints.filterNot { state.isCurrentJetsonWifi(it.ssid) }.forEach { accessPoint ->
                                        val expanded = !manual && accessPoint.ssid == state.selectedAccessPointSsid
                                        GeoRadioRow(accessPoint.ssid, expanded, {
                                            manual = false
                                            onSelectAccessPoint(accessPoint)
                                        }, enabled = connected && !state.sending && accessPoint.provisionable,
                                            disabledReason = if (!accessPoint.provisionable) "이 네트워크의 인증 방식은 지원하지 않습니다."
                                                else "연결되면 쓸 수 있습니다.")
                                        if (expanded && accessPoint.requiresPassword) {
                                            GeoTextField(state.password, onPasswordChange, "비밀번호",
                                                Modifier.testTag("wifi-selected-network-form").padding(start = 36.dp, bottom = 16.dp), password = true,
                                                helper = "대소문자를 구분합니다.", enabled = connected && !state.sending,
                                                disabledReason = "연결되면 쓸 수 있습니다.")
                                        }
                                    }
                                }
                            }
                            GeoRow("숨겨진 네트워크 추가", {
                                manual = !manual
                                if (manual) { onSsidChange(""); onPasswordChange(""); onHiddenChange(true) }
                            }, enabled = connected && !state.sending, disabledReason = "연결되면 쓸 수 있습니다.")
                            if (manual) {
                                GeoTextField(state.ssid, onSsidChange, "네트워크 이름", enabled = connected && !state.sending,
                                    disabledReason = "연결되면 쓸 수 있습니다.")
                                Spacer(Modifier.height(12.dp))
                                GeoTextField(state.password, onPasswordChange, "비밀번호", password = true,
                                    helper = "비밀번호가 없는 네트워크는 비워 두세요.", enabled = connected && !state.sending,
                                    disabledReason = "연결되면 쓸 수 있습니다.")
                                GeoSwitchRow("숨겨진 네트워크", state.hidden, onHiddenChange,
                                    enabled = connected && !state.sending, disabledReason = "연결되면 쓸 수 있습니다.")
                            }
                        }
                        state.message?.let {
                            Text(it, style = type.body, color = if (state.isError) c.danger else c.textSecondary)
                        }
                    }
                }
                item { inlineAction() }
            }
        }
        GeoSnackbar(host, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
        }
    }
    confirmation?.let { target ->
        val valid = state.matches(target)
        GeoConfirmDialog("${deviceName}을 ${target.ssid}에 연결할까요?", "적용하면 지금 연결이 끊길 수 있습니다.",
            "적용", onConfirm = { confirmation = null; if (valid) onApply(target) },
            onDismissRequest = { confirmation = null }, confirmEnabled = valid,
            disabledReason = if (!valid) "연결과 네트워크 선택을 다시 확인하세요." else null)
    }
    if (info) GeoInfoDialog("연결 상태", { info = false }) {
        GeoFactRow("휴대폰 Wi-Fi", state.mobileWifiSsid ?: "미확인")
        GeoFactRow("장치 Wi-Fi", state.currentWifiSsid.takeIf { connected && state.wifiConnected } ?: "미확인")
        Text(when {
            !connected || state.mobileWifiSsid.isNullOrBlank() || state.currentWifiSsid.isNullOrBlank() -> "연결 상태를 확인할 수 없습니다."
            state.sameWifi -> "같은 네트워크입니다."
            else -> "서로 다른 네트워크입니다."
        }, style = type.body, color = c.text)
    }
}
