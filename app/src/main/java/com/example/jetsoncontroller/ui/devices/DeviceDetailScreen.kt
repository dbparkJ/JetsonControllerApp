package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.connection.DeviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.sensors.DeviceSensorRows
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun DeviceDetailScreen(
    deviceName: String,
    dashboard: DashboardUiState,
    snapshot: DeviceSnapshot?,
    connection: DeviceConnectionUi,
    lastSeenText: String?,
    freshness: String?,
    runActive: Boolean,
    developerModeEnabled: Boolean,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onStartCollection: () -> Unit,
    onViewRun: () -> Unit,
    onCamera: () -> Unit,
    onGnss: () -> Unit,
    onSameNetwork: () -> Unit,
    onDirectConnect: () -> Unit,
    onQrRegister: () -> Unit,
    onSaveDiagnostics: () -> Unit,
    onNetwork: () -> Unit,
    onFiles: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    onPipelines: () -> Unit,
    onFan: () -> Unit,
    onDiagnostics: () -> Unit,
    deviceId: String = snapshot?.deviceId.orEmpty(),
    selectedDeviceId: String? = deviceId,
    onForget: (() -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    trashCount: Int? = null,
    onBluetooth: (() -> Unit)? = null,
    bluetoothPermissionGranted: Boolean = false,
    onRequestBluetoothPermission: () -> Unit = {},
    ongoingStrip: GeoStatusStripState? = null,
    onRefresh: () -> Unit = {},
    bluetoothBusy: Boolean = false,
    bluetoothError: String? = null
) {
    val c = LocalGeoColors.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val targetSelected = sameDeviceTarget(deviceId, selectedDeviceId)
    val online = targetSelected && dashboard.isOnline
    val control = online && dashboard.fullControlAvailable
    val current = online && dashboard.statusFreshness == StatusFreshness.CURRENT
    val powerReason = powerUnavailableReason(dashboard, runActive)
    val powerEnabled = targetSelected && control && current && powerReason.isEmpty()
    var sheet by rememberSaveable(deviceId) { mutableStateOf(false) }
    var menu by rememberSaveable(deviceId) { mutableStateOf(false) }
    var confirmation by rememberSaveable(deviceId) { mutableStateOf<String?>(null) }
    var previousSelected by remember(deviceId) { mutableStateOf(selectedDeviceId) }
    LaunchedEffect(selectedDeviceId) {
        if (previousSelected != selectedDeviceId) {
            val wasOpen = sheet || menu || confirmation != null
            sheet = false; menu = false; confirmation = null
            previousSelected = selectedDeviceId
            if (wasOpen) snackbar.showGeoSnackbar("장치를 바꿔서 열려 있던 확인 창을 닫았습니다.")
        }
    }
    val disabledReason = if (!targetSelected) "이 장치를 다시 선택하면 쓸 수 있습니다." else "연결되면 쓸 수 있습니다."
    Column(Modifier.fillMaxSize()) {
        DeviceTitleBar("장치", onBack) {
            Box {
                GeoIconButton(GeoIcons.more, "더 보기", { menu = true }, chrome = true)
                GeoMenu(menu, { menu = false }, listOf(GeoMenuItem("연결 끊기", {
                    onDisconnect()
                    scope.launch {
                        if (snackbar.showGeoSnackbar("$deviceName 연결을 끊었습니다.", "다시 연결") == SnackbarResult.ActionPerformed) onSameNetwork()
                    }
                },
                    enabled = online, disabledReason = disabledReason)))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                GeoStatusStrip(listOfNotNull(
                    if (!online) GeoStatusStripState(GeoStripKind.DISCONNECTED, deviceName,
                        listOfNotNull("연결이 끊겼습니다", lastSeenText?.let { "마지막 응답 $it" }).joinToString(" · "),
                        listOf(GeoOverlayAction("다시 연결", onSameNetwork), GeoOverlayAction("연결 방법", { sheet = true }))) else null,
                    ongoingStrip
                ))
            }
            item {
                Column(Modifier.padding(horizontal = LocalGeoWindow.current.gutterDp.dp)) {
                    DeviceIdentity(deviceName, connection,
                        if (online) listOfNotNull(deviceTransportLabel(dashboard.transportType), freshness).joinToString(" · ")
                        else lastSeenText?.let { "마지막 응답 $it" }, detail = true)
                    GeoSection("상태", modifier = Modifier.padding(top = 8.dp)) {
                        GeoFigureGrid(deviceFigureItems(dashboard.status.takeIf { current }, snapshot))
                        GeoButton("다시 확인", onRefresh, style = GeoButtonStyle.Tertiary,
                            enabled = online, disabledReason = if (online) null else disabledReason)
                    }
                    GeoSection("센서") {
                        DeviceSensorRows(dashboard.status, current, control, onCamera, onGnss)
                    }
                    GeoSection("연결") {
                        GeoRow("연결 방법", { sheet = true }, value = deviceTransportLabel(dashboard.transportType))
                        GeoRow("장치 Wi-Fi", onNetwork, value = dashboard.status.wifiSsid,
                            enabled = online, disabledReason = disabledReason)
                    }
                    GeoSection("데이터") {
                        GeoRow("장치 파일", onFiles, enabled = control, disabledReason = disabledReason)
                        GeoRow("휴지통", { onTrash?.invoke() }, value = trashCount?.let { "${it}개" },
                            enabled = control && onTrash != null, disabledReason = disabledReason)
                    }
                    GeoSection("관리", danger = true,
                        note = "재부팅과 전원 종료는 수집 중에는 쓸 수 없습니다.") {
                        DevicePowerButtons(powerEnabled, { confirmation = "reboot" }, { confirmation = "shutdown" })
                        if (!powerEnabled) Text(powerReason.ifEmpty { disabledReason }, style = LocalGeoType.current.body, color = c.textSecondary)
                    }
                    GeoSection("이 휴대폰", danger = true, note = "이 휴대폰에 저장된 인증 정보만 지웁니다.") {
                        GeoButton("등록 해제", { confirmation = "forget" }, style = GeoButtonStyle.DangerOutline,
                            actionArea = true, enabled = onForget != null,
                            disabledReason = if (onForget == null) "등록된 장치를 먼저 선택하세요." else null)
                    }
                }
            }
        }
        GeoSnackbar(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
        }
    }
    confirmation?.let { action ->
        val forget = action == "forget"
        val reboot = action == "reboot"
        val enabled = if (forget) onForget != null else powerEnabled
        GeoConfirmDialog(
            title = when { forget -> "$deviceName 등록을 해제할까요?"; reboot -> "${deviceName}을 재부팅할까요?"; else -> "${deviceName}의 전원을 끌까요?" },
            body = when {
                forget -> "이 휴대폰에 저장된 인증 정보만 지웁니다. 장치의 파일과 설정은 그대로입니다. 다시 쓰려면 QR 코드로 다시 등록해야 합니다."
                reboot -> "재부팅하는 동안 연결이 끊깁니다. 진행 중인 전송은 멈춥니다."
                else -> "다시 켜려면 장치의 전원 버튼을 눌러야 합니다. 진행 중인 전송은 멈춥니다."
            },
            confirmLabel = when { forget -> "등록 해제"; reboot -> "재부팅"; else -> "전원 종료" },
            kind = GeoConfirmKind.DANGER,
            confirmEnabled = enabled,
            disabledReason = if (!enabled) powerReason.ifEmpty { disabledReason } else null,
            onConfirm = {
                confirmation = null
                if (enabled) when { forget -> onForget?.invoke(); reboot -> onReboot(); else -> onShutdown() }
            },
            onDismissRequest = { confirmation = null }
        )
    }
    if (sheet) ConnectMethodSheet(deviceName, connection, lastSeenText, developerModeEnabled,
        { sheet = false }, { sheet = false; onSameNetwork() }, { sheet = false; onDirectConnect() },
        { sheet = false; onQrRegister() }, onSaveDiagnostics,
        onBluetooth = onBluetooth,
        bluetoothPermissionGranted = bluetoothPermissionGranted, onRequestBluetoothPermission = onRequestBluetoothPermission,
        bluetoothBusy = bluetoothBusy, bluetoothError = bluetoothError)
}

internal fun powerUnavailableReason(dashboard: DashboardUiState, runActive: Boolean): String = when {
    runActive -> "수집 중에는 쓸 수 없습니다."
    !dashboard.isOnline || !dashboard.fullControlAvailable -> "연결되면 쓸 수 있습니다."
    dashboard.statusFreshness != StatusFreshness.CURRENT -> "장치 상태를 다시 확인하세요."
    !dashboard.capabilities.powerCommandsEnabled -> "이 장치에서는 쓸 수 없습니다."
    dashboard.operationInProgress -> "진행 중인 작업이 끝나면 쓸 수 있습니다."
    else -> ""
}
