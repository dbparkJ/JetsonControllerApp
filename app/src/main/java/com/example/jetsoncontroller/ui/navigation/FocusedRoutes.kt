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
import com.example.jetsoncontroller.ui.sensors.GnssMapScreen
import com.example.jetsoncontroller.ui.wifi.WifiDirectScreen

internal fun NavGraphBuilder.focusedRoutes(state: State<AppNavigationState>) {
        composable(Routes.ONBOARDING) {
            with(state.value) {
                FirstDeviceOnboardingScreen(
                    onScanQr = {
                        pairingViewModel.beginQrPairing()
                        navController.navigateSingle(Routes.QR_SCANNER)
                    },
                    onBack = { navController.popBackStack() }
                )
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

        composable(Routes.GNSS_MAP) {
            with(state.value) {
                val mapReturnLabel = when (navController.previousBackStackEntry?.destination?.route) {
                    Routes.ACTIVE_RUN -> "수집 화면으로"
                    Routes.TASK_HISTORY -> "수집 이력으로"
                    Routes.DEVICES -> "홈으로"
                    Routes.SENSORS -> "센서 화면으로"
                    else -> "이전 화면으로"
                }
                val requestedMapRun = if (requestedMapActiveRunId != null) {
                    activeCollectionHistoryRun(
                        (surveyRunState.activeRun ?: surveyRunState.latestRun?.takeIf { it.active })
                            ?.takeIf { it.runId == requestedMapActiveRunId },
                        fieldState.runs
                    )
                } else {
                    fieldState.runs.firstOrNull { it.id == requestedMapHistoryEntryId }
                }
                LaunchedEffect(
                    selectedDeviceId,
                    requestedMapHistoryEntryId,
                    requestedMapActiveRunId,
                    requestedMapRun
                ) {
                    fieldToolsViewModel.selectRun(requestedMapRun)
                }
                DisposableEffect(selectedDeviceId, requestedMapHistoryEntryId, requestedMapActiveRunId) {
                    onDispose { fieldToolsViewModel.stopRoutePolling() }
                }
                StatusPollingLifecycleEffect(dashboardViewModel)
                GnssMapScreen(
                    route = fieldState.route,
                    quality = fieldState.routeQuality,
                    routeLabel = fieldState.selectedRun?.label,
                    returnLabel = mapReturnLabel,
                    gnss = dashboardState.status.gnssSensor,
                    telemetryFresh = dashboardState.status.sensorTelemetryFresh,
                    deviceOnline = transportState is TransportState.Connected,
                    onBack = { navController.popBackStack() }
                )
            }
        }

}
