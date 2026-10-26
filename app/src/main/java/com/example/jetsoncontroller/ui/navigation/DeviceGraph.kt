package com.example.jetsoncontroller.ui.navigation

import com.example.jetsoncontroller.ui.model.localDeviceWord
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.navigation.NavBackStackEntry
import com.example.jetsoncontroller.ui.components.geo.shell.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.bleConnectionProgressText
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.alerts.AlertCenterScreen
import com.example.jetsoncontroller.ui.devices.*
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.field.ongoingRunStripState
import com.example.jetsoncontroller.data.devices.deviceSnapshotKey
import com.example.jetsoncontroller.ui.model.Formats
import java.time.Duration
import com.example.jetsoncontroller.ui.network.NetworkSettingsScreen

internal fun NavGraphBuilder.deviceGraph(state: State<AppNavigationState>, includeRoot: Boolean = false) {
    (listOf(Routes.DEVICE_BROWSE) + if (includeRoot) listOf(Routes.DEVICES) else emptyList()).forEach { rootRoute ->
        composable(rootRoute) { entry ->
            StatusPollingLifecycleEffect(state.value.dashboardViewModel)
            GeoPaneLayout(singlePane = GeoPaneSelection.LIST,
                list = { DeviceRootRoute(state, entry) }, detail = { DeviceDetailRoute(state, entry) })
        }
    }

        composable(Routes.DEVICE_DETAIL) { entry ->
            StatusPollingLifecycleEffect(state.value.dashboardViewModel)
            GeoPaneLayout(singlePane = GeoPaneSelection.DETAIL,
                list = { DeviceRootRoute(state, entry) }, detail = { DeviceDetailRoute(state, entry) })
        }

        composable(Routes.ALERTS) {
            with(state.value) {
                AlertCenterScreen(
                    state = alertCenterState,
                    onBack = { navController.popBackStack() },
                    onAlertClick = { alert ->
                        alertCenterViewModel.markRead(alert.id)
                        val destination = alertDestinationRoute(alert)
                        val target = alert.target
                        target?.transferJob?.let { job ->
                            navController.currentBackStackEntry?.savedStateHandle?.let { source ->
                                source["pushedTransferDevice"] = target.deviceId
                                source["pushedTransferName"] = target.deviceName ?: "선택한 장치"
                                source["pushedTransferId"] = job.id
                                source["pushedTransferJob"] = com.google.gson.Gson().toJson(job)
                            }
                        }
                        navController.navigateSingle(destination) {
                            launchSingleTop = true
                        }
                        if (destination == Routes.DEVICE_DETAIL && target != null) {
                            navController.currentBackStackEntry?.savedStateHandle?.set("deviceTargetId", target.deviceId)
                            navController.currentBackStackEntry?.savedStateHandle?.set("deviceTargetName", target.deviceName ?: "선택한 장치")
                        }
                    },
                    onDelete = alertCenterViewModel::delete,
                    onMarkAllRead = alertCenterViewModel::markAllRead,
                    onClear = alertCenterViewModel::clear
                )
            }
        }

        composable(Routes.NETWORK_SETTINGS) { entry ->
            val requestedTarget by entry.savedStateHandle.getStateFlow<String?>("deviceTargetId", null).collectAsState()
            val pinnedTarget = rememberSaveable(entry.id) { state.value.repository.selectedDeviceId.value.orEmpty() }
            val targetId = requestedTarget ?: if (entry.destination.route in setOf(Routes.DEVICES, Routes.DEVICE_BROWSE))
                state.value.selectedDeviceId.orEmpty() else pinnedTarget
            val requestedName by entry.savedStateHandle.getStateFlow<String?>("deviceTargetName", null).collectAsState()
            val targetName = requestedName ?: rememberSaveable(entry.id) { state.value.selectedDeviceName }
            with(state.value) {
                val matches = sameDeviceTarget(targetId, selectedDeviceId) && sameDeviceTarget(targetId, networkSettingsState.deviceId)
                LaunchedEffect(wifiScanPermissionGranted, targetId, matches) {
                    if (wifiScanPermissionGranted && matches) networkSettingsViewModel.scanAccessPoints()
                }
                DisposableEffect(Unit) { onDispose { networkSettingsViewModel.stopAccessPointScan() } }
                NetworkSettingsScreen(
                    state = if (matches) networkSettingsState else com.example.jetsoncontroller.ui.network.NetworkSettingsUiState(deviceId = targetId),
                    onBack = { navController.popBackStack() },
                    onSsidChange = { if (deviceTargetSelected(state.value, targetId)) state.value.networkSettingsViewModel.onSsidChange(it) },
                    onPasswordChange = { if (deviceTargetSelected(state.value, targetId)) state.value.networkSettingsViewModel.onPasswordChange(it) },
                    onHiddenChange = { if (deviceTargetSelected(state.value, targetId)) state.value.networkSettingsViewModel.onHiddenChange(it) },
                    onSubmit = { networkSettingsViewModel.submit() },
                    wifiScanPermissionGranted = wifiScanPermissionGranted,
                    onRequestWifiScanPermission = onRequestWifiScanPermission,
                    onScanAccessPoints = { if (deviceTargetSelected(state.value, targetId)) state.value.networkSettingsViewModel.scanAccessPoints() },
                    onSelectAccessPoint = { if (deviceTargetSelected(state.value, targetId)) state.value.networkSettingsViewModel.selectAccessPoint(it) },
                    deviceName = targetName,
                    onApply = { target -> if (sameDeviceTarget(target.deviceId, targetId)) state.value.networkSettingsViewModel.submit(target) },
                    selectedDeviceId = selectedDeviceId,
                    ongoingStrip = deviceOngoingStrip(this, targetId, targetName)
                )
            }
        }

        composable(Routes.SENSORS) {
            LaunchedEffect(Unit) {
                state.value.navController.navigateSingle(Routes.DEVICE_DETAIL) {
                    popUpTo(Routes.SENSORS) { inclusive = true }
                }
            }
        }
}

