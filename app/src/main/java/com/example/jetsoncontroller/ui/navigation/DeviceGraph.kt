package com.example.jetsoncontroller.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.alerts.AlertCenterScreen
import com.example.jetsoncontroller.ui.connection.ConnectionHubScreen
import com.example.jetsoncontroller.ui.devices.DeviceDetailScreen
import com.example.jetsoncontroller.ui.devices.DevicesHomeScreen
import com.example.jetsoncontroller.ui.devices.formatFreshness
import com.example.jetsoncontroller.ui.devices.formatSeenAt
import com.example.jetsoncontroller.ui.network.NetworkSettingsScreen
import com.example.jetsoncontroller.ui.sensors.SensorScreen

internal fun NavGraphBuilder.deviceGraph(state: State<AppNavigationState>, includeRoot: Boolean = false) {
        composable(
            Routes.CONNECTION_HUB
        ) {
            with(state.value) {
                ConnectionHubScreen(
                    onAddDevice = { navController.navigateSingle(Routes.ONBOARDING) },
                    onDirectConnect = { device ->
                        repository.prepareManualWifiDirect(device.deviceId)
                        navController.navigateSingle(Routes.WIFI_DIRECT)
                    },
                    onSettingsClick = { navigateToTab(navController, Routes.SETTINGS) },
                    onUploadHistoryClick = { navController.navigateSingle(Routes.UPLOAD_QUEUE) },
                    onServerDataClick = { navController.navigateSingle(Routes.SERVER_STORAGE) },
                    onOpenDashboard = { navigateToDeviceRoot(navController) },
                    unreadAlertCount = alertCenterState.unreadCount,
                    onAlertsClick = { navController.navigateSingle(Routes.ALERTS) },
                    lanEndpoints = lanEndpoints,
                    registeredDevices = deviceState.registeredDevices,
                    transportState = transportState,
                    lanError = userFacingConnectionMessage(
                        lanConnectionError ?: lanDiscoveryError,
                        developerModeEnabled
                    ),
                    connectingLanDeviceId = connectingLanDeviceId,
                    localNetworkPermissionGranted = localNetworkPermissionGranted,
                    onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                    onRefreshLan = { repository.startLanDiscovery() },
                    onConnectLan = { endpoint ->
                        pendingDashboardTransport = TransportType.LAN
                        repository.connectLan(endpoint)
                    },
                    onReconnectDevice = { device ->
                        repository.connectRegisteredAutomatically(device.deviceId)
                        navigateToDeviceRoot(navController)
                    }
                )
            }
        }

    (listOf(Routes.DEVICE_BROWSE) + if (includeRoot) listOf(Routes.DEVICES) else emptyList()).forEach { rootRoute ->
        composable(rootRoute) {
            with(state.value) {
                StatusPollingLifecycleEffect(dashboardViewModel)

                DevicesHomeScreen(
                    registeredDevices = deviceState.registeredDevices,
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
                    onAddDevice = { navController.navigateSingle(Routes.ONBOARDING) },
                    // A first registration is QR-only; the other thing a phone can do without a device
                    // is read the receiving server's files.
                    onServerFiles = { navController.navigateSingle(Routes.SERVER_STORAGE) },
                    onOpenDevice = { navController.navigateSingle(Routes.DEVICE_DETAIL) },
                    onReconnect = { currentRegisteredDevice?.let(connectRegisteredDevice) },
                    onStartCollection = openSurveyPreparation,
                    onViewRun = { navController.navigateSingle(Routes.ACTIVE_RUN) { launchSingleTop = true } },
                    onSelectDevice = connectRegisteredDevice,
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
                    onSaveDiagnostics = { navController.navigateSingle(Routes.DIAGNOSTICS) }
                )
            }
        }
    }
        composable(Routes.DEVICE_DETAIL) {
            with(state.value) {
                StatusPollingLifecycleEffect(dashboardViewModel)
                DeviceDetailScreen(
                    deviceName = selectedDeviceName,
                    dashboard = deviceDashboardState,
                    snapshot = selectedSnapshot,
                    connection = selectedConnection,
                    lastSeenText = selectedSnapshot?.lastSeenAtEpochMillis?.let {
                        formatSeenAt(it, System.currentTimeMillis())
                    },
                    freshness = formatFreshness(deviceDashboardState.statusAgeSeconds),
                    runActive = deviceRunActive,
                    developerModeEnabled = developerModeEnabled,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navigateToDeviceRoot(navController)
                        }
                    },
                    onDisconnect = {
                        dashboardViewModel.disconnect()
                        pendingDashboardTransport = null
                        if (!navController.popBackStack()) {
                            navigateToDeviceRoot(navController)
                        }
                    },
                    onStartCollection = openSurveyPreparation,
                    onViewRun = { navController.navigateSingle(Routes.ACTIVE_RUN) { launchSingleTop = true } },
                    onCamera = { navController.navigateSingle(Routes.CAMERA_PREVIEW) },
                    onGnss = {
                        requestedMapHistoryEntryId = null
                        requestedMapActiveRunId = null
                        navController.navigateSingle(Routes.GNSS_MAP)
                    },
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
                    onSaveDiagnostics = { navController.navigateSingle(Routes.DIAGNOSTICS) },
                    onNetwork = { navController.navigateSingle(Routes.NETWORK_SETTINGS) },
                    onFiles = { navController.navigateSingle(Routes.STORAGE) },
                    onReboot = dashboardViewModel::reboot,
                    onShutdown = dashboardViewModel::shutdown,
                    onPipelines = { navController.navigateSingle(Routes.PIPELINES) },
                    onFan = { navController.navigateSingle(Routes.ADMIN_TOOLS) },
                    onDiagnostics = { navController.navigateSingle(Routes.DIAGNOSTICS) }
                )
            }
        }

        composable(Routes.ALERTS) {
            with(state.value) {
                AlertCenterScreen(
                    state = alertCenterState,
                    onBack = { navController.popBackStack() },
                    onAlertClick = { alert ->
                        alertCenterViewModel.markRead(alert.id)
                        navController.navigateSingle(alertDestinationRoute(alert.destination)) {
                            popUpTo(Routes.ALERTS) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDelete = alertCenterViewModel::delete,
                    onMarkAllRead = alertCenterViewModel::markAllRead,
                    onClear = alertCenterViewModel::clear
                )
            }
        }

        composable(Routes.NETWORK_SETTINGS) {
            with(state.value) {
                LaunchedEffect(wifiScanPermissionGranted) {
                    if (wifiScanPermissionGranted) {
                        networkSettingsViewModel.scanAccessPoints()
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        networkSettingsViewModel.stopAccessPointScan()
                    }
                }

                NetworkSettingsScreen(
                    state = networkSettingsState,
                    onBack = { navController.popBackStack() },
                    onSsidChange = networkSettingsViewModel::onSsidChange,
                    onPasswordChange = networkSettingsViewModel::onPasswordChange,
                    onHiddenChange = networkSettingsViewModel::onHiddenChange,
                    onSubmit = networkSettingsViewModel::submit,
                    wifiScanPermissionGranted = wifiScanPermissionGranted,
                    onRequestWifiScanPermission = onRequestWifiScanPermission,
                    onScanAccessPoints = networkSettingsViewModel::scanAccessPoints,
                    onSelectAccessPoint = networkSettingsViewModel::selectAccessPoint,
                    deviceName = selectedDeviceName
                )
            }
        }


        composable(Routes.SENSORS) {
            with(state.value) {
                StatusPollingLifecycleEffect(dashboardViewModel)
                SensorScreen(
                    deviceName = selectedDeviceName,
                    onDevices = { navigateToDeviceRoot(navController) },
                    onAlerts = { navController.navigateSingle(Routes.ALERTS) },
                    unreadCount = alertCenterState.unreadCount,
                    status = dashboardState.status,
                    deviceOnline = transportState is TransportState.Connected,
                    fullControlAvailable = fullControlConnected,
                    onCameraClick = { navController.navigateSingle(Routes.CAMERA_PREVIEW) },
                    onGnssClick = {
                        requestedMapHistoryEntryId = null
                        requestedMapActiveRunId = null
                        navController.navigateSingle(Routes.GNSS_MAP)
                    },
                    onSectionSelected = onSectionSelected
                )
            }
        }

}
