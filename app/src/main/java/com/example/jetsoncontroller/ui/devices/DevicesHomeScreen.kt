package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.ui.model.localDeviceWord
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.data.devices.deviceSnapshotKey
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.model.Formats

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
    onRequestLocalNetworkPermission: () -> Unit = {},
    loadingDevices: Boolean = false,
    highlightedDeviceId: String? = selectedDeviceId,
    bluetoothProgress: String? = null
) {
    val scroll = com.example.jetsoncontroller.ui.components.geo.shell.rememberGeoPaneListState("devices")
    val gutter = if (LocalGeoPane.current) 0.dp else 24.dp
    val current = registeredDevices.firstOrNull { sameDeviceTarget(it.deviceId, selectedDeviceId) }
    val online = current != null && currentDashboard.isOnline
    val snapshot = current?.let { snapshots[deviceSnapshotKey(it.deviceId)] }
    val lastSeen = snapshot?.lastSeenAtEpochMillis?.let { formatSeenAt(it, nowEpochMillis) }
    val connection = deviceConnectionUi(online && currentDashboard.statusFreshness != StatusFreshness.UNKNOWN,
        currentDashboard.transportType.takeIf { online },
        checking = (online && currentDashboard.statusFreshness == StatusFreshness.UNKNOWN) ||
            (current != null && sameDeviceTarget(current.deviceId, connectingDeviceId)),
        available = current?.deviceId?.lowercase()?.let { it in availableDeviceIds } == true,
        connectFailed = current != null && sameDeviceTarget(current.deviceId, connectFailedDeviceId), lastSeenText = lastSeen,
        localDeviceWord = localDeviceWord())
    var sheet by rememberSaveable { mutableStateOf(false) }
    val host = rememberDeviceOverlayHost(selectedDeviceId, sheet) { sheet = false }
    var hiddenKeys by rememberSaveable(selectedDeviceId) { mutableStateOf(arrayListOf<String>()) }
    val visibleHealth = healthItems.filterNot { it.key in hiddenKeys }
    val nearby = discoveredDevices.filter { endpoint ->
        registeredDevices.none { sameDeviceTarget(endpoint.deviceId, it.deviceId) }
    }.distinctBy { it.deviceId.lowercase() }
    val fallbackAttention = attention.takeIf { healthItems.isEmpty() && "unsent" !in hiddenKeys }
    GeoLargeTitleScaffold("장치", actions = {
            GeoIconButton(GeoIcons.add, "장치 등록", onAddDevice)
            DeviceAlertButton(unreadAlertCount, onAlertsClick)
        }) {
        Box(Modifier.fillMaxSize()) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { GeoStatusStrip(listOfNotNull(ongoingStrip)) }
            item {
                Column(Modifier.padding(horizontal = gutter)) {
                    if (!localNetworkPermissionGranted) com.example.jetsoncontroller.ui.pairing.LocalNetworkPermissionBlock(
                        current?.deviceName ?: "GEO-X", onRequestLocalNetworkPermission)
                    if (loadingDevices) {
                        DevicesHomeLoading()
                    } else if (registeredDevices.isEmpty()) {
                        GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "등록된 장치가 없습니다",
                            "장치 본체의 QR 코드를 스캔하면 GEO-X에서 장치를 관리할 수 있습니다.", "QR 코드 스캔"),
                            onAction = onAddDevice)
                        GeoButton("장치 없이 서버 파일 보기", onServerFiles, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                    } else {
                        GeoGroupedSection("장치 목록", first = true, trailingColumn = GeoRowTrailing.Status,
                            modifier = Modifier.testTag("device-list-card")) {
                                    registeredDevices.forEachIndexed { index, device ->
                                        val isCurrent = sameDeviceTarget(device.deviceId, selectedDeviceId)
                                        val seen = snapshots[deviceSnapshotKey(device.deviceId)]?.lastSeenAtEpochMillis
                                            ?.let { formatSeenAt(it, nowEpochMillis) }
                                        val rowConnection = if (isCurrent) connection else deviceConnectionUi(false, null,
                                            checking = sameDeviceTarget(device.deviceId, connectingDeviceId),
                                            available = device.deviceId.lowercase() in availableDeviceIds,
                                            connectFailed = sameDeviceTarget(device.deviceId, connectFailedDeviceId),
                                            lastSeenText = seen, localDeviceWord = localDeviceWord())
                                        GeoPaneListRow(device.deviceName, { if (isCurrent) onOpenDevice() else onSelectDevice(device) },
                                            selected = sameDeviceTarget(device.deviceId, highlightedDeviceId),
                                            index = index, rowCount = registeredDevices.size,
                                            nextSelected = registeredDevices.getOrNull(index + 1)?.let {
                                                sameDeviceTarget(it.deviceId, highlightedDeviceId)
                                            } == true,
                                            modifier = Modifier.testTag("device-list-row-${device.deviceId}"),
                                            leading = { DevicePhoto(40.dp) }, leadingIsPhoto = true,
                                            badge = { GeoBadge(rowConnection.label, rowConnection.tone) },
                                            meta = seen?.let { "마지막 응답 $it" } ?: rowConnection.detail)
                                    }
                        }
                        if (current != null && !online) GeoGroupedSection("연결") {
                            GeoActionRow(current.deviceName, "다시 연결", onOpenDevice, onReconnect,
                                meta = connection.detail, leading = { GeoIconTile(GeoIcons.link) })
                            GeoRow("연결 방법", { sheet = true }, leading = { GeoIconTile(GeoIcons.wifi) }, divider = false)
                        }
                        val attentionCount = visibleHealth.size + if (fallbackAttention == null) 0 else 1
                        GeoGroupedSection("확인할 일 · ${Formats.caseCount(attentionCount.toLong())}") {
                            if (attentionCount > 0) DeviceSurface(tone = com.example.jetsoncontroller.ui.model.Tone.WARNING) {
                                visibleHealth.forEachIndexed { index, item ->
                                    DeviceAttentionRow(item.title, item.detail, { onHealthItem(item) },
                                        { hiddenKeys = ArrayList(hiddenKeys + item.key) },
                                        divider = index < visibleHealth.lastIndex || fallbackAttention != null)
                                }
                                fallbackAttention?.let {
                                    DeviceAttentionRow("${current?.deviceName ?: "선택한 장치"} ${it.title}", it.detail, onAttentionClick,
                                        { hiddenKeys = ArrayList(hiddenKeys + "unsent") }, divider = false)
                                }
                            } else GeoRow("확인할 일이 없습니다.", {}, navigable = false, divider = false,
                                leading = { GeoIconTile(GeoIcons.check) })
                        }
                    }
                    if (!loadingDevices && nearby.isNotEmpty()) GeoGroupedSection("주변에서 찾은 장치", first = current == null) {
                            nearby.forEachIndexed { index, endpoint ->
                                GeoActionRow(endpoint.displayName, "등록", onAddDevice, onAddDevice,
                                    leading = { DevicePhoto(40.dp) }, leadingIsPhoto = true, meta = "같은 네트워크에서 찾았습니다.",
                                    divider = index < nearby.lastIndex)
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
        bluetoothBusy = bluetoothBusy, bluetoothError = bluetoothError, bluetoothProgress = bluetoothProgress)
}

/** The host supplies its real inventory loading state; an empty list alone is not loading. */
@Composable
internal fun DevicesHomeLoading() {
    GeoSkeleton("장치를 불러오고 있습니다", kind = GeoSkeletonKind.DeviceCard)
    GeoGroupedSection("장치 목록") {
        DeviceSurface {
            GeoSkeleton("연결 상태와 센서 정보를 확인합니다.", kind = GeoSkeletonKind.Rows)
        }
    }
}