private fun deviceTargetSelected(state: AppNavigationState, targetId: String): Boolean =
    sameDeviceTarget(targetId, state.repository.selectedDeviceId.value)

private fun deviceTargetConnected(state: AppNavigationState, targetId: String): Boolean =
    deviceTargetSelected(state, targetId) && (state.repository.transportState.value as? TransportState.Connected)
        ?.deviceId?.let { sameDeviceTarget(it, targetId) } == true

private fun devicePowerBlockedByCollection(state: AppNavigationState): Boolean =
    state.deviceRunActive || state.surveyRunState.pendingStart != null || state.surveyRunState.unconfirmedRunId != null ||
        state.surveyRunState.acceptedStart != null

private fun devicePowerAllowed(state: AppNavigationState, targetId: String): Boolean =
    devicePowerTargetAllowed(targetId, state.repository.selectedDeviceId.value,
        (state.repository.transportState.value as? TransportState.Connected)?.deviceId,
        state.deviceDashboardState, devicePowerBlockedByCollection(state))

private fun deviceOngoingStrip(state: AppNavigationState, targetId: String?, name: String): GeoStatusStripState? =
    ongoingRunStripState(targetId, name, state.surveyRunState.activeRun ?: state.surveyRunState.latestRun?.takeIf { it.active },
        { if (targetId != null && deviceTargetSelected(state, targetId)) state.navController.navigateSingle(Routes.ACTIVE_RUN) })

