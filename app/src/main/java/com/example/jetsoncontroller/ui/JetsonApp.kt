package com.example.jetsoncontroller.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.ui.dashboard.DashboardScreen
import com.example.jetsoncontroller.ui.dashboard.DashboardViewModel
import com.example.jetsoncontroller.ui.devices.DeviceListScreen
import com.example.jetsoncontroller.ui.devices.DeviceListViewModel
import com.example.jetsoncontroller.ui.pairing.PairingScreen
import com.example.jetsoncontroller.ui.pairing.PairingViewModel
import com.example.jetsoncontroller.ui.pairing.QrScannerScreen
import com.example.jetsoncontroller.ui.pairing.PairingPhase
import com.example.jetsoncontroller.ui.connection.ConnectionHubScreen
import com.example.jetsoncontroller.ui.wifi.WifiDirectScreen
import com.example.jetsoncontroller.ui.wifi.WifiDirectViewModel
import com.example.jetsoncontroller.ui.storage.DeviceStorageScreen
import com.example.jetsoncontroller.ui.storage.DeviceStorageViewModel
import com.example.jetsoncontroller.ui.upload.UploadConfirmScreen
import com.example.jetsoncontroller.ui.upload.UploadHistoryScreen
import com.example.jetsoncontroller.ui.upload.UploadProgressScreen
import com.example.jetsoncontroller.ui.upload.UploadViewModel

private object Routes {

    const val CONNECTION_HUB =
        "connection_hub"

    const val DEVICES_BLE =
        "devices_ble"

    const val QR_SCANNER =
        "qr_scanner"

    const val PAIRING =
        "pairing"

    const val WIFI_DIRECT =
        "wifi_direct"

    const val DASHBOARD =
        "dashboard"
        
    const val STORAGE =
        "storage"

    const val UPLOAD_CONFIRM =
        "upload_confirm/{rootId}/{path}"
        
    const val UPLOAD_PROGRESS =
        "upload_progress"

    const val UPLOAD_HISTORY =
        "upload_history"
}


