package com.example.jetsoncontroller.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.devices.DeviceListScreen
import com.example.jetsoncontroller.ui.onboarding.FirstDeviceOnboardingScreen
import com.example.jetsoncontroller.ui.pairing.PairingScreen
import com.example.jetsoncontroller.ui.pairing.QrScannerScreen
import com.example.jetsoncontroller.ui.sensors.CameraPreviewScreen
import com.example.jetsoncontroller.ui.wifi.WifiDirectScreen

internal fun NavGraphBuilder.focusedRoutes(state: State<AppNavigationState>) {
        composable(Routes.ONBOARDING) {
            with(state.value) {
                com.example.jetsoncontroller.ui.onboarding.FirstDeviceOnboardingRoute(state.value)
            }
        }

        composable(
            Routes.DEVICES_BLE
        ) {
            with(state.value) {

                DeviceListScreen(
                    state =
                        deviceState,
                    onScanClick = {
                        deviceViewModel
                            .toggleScan()
                    },
                    onRequestBluetoothPermission =
                        onRequestBluetoothPermission,
                    onConnect = {
                        device ->
                        pendingDashboardTransport = TransportType.BLE
                        deviceViewModel
                            .connect(
                                device
                            )
                    },
                    onReconnect = {
                        device ->
                        pendingDashboardTransport = TransportType.BLE
                        deviceViewModel.reconnect(device)
                    },
                    onForget = {
                        device ->
                        deviceViewModel.forget(device)
                    },
                    onAddDeviceClick = {
                        pairingViewModel.beginQrPairing()
                        navController.navigateSingle(Routes.QR_SCANNER)
                    },
                    onBack = {
                        if (pendingDashboardTransport == TransportType.BLE) {
                            pendingDashboardTransport = null
                        }
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            Routes.QR_SCANNER
        ) {
            with(state.value) {
                QrScannerScreen(
                    cameraPermissionGranted =
                        cameraPermissionGranted,
                    errorMessage =
                        pairingState.errorMessage,
                    onRequestCameraPermission =
                        onRequestCameraPermission,
                    onQrScanned = { rawValue ->
                        val accepted =
                            pairingViewModel.onQrScanned(rawValue)

                        if (accepted) {
                            navController.navigateSingle(Routes.PAIRING)
                        }

                        accepted
                    },
                    onBack = {
                        pairingViewModel.cancelPairing()
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            Routes.PAIRING
        ) {
            with(state.value) {
                PairingScreen(
                    state = pairingState,
                    onStartPairing = {
                        if (bluetoothPermissionGranted) {
                            pairingViewModel.startPairing()
                        } else {
                            onRequestBluetoothPermission()
                        }
                    },
                    onCancel = {
                        pairingViewModel.cancelPairing()
                        navController.popBackStack()
                    },
                    onRetry = {
                        if (bluetoothPermissionGranted) {
                            pairingViewModel.retry()
                        } else {
                            onRequestBluetoothPermission()
                        }
                    }
                )
            }
        }


        composable(
            Routes.WIFI_DIRECT
        ) {
            with(state.value) {
                DisposableEffect(
                    nearbyWifiPermissionGranted
                ) {
                    if (nearbyWifiPermissionGranted) {
                        wifiDirectViewModel.startDiscovery()
                    }

                    onDispose {
                        wifiDirectViewModel.stopDiscovery()
                    }
                }

                WifiDirectScreen(
                    state = wifiDirectState,
                    permissionGranted = nearbyWifiPermissionGranted,
                    onBack = {
                        if (pendingDashboardTransport == TransportType.WIFI_DIRECT) {
                            pendingDashboardTransport = null
                        }
                        navController.popBackStack()
                    },
                    onPermissionClick = onRequestNearbyWifiPermission,
                    onDiscoveryClick = {
                        if (nearbyWifiPermissionGranted) {
                            wifiDirectViewModel.startDiscovery()
                        } else {
                            onRequestNearbyWifiPermission()
                        }
                    },
                    onConnectClick = {
                        pendingDashboardTransport = TransportType.WIFI_DIRECT
                        wifiDirectViewModel.connect(it)
                    },
                    onRetryApi = { wifiDirectViewModel.retryApi() },
                    onCancel = {
                        pendingDashboardTransport = null
                        repository.cancelWifiDirectConnection()
                    }
                )
            }
        }

        composable(Routes.ACTIVE_RUN) {
            com.example.jetsoncontroller.ui.field.ActiveRunRoute(state.value)
        }

        composable(Routes.CAMERA_PREVIEW) {
            with(state.value) {
                StatusPollingLifecycleEffect(dashboardViewModel)
                val camera = dashboardState.status.cameraSensor
                val cameraActive = if (dashboardState.status.sensorTelemetryAvailable) {
                    dashboardState.status.sensorTelemetryFresh && camera.active
                } else {
                    dashboardState.status.cameraRunning
                }
                LaunchedEffect(cameraActive) {
                    cameraPreviewViewModel.setSensorActive(cameraActive)
                }
                DisposableEffect(Unit) {
                    cameraPreviewViewModel.setVisible(true)
                    onDispose { cameraPreviewViewModel.setVisible(false) }
                }
                CameraPreviewScreen(
                    state = cameraPreviewState,
                    camera = camera,
                    telemetryFresh = if (dashboardState.status.sensorTelemetryAvailable) {
                        dashboardState.status.sensorTelemetryFresh
                    } else {
                        dashboardState.status.cameraRunning
                    },
                    onBack = { navController.popBackStack() },
                    captureBusy = fieldState.captureBusy,
                    captureMessage = fieldState.captureMessage,
                    onCapture = { device, mobile -> fieldToolsViewModel.capture(fieldContext, device, mobile) },
                    onDismissCaptureMessage = fieldToolsViewModel::dismissCaptureMessage,
                    onRefresh = cameraPreviewViewModel::refresh
                )
            }
        }

        listOf(
            "${Routes.GNSS_MAP}?mode={mode}&ticket={ticket}&run={run}&device={device}",
            "map?mode={mode}&ticket={ticket}&run={run}&device={device}"
        ).forEach { mapPattern ->
            composable(mapPattern, arguments = listOf("mode", "ticket", "run", "device").map { name ->
                androidx.navigation.navArgument(name) { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }
            }) { entry ->
                val current = state.value
                val opener = androidx.compose.runtime.remember(entry) { current.navController.previousBackStackEntry?.destination?.route }
                val targetDevice = androidx.compose.runtime.saveable.rememberSaveable(entry.id) { androidx.compose.runtime.mutableStateOf(entry.arguments?.getString("device") ?: current.selectedDeviceId) }.value
                val targetRun = androidx.compose.runtime.saveable.rememberSaveable(entry.id) { androidx.compose.runtime.mutableStateOf(entry.arguments?.getString("run") ?: current.requestedMapActiveRunId.takeIf { opener == Routes.ACTIVE_RUN }) }.value
                val targetHistory = androidx.compose.runtime.saveable.rememberSaveable(entry.id) { androidx.compose.runtime.mutableStateOf(current.requestedMapHistoryEntryId.takeIf { opener == Routes.TASK_HISTORY }) }.value
                val targetTicket = entry.arguments?.getString("ticket")
                val mode = com.example.jetsoncontroller.ui.map.GeoMapMode.fromParameter(entry.arguments?.getString("mode"))
                    ?: when {
                        targetTicket != null -> com.example.jetsoncontroller.ui.map.GeoMapMode.ZONE
                        targetRun != null -> com.example.jetsoncontroller.ui.map.GeoMapMode.RUN
                        targetHistory != null -> com.example.jetsoncontroller.ui.map.GeoMapMode.ROUTE
                        else -> com.example.jetsoncontroller.ui.map.GeoMapMode.LOCATION
                    }
                com.example.jetsoncontroller.ui.map.GeoMapRouteWindow(current, mode, targetDevice, targetRun, targetTicket, targetHistory,
                    onBack = { current.navController.popBackStack() })
            }
        }

}