private fun deviceRootHealthItems(state: AppNavigationState): List<DeviceHealthItem> {
    val dashboard = state.deviceDashboardState
    val health = assessDashboardHealth(dashboard.status, dashboard.statusFreshness,
        state.pipelineState.pipelines, (listOfNotNull(state.uploadState.currentJob) + state.uploadState.queue).distinctBy { it.id })
    val items = deviceHealthItems(state.selectedDeviceName, health)
    val sensors = com.example.jetsoncontroller.ui.sensors.sensorPresentations(dashboard.status,
        dashboard.isOnline && dashboard.statusFreshness == StatusFreshness.CURRENT, System.currentTimeMillis())
    return items + listOf("카메라" to sensors.camera, "위치(GNSS)" to sensors.gnss, "움직임(IMU)" to sensors.imu)
        .filter { it.second.activity in setOf(com.example.jetsoncontroller.ui.sensors.SensorActivity.STALE,
            com.example.jetsoncontroller.ui.sensors.SensorActivity.DISCONNECTED) }
        .map { (name, sensor) -> DeviceHealthItem("sensor:$name:${sensor.activity}",
            "${state.selectedDeviceName} $name ${sensor.badge}", sensor.description) }
}

internal fun openConnectionMethods(navController: androidx.navigation.NavHostController) {
    navController.navigateSingle(Routes.DEVICE_DETAIL)
    navController.currentBackStackEntry?.savedStateHandle?.set("connectionMethodsRequested", true)
}

@Composable
private fun DeviceRootRoute(state: State<AppNavigationState>, entry: NavBackStackEntry) {
    val highlightedTarget by entry.savedStateHandle.getStateFlow<String?>("deviceTargetId", null).collectAsState()
    with(state.value) {

        DevicesHomeScreen(
            registeredDevices = deviceState.registeredDevices,
            loadingDevices = deviceState.isLoading,
            highlightedDeviceId = highlightedTarget ?: selectedDeviceId,
            selectedDeviceId = selectedDeviceId,
            currentDashboard = deviceDashboardState,
            snapshots = deviceSnapshots,
            availableDeviceIds = lanEndpoints.map { it.deviceId.lowercase() }.toSet(),
            connectingDeviceId = connectingLanDeviceId,
            connectFailedDeviceId = selectedDeviceId.takeIf { lanConnectionError != null },
            runActive = deviceRunActive,
            attention = deviceAttention,
            unreadAlertCount = alertCenterState.unreadCount,
            nowEpochMillis = System.currentTimeMillis(),
            developerModeEnabled = developerModeEnabled,
            onSectionSelected = onSectionSelected,
            onAlertsClick = { navController.navigateSingle(Routes.ALERTS) },
            localNetworkPermissionGranted = localNetworkPermissionGranted,
            onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
            onAddDevice = { navController.navigateSingle(Routes.ONBOARDING) },
            // A first registration is QR-only; the other thing a phone can do without a device
            // is read the receiving server's files.
            onServerFiles = { navController.navigateSingle(Routes.SERVER_STORAGE) },
            onOpenDevice = { navController.navigateSingle(Routes.DEVICE_DETAIL) },
            onReconnect = { currentRegisteredDevice?.let(connectRegisteredDevice) },
            onStartCollection = openSurveyPreparation,
            onViewRun = { navController.navigateSingle(Routes.ACTIVE_RUN) { launchSingleTop = true } },
            onSelectDevice = { device ->
                connectRegisteredDevice(device)
                navController.navigateSingle(Routes.DEVICE_DETAIL)
                navController.currentBackStackEntry?.savedStateHandle?.set("deviceTargetId", device.deviceId)
                navController.currentBackStackEntry?.savedStateHandle?.set("deviceTargetName", device.deviceName)
            },
            onAttentionClick = { navController.navigateSingle(Routes.TRANSFER_BROWSE) },
            onSameNetwork = {
                repository.startLanDiscovery()
                currentRegisteredDevice?.let(connectRegisteredDevice)
            },
            onDirectConnect = {
                selectedDeviceId?.let { deviceId ->
                    repository.prepareManualWifiDirect(deviceId)
                    navController.navigateSingle(Routes.WIFI_DIRECT)
                }
            },
            onQrRegister = { navController.navigateSingle(Routes.ONBOARDING) },
            onSaveDiagnostics = { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.DIAGNOSTICS.route) },
            discoveredDevices = lanEndpoints,
            healthItems = deviceRootHealthItems(this),
            onHealthItem = { item ->
                navController.navigateSingle(when (item.kind) {
                    DashboardHealthIssue.FAILED_UPLOAD -> Routes.TRANSFER_BROWSE
                    DashboardHealthIssue.FAILED_PIPELINE -> Routes.WORK_BROWSE
                    else -> Routes.DEVICE_DETAIL
                })
            },
            ongoingStrip = deviceOngoingStrip(this, selectedDeviceId, selectedDeviceName),
            onBluetooth = currentRegisteredDevice?.let { device -> { deviceViewModel.reconnect(device) } },
            bluetoothPermissionGranted = bluetoothPermissionGranted,
            onRequestBluetoothPermission = onRequestBluetoothPermission,
            bluetoothBusy = sameDeviceTarget(selectedDeviceId, deviceState.reconnectingDeviceId),
            bluetoothProgress = (deviceState.connectionState as? ConnectionState.Connecting)
                ?.takeIf { sameDeviceTarget(selectedDeviceId, it.deviceId) }?.let { bleConnectionProgressText(it.attempt) },
            bluetoothError = deviceState.reconnectError.takeIf { sameDeviceTarget(selectedDeviceId, deviceState.reconnectErrorDeviceId) }
        )
    }
}