@Composable
fun JetsonApp(
    repository:
        JetsonRepository,
    bluetoothPermissionGranted:
        Boolean,
    cameraPermissionGranted:
        Boolean,
    nearbyWifiPermissionGranted:
        Boolean,
    onRequestCameraPermission:
        () -> Unit,
    onRequestNearbyWifiPermission:
        () -> Unit
) {

    val navController =
        rememberNavController()

    val deviceViewModel:
        DeviceListViewModel =
        viewModel(
            factory =
                DeviceListViewModel.Factory(
                    repository
                )
        )

    val pairingViewModel:
        PairingViewModel =
        viewModel(
            factory =
                PairingViewModel.Factory(
                    repository
                )
        )

    val dashboardViewModel:
        DashboardViewModel =
        viewModel(
            factory =
                DashboardViewModel.Factory(
                    repository
                )
        )
        
    val wifiDirectViewModel:
        WifiDirectViewModel =
        viewModel(
            factory =
                WifiDirectViewModel.Factory(
                    repository
                )
        )
        
    val storageViewModel:
        DeviceStorageViewModel =
        viewModel(
            factory =
                DeviceStorageViewModel.Factory(
                    repository
                )
        )
        
    val uploadViewModel:
        UploadViewModel =
        viewModel(
            factory =
                UploadViewModel.Factory(
                    repository
                )
        )

    val deviceState by
        deviceViewModel
            .uiState
            .collectAsStateWithLifecycle()

    val pairingState by
        pairingViewModel
            .uiState
            .collectAsStateWithLifecycle()

    val dashboardState by
        dashboardViewModel
            .uiState
            .collectAsStateWithLifecycle()
            
    val wifiDirectState by
        wifiDirectViewModel
            .uiState
            .collectAsStateWithLifecycle()
            
    val storageState by
        storageViewModel
            .uiState
            .collectAsStateWithLifecycle()
            
    val uploadState by
        uploadViewModel
            .uiState
            .collectAsStateWithLifecycle()


    LaunchedEffect(
        bluetoothPermissionGranted
    ) {

        deviceViewModel
            .onPermissionResult(
                bluetoothPermissionGranted
            )
    }


    LaunchedEffect(
        deviceState.connectionState,
        pairingState.phase
    ) {

        if (
            deviceState
                .connectionState
                is ConnectionState.Ready ||
            pairingState.phase == PairingPhase.READY
        ) {

            navController.navigate(
                Routes.DASHBOARD
            ) {

                popUpTo(Routes.CONNECTION_HUB) {
                    inclusive = false
                }
                launchSingleTop =
                    true
            }
        }
    }


    NavHost(
        navController =
            navController,
        startDestination =
            Routes.CONNECTION_HUB
    ) {

        composable(
            Routes.CONNECTION_HUB
        ) {
            ConnectionHubScreen(
                onBleClick = { navController.navigate(Routes.DEVICES_BLE) },
                onQrClick = { navController.navigate(Routes.QR_SCANNER) },
                onWifiDirectClick = { navController.navigate(Routes.WIFI_DIRECT) }
            )
        }

        composable(
            Routes.DEVICES_BLE
        ) {

            DeviceListScreen(
                state =
                    deviceState,
                onScanClick = {
                    deviceViewModel
                        .toggleScan()
                },
                onConnect = {
                    device ->
                    deviceViewModel
                        .connect(
                            device
                        )
                },
                onAddDeviceClick = {
                    navController.navigate(Routes.QR_SCANNER)
                }
            )
        }

        composable(
            Routes.QR_SCANNER
        ) {
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
                        navController.navigate(Routes.PAIRING)
                    }

                    accepted
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            Routes.PAIRING
        ) {
            PairingScreen(
                state = pairingState,
                onStartPairing = {
                    pairingViewModel.startPairing()
                },
                onCancel = {
                    pairingViewModel.cancelPairing()
                    navController.popBackStack(Routes.CONNECTION_HUB, false)
                },
                onRetry = {
                    pairingViewModel.retry()
                }
            )
        }
        
        composable(
            Routes.WIFI_DIRECT
        ) {
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
                onBack = { navController.popBackStack() },
                onPermissionClick = onRequestNearbyWifiPermission,
                onDiscoveryClick = {
                    if (nearbyWifiPermissionGranted) {
                        wifiDirectViewModel.startDiscovery()
                    } else {
                        onRequestNearbyWifiPermission()
                    }
                },
                onConnectClick = { wifiDirectViewModel.connect(it) }
            )
        }


        composable(
            Routes.DASHBOARD
        ) {

            DashboardScreen(
                state =
                    dashboardState,

                onDisconnect = {

                    dashboardViewModel
                        .disconnect()

                    navController
                        .popBackStack(
                            Routes.CONNECTION_HUB,
                            inclusive = false
                        )
                },

                onStartSystem = {
                    dashboardViewModel
                        .startSystem()
                },

                onStopSystem = {
                    dashboardViewModel
                        .stopSystem()
                },

                onRestartServices = {
                    dashboardViewModel
                        .restartServices()
                },

                onReboot = {
                    dashboardViewModel
                        .reboot()
                },

                onShutdown = {
                    dashboardViewModel
                        .shutdown()
                },
                
                onStorageClick = {
                    navController.navigate(Routes.STORAGE)
                },
                
                onNetworkSettingsClick = {
                    // TODO: Provisioning
                },
                
                onUploadHistoryClick = {
                    navController.navigate(Routes.UPLOAD_HISTORY)
                }
            )
        }
        
        composable(Routes.STORAGE) {
            DeviceStorageScreen(
                state = storageState,
                onBack = { storageViewModel.navigateBack() },
                onRootClick = { storageViewModel.selectRoot(it) },
                onDirectoryClick = { storageViewModel.selectDirectory(it) },
                onUploadClick = { rootId, path ->
                    navController.navigate(Routes.UPLOAD_CONFIRM.replace("{rootId}", rootId).replace("{path}", if (path.isEmpty()) "_" else path))
                }
            )
        }

        composable(Routes.UPLOAD_CONFIRM) { backStackEntry ->
            val rootId = backStackEntry.arguments?.getString("rootId") ?: ""
            val path = backStackEntry.arguments?.getString("path")?.replace("_", "") ?: ""
            UploadConfirmScreen(
                rootId = rootId,
                path = path,
                targets = uploadState.targets,
                onBack = { navController.popBackStack() },
                onConfirm = { targetId ->
                    uploadViewModel.startUpload(rootId, path, targetId)
                    navController.navigate(Routes.UPLOAD_PROGRESS) {
                        popUpTo(Routes.STORAGE) { inclusive = false }
                    }
                }
            )
        }
        
        composable(Routes.UPLOAD_PROGRESS) {
            UploadProgressScreen(
                job = uploadState.currentJob,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.UPLOAD_HISTORY) {
            UploadHistoryScreen(
                history = uploadState.history,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
