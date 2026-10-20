package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.data.devices.deviceSnapshotKey
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import java.time.Duration

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun DevicesHomeScreen(
    registeredDevices: List<RegisteredDevice>,
    selectedDeviceId: String?,
    currentDashboard: DashboardUiState,
    snapshots: Map<String, DeviceSnapshot>,
    availableDeviceIds: Set<String>,
    connectingDeviceId: String?,
    connectFailedDeviceId: String?,
    runActive: Boolean,
    attention: DeviceAttention?,
    unreadAlertCount: Int,
    nowEpochMillis: Long,
    developerModeEnabled: Boolean,
    onSectionSelected: (GeoTab) -> Unit,
    onAlertsClick: () -> Unit,
    onAddDevice: () -> Unit,
    onServerFiles: () -> Unit,
    onOpenDevice: () -> Unit,
    onReconnect: () -> Unit,
    onStartCollection: () -> Unit,
    onViewRun: () -> Unit,
    onSelectDevice: (RegisteredDevice) -> Unit,
    onAttentionClick: () -> Unit,
    onSameNetwork: () -> Unit,
    onDirectConnect: () -> Unit,
    onQrRegister: () -> Unit,
    onSaveDiagnostics: () -> Unit,
    discoveredDevices: List<DeviceEndpoint> = emptyList(),
    healthItems: List<DeviceHealthItem> = emptyList(),
    onHealthItem: (DeviceHealthItem) -> Unit = { onOpenDevice() },
    ongoingStrip: GeoStatusStripState? = null,
    onBluetooth: (() -> Unit)? = null,
    bluetoothPermissionGranted: Boolean = false,
    onRequestBluetoothPermission: () -> Unit = {},
    bluetoothBusy: Boolean = false,
    bluetoothError: String? = null,
    localNetworkPermissionGranted: Boolean = true,
    onRequestLocalNetworkPermission: () -> Unit = {}
) {
    val current = registeredDevices.firstOrNull { sameDeviceTarget(it.deviceId, selectedDeviceId) }
    val online = current != null && currentDashboard.isOnline
    val fresh = online && currentDashboard.statusFreshness == StatusFreshness.CURRENT
    val snapshot = current?.let { snapshots[deviceSnapshotKey(it.deviceId)] }
    val lastSeen = snapshot?.lastSeenAtEpochMillis?.let { formatSeenAt(it, nowEpochMillis) }
    val connection = deviceConnectionUi(online && currentDashboard.statusFreshness != StatusFreshness.UNKNOWN,
        currentDashboard.transportType.takeIf { online },
        checking = (online && currentDashboard.statusFreshness == StatusFreshness.UNKNOWN) ||
            (current != null && sameDeviceTarget(current.deviceId, connectingDeviceId)),
        available = current?.deviceId?.lowercase()?.let { it in availableDeviceIds } == true,
        connectFailed = current != null && sameDeviceTarget(current.deviceId, connectFailedDeviceId), lastSeenText = lastSeen)
    var sheet by rememberSaveable { mutableStateOf(false) }
    val host = rememberDeviceOverlayHost(selectedDeviceId, sheet) { sheet = false }
    var hiddenKeys by rememberSaveable(selectedDeviceId) { mutableStateOf(arrayListOf<String>()) }
    val visibleHealth = healthItems.filterNot { it.key in hiddenKeys }
    val fallbackAttention = attention.takeIf { healthItems.isEmpty() && "unsent" !in hiddenKeys }
    Column(Modifier.fillMaxSize().background(LocalGeoColors.current.surface)) {
        DeviceTitleBar("장치", root = true) {
            GeoIconButton(GeoIcons.add, "장치 등록", onAddDevice, chrome = true)
            DeviceAlertButton(unreadAlertCount, onAlertsClick)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { GeoStatusStrip(listOfNotNull(ongoingStrip)) }
            item {
                Column(Modifier.padding(horizontal = LocalGeoWindow.current.gutterDp.dp).padding(top = 16.dp)) {
                    if (!localNetworkPermissionGranted) com.example.jetsoncontroller.ui.pairing.LocalNetworkPermissionBlock(
                        current?.deviceName ?: "GEO-X", onRequestLocalNetworkPermission)
                    if (current == null) {
                        GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "등록된 장치가 없습니다",
                            "장치 본체의 QR 코드를 스캔하면 GEO-X에서 장치를 관리할 수 있습니다.", "QR 코드 스캔"),
                            onAction = onAddDevice)
                        GeoButton("장치 없이 서버 파일 보기", onServerFiles, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                    } else {
                        GeoSection("현재 장치", first = true) {
                            DeviceIdentity(current.deviceName, connection, onClick = onOpenDevice,
                                meta = if (online) listOfNotNull(deviceTransportLabel(currentDashboard.transportType),
                                    currentDashboard.statusAgeSeconds?.let { Formats.freshness(Duration.ofSeconds(it.coerceAtLeast(0))) }).joinToString(" · ")
                                    else lastSeen?.let { "마지막 응답 $it" } ?: "현재 상태 미확인")
                            Spacer(Modifier.height(8.dp))
                            GeoFigureGrid(deviceFigureItems(currentDashboard.status.takeIf { fresh }, snapshot).take(2))
                            if (!online) GeoButtonActions(listOf(
                                GeoButtonAction("다시 연결", onReconnect, GeoButtonStyle.Secondary),
                                GeoButtonAction("연결 방법", { sheet = true }, GeoButtonStyle.Tertiary)))
                        }
                        GeoSection("확인할 일", count = Formats.caseCount((visibleHealth.size + if (fallbackAttention == null) 0 else 1).toLong())) {
                            visibleHealth.forEach { item ->
                                DeviceAttentionRow(item.title, item.detail, { onHealthItem(item) },
                                    { hiddenKeys = ArrayList(hiddenKeys + item.key) })
                            }
                            fallbackAttention?.let {
                                DeviceAttentionRow("${current.deviceName} ${it.title}", it.detail, onAttentionClick,
                                    { hiddenKeys = ArrayList(hiddenKeys + "unsent") })
                            }
                            if (visibleHealth.isEmpty() && fallbackAttention == null) Text("확인할 일이 없습니다.",
                                style = LocalGeoType.current.body, color = LocalGeoColors.current.textSecondary)
                        }
                        val others = registeredDevices.filterNot { sameDeviceTarget(it.deviceId, current.deviceId) }
                        if (others.isNotEmpty()) GeoSection("다른 장치") {
                            others.forEach { device ->
                                val seen = snapshots[deviceSnapshotKey(device.deviceId)]?.lastSeenAtEpochMillis
                                    ?.let { formatSeenAt(it, nowEpochMillis) }
                                val other = deviceConnectionUi(false, null,
                                    checking = sameDeviceTarget(device.deviceId, connectingDeviceId),
                                    available = device.deviceId.lowercase() in availableDeviceIds,
                                    connectFailed = sameDeviceTarget(device.deviceId, connectFailedDeviceId), lastSeenText = seen)
                                GeoRow(device.deviceName, { onSelectDevice(device) },
                                    leading = { DevicePhoto(40.dp) }, badge = { GeoBadge(other.label, other.tone) },
                                    meta = seen?.let { "마지막 응답 $it" } ?: other.detail)
                            }
                        }
                    }
                    val nearby = discoveredDevices.filter { endpoint ->
                        registeredDevices.none { sameDeviceTarget(endpoint.deviceId, it.deviceId) }
                    }.distinctBy { it.deviceId.lowercase() }
                    if (nearby.isNotEmpty()) GeoSection("주변에서 찾은 장치") {
                        nearby.forEach { endpoint ->
                            GeoActionRow(endpoint.displayName, "등록", onAddDevice, onAddDevice,
                                leading = { DevicePhoto(40.dp) }, meta = "같은 네트워크에서 찾았습니다.")
                        }
                    }
                }
            }
        }
        GeoSnackbar(host, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
        }
    }
    if (sheet && current != null) ConnectMethodSheet(current.deviceName, connection, lastSeen,
        developerModeEnabled, { sheet = false }, { sheet = false; onSameNetwork() },
        { sheet = false; onDirectConnect() }, { sheet = false; onQrRegister() }, onSaveDiagnostics,
        onBluetooth = onBluetooth,
        bluetoothPermissionGranted = bluetoothPermissionGranted, onRequestBluetoothPermission = onRequestBluetoothPermission,
        bluetoothBusy = bluetoothBusy, bluetoothError = bluetoothError)
}