@Composable
private fun DeviceDetailRoute(state: State<AppNavigationState>, entry: NavBackStackEntry) {
    val requestedTarget by entry.savedStateHandle.getStateFlow<String?>("deviceTargetId", null).collectAsState()
    val pinnedTarget = rememberSaveable(entry.id) { state.value.repository.selectedDeviceId.value.orEmpty() }
    val targetId = requestedTarget ?: if (entry.destination.route in setOf(Routes.DEVICES, Routes.DEVICE_BROWSE))
        state.value.selectedDeviceId.orEmpty() else pinnedTarget
    val requestedName by entry.savedStateHandle.getStateFlow<String?>("deviceTargetName", null).collectAsState()
    val targetName = requestedName ?: rememberSaveable(entry.id) { state.value.selectedDeviceName }
    with(state.value) {
        val selected = sameDeviceTarget(targetId, selectedDeviceId)
        val targetDevice = deviceState.registeredDevices.firstOrNull { sameDeviceTarget(it.deviceId, targetId) }
        val snapshot = deviceSnapshots[deviceSnapshotKey(targetId)]
        val name = targetDevice?.deviceName ?: snapshot?.deviceName ?: targetName
        val dashboard = if (selected) deviceDashboardState else DashboardUiState(deviceName = name)
        val seen = snapshot?.lastSeenAtEpochMillis?.let { formatSeenAt(it, System.currentTimeMillis()) }
        val reconnect = {
            state.value.let { latest ->
                latest.deviceState.registeredDevices.firstOrNull { sameDeviceTarget(it.deviceId, targetId) }?.let {
                    latest.repository.startLanDiscovery()
                    latest.connectRegisteredDevice(it)
                }
            }
        }
        DeviceDetailScreen(
            deviceName = name,
            initialConnectionMethods = entry.savedStateHandle.get<Boolean>("connectionMethodsRequested") == true,
            dashboard = dashboard,
            snapshot = snapshot,
            connection = deviceConnectionUi(dashboard.isOnline && dashboard.statusFreshness != StatusFreshness.UNKNOWN, dashboard.transportType,
                checking = sameDeviceTarget(targetId, connectingLanDeviceId) ||
                    (dashboard.isOnline && dashboard.statusFreshness == StatusFreshness.UNKNOWN), lastSeenText = seen,
                localDeviceWord = localDeviceWord()),
            lastSeenText = seen,
            freshness = dashboard.statusAgeSeconds?.let { Formats.freshness(Duration.ofSeconds(it.coerceAtLeast(0))) },
            runActive = selected && devicePowerBlockedByCollection(this),
            developerModeEnabled = developerModeEnabled,
            onBack = { if (!navController.popBackStack()) navigateToDeviceRoot(navController) },
            onDisconnect = {
                val latest = state.value
                if (deviceTargetConnected(latest, targetId)) {
                    latest.dashboardViewModel.disconnect()
                    latest.pendingDashboardTransport = null
                }
            },
            onStartCollection = openSurveyPreparation,
            onViewRun = { if (deviceTargetSelected(state.value, targetId)) navController.navigateSingle(Routes.ACTIVE_RUN) },
            onCamera = { if (deviceTargetConnected(state.value, targetId)) navController.navigateSingle(Routes.CAMERA_PREVIEW) },
            onGnss = {
                if (deviceTargetSelected(state.value, targetId)) {
                    requestedMapHistoryEntryId = null
                    requestedMapActiveRunId = null
                    navController.navigateSingle(Routes.GNSS_MAP)
                }
            },
            onSameNetwork = { reconnect() },
            onDirectConnect = {
                if (deviceTargetSelected(state.value, targetId)) {
                    repository.prepareManualWifiDirect(targetId)
                    navController.navigateSingle(Routes.WIFI_DIRECT)
                }
            },
            onQrRegister = { navController.navigateSingle(Routes.ONBOARDING) },
            onSaveDiagnostics = { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.DIAGNOSTICS.route) },
            onNetwork = {
                if (deviceTargetConnected(state.value, targetId)) {
                    navController.navigateSingle(Routes.NETWORK_SETTINGS)
                    navController.currentBackStackEntry?.savedStateHandle?.set("deviceTargetId", targetId)
                    navController.currentBackStackEntry?.savedStateHandle?.set("deviceTargetName", name)
                }
            },
            onFiles = { if (deviceTargetConnected(state.value, targetId)) navController.navigateSingle(Routes.STORAGE) },
            onReboot = { state.value.let { if (devicePowerAllowed(it, targetId)) it.dashboardViewModel.reboot() } },
            onShutdown = { state.value.let { if (devicePowerAllowed(it, targetId)) it.dashboardViewModel.shutdown() } },
            onPipelines = { navController.navigateSingle(Routes.PIPELINES) },
            onFan = { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.FAN.route) },
            onDiagnostics = { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.DIAGNOSTICS.route) },
            deviceId = targetId,
            selectedDeviceId = selectedDeviceId,
            onForget = targetDevice?.let { {
                state.value.let { latest ->
                    // Forget is phone-local and may target a pinned, offline device.
                    latest.deviceState.registeredDevices.firstOrNull { sameDeviceTarget(it.deviceId, targetId) }?.let {
                        latest.deviceViewModel.forget(it)
                        if (!latest.navController.popBackStack()) navigateToDeviceRoot(latest.navController)
                    }
                }
            } },
            onTrash = { if (deviceTargetConnected(state.value, targetId)) navController.navigateSingle(Routes.LOCAL_TRASH) },
            trashCount = localTrashState.entries.size.takeIf { localTrashState.hasLoaded && sameDeviceTarget(localTrashState.deviceId, targetId) },
            onBluetooth = targetDevice?.takeIf { selected }?.let { device -> { deviceViewModel.reconnect(device) } },
            bluetoothPermissionGranted = bluetoothPermissionGranted,
            onRequestBluetoothPermission = onRequestBluetoothPermission,
            ongoingStrip = deviceOngoingStrip(this, targetId, name),
            onRefresh = { if (deviceTargetConnected(state.value, targetId)) dashboardViewModel.requestStatus() },
            bluetoothBusy = sameDeviceTarget(targetId, deviceState.reconnectingDeviceId),
            bluetoothProgress = (deviceState.connectionState as? ConnectionState.Connecting)
                ?.takeIf { sameDeviceTarget(targetId, it.deviceId) }?.let { bleConnectionProgressText(it.attempt) },
            bluetoothError = deviceState.reconnectError.takeIf { sameDeviceTarget(targetId, deviceState.reconnectErrorDeviceId) }
        )
    }
}
