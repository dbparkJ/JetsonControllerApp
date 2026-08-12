package com.example.jetsoncontroller.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.data.network.WifiDirectApiStatus
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.ui.dashboard.DashboardScreen
import com.example.jetsoncontroller.ui.dashboard.DashboardViewModel
import com.example.jetsoncontroller.ui.devices.DeviceListScreen
import com.example.jetsoncontroller.ui.devices.DeviceListViewModel
import com.example.jetsoncontroller.ui.pairing.PairingScreen
import com.example.jetsoncontroller.ui.pairing.PairingViewModel
import com.example.jetsoncontroller.ui.pairing.QrScannerScreen
import com.example.jetsoncontroller.ui.pairing.PairingPhase
import com.example.jetsoncontroller.ui.connection.ConnectionHubScreen
import com.example.jetsoncontroller.ui.network.NetworkSettingsScreen
import com.example.jetsoncontroller.ui.network.NetworkSettingsViewModel
import com.example.jetsoncontroller.ui.wifi.WifiDirectScreen
import com.example.jetsoncontroller.ui.wifi.WifiDirectViewModel
import com.example.jetsoncontroller.ui.storage.DeviceStorageScreen
import com.example.jetsoncontroller.ui.storage.DeviceStorageViewModel
import com.example.jetsoncontroller.ui.upload.UploadConfirmScreen
import com.example.jetsoncontroller.ui.upload.UploadHistoryScreen
import com.example.jetsoncontroller.ui.upload.UploadProgressScreen
import com.example.jetsoncontroller.ui.upload.UploadViewModel
import com.example.jetsoncontroller.ui.pipelines.PipelineEditorScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineListScreen
import com.example.jetsoncontroller.ui.pipelines.PipelinePickerScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineViewModel

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

    const val NETWORK_SETTINGS =
        "network_settings"
        
    const val STORAGE =
        "storage"

    const val UPLOAD_CONFIRM =
        "upload_confirm/{rootId}?path={path}"
        
    const val UPLOAD_PROGRESS =
        "upload_progress"

    const val UPLOAD_HISTORY =
        "upload_history"

    const val PIPELINES =
        "pipelines"

    const val PIPELINE_EDITOR =
        "pipeline_editor"

    const val PIPELINE_PICKER =
        "pipeline_picker"
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
    wifiScanPermissionGranted:
        Boolean,
    localNetworkPermissionGranted:
        Boolean,
    onRequestCameraPermission:
        () -> Unit,
    onRequestNearbyWifiPermission:
        () -> Unit,
    onRequestWifiScanPermission:
        () -> Unit,
    onRequestLocalNetworkPermission:
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

    val networkSettingsViewModel:
        NetworkSettingsViewModel =
        viewModel(
            factory =
                NetworkSettingsViewModel.Factory(
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

    val pipelineViewModel:
        PipelineViewModel =
        viewModel(
            factory =
                PipelineViewModel.Factory(
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

    val networkSettingsState by
        networkSettingsViewModel
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

    val pipelineState by
        pipelineViewModel
            .uiState
            .collectAsStateWithLifecycle()

    val lanEndpoints by
        repository.lanEndpoints.collectAsStateWithLifecycle()

    val lanDiscovering by
        repository.isLanDiscovering.collectAsStateWithLifecycle()

    val lanDiscoveryError by
        repository.lanDiscoveryError.collectAsStateWithLifecycle()

    val lanConnectionError by
        repository.lanConnectionError.collectAsStateWithLifecycle()

    val connectingLanDeviceId by
        repository.connectingLanDeviceId.collectAsStateWithLifecycle()

    val transportState by
        repository.transportState.collectAsStateWithLifecycle()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route


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
        wifiDirectState.apiStatus,
        transportState,
        currentRoute
    ) {
        val connected =
            (deviceState.connectionState is ConnectionState.Ready) ||
            wifiDirectState.apiStatus == WifiDirectApiStatus.READY ||
            transportState is TransportState.Connected
        val connectionRoute = currentRoute == Routes.CONNECTION_HUB ||
            currentRoute == Routes.DEVICES_BLE ||
            currentRoute == Routes.WIFI_DIRECT

        if (connected && connectionRoute) {
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

    LaunchedEffect(pairingState.phase, currentRoute) {
        if (pairingState.phase == PairingPhase.READY && currentRoute == Routes.PAIRING) {
            navController.navigate(Routes.NETWORK_SETTINGS) {
                popUpTo(Routes.CONNECTION_HUB) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(deviceState.connectionState) {
        if (
            deviceState.connectionState
            is ConnectionState.RegistrationRequired
        ) {
            navController.navigate(Routes.QR_SCANNER) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(pipelineState.registrationComplete) {
        if (pipelineState.registrationComplete) {
            navController.popBackStack(Routes.PIPELINES, inclusive = false)
            pipelineViewModel.consumeRegistrationComplete()
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
            DisposableEffect(localNetworkPermissionGranted) {
                if (localNetworkPermissionGranted) {
                    repository.startLanDiscovery()
                }
                onDispose {
                    repository.stopLanDiscovery()
                }
            }

            ConnectionHubScreen(
                onBleClick = { navController.navigate(Routes.DEVICES_BLE) },
                onQrClick = { navController.navigate(Routes.QR_SCANNER) },
                onWifiDirectClick = { navController.navigate(Routes.WIFI_DIRECT) },
                lanEndpoints = lanEndpoints,
                registeredDeviceIds = deviceState.registeredDevices
                    .map { it.deviceId }
                    .toSet(),
                lanDiscovering = lanDiscovering,
                lanError = lanConnectionError ?: lanDiscoveryError,
                connectingLanDeviceId = connectingLanDeviceId,
                localNetworkPermissionGranted = localNetworkPermissionGranted,
                onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                onRefreshLan = { repository.startLanDiscovery() },
                onConnectLan = repository::connectLan
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
                onReconnect = {
                    device ->
                    deviceViewModel.reconnect(device)
                },
                onAddDeviceClick = {
                    navController.navigate(Routes.QR_SCANNER)
                },
                onBack = { navController.popBackStack() }
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
                    if (
                        deviceState.connectionState
                        is ConnectionState.RegistrationRequired
                    ) {
                        pairingViewModel.cancelPairing()
                    }
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
                onConnectClick = { wifiDirectViewModel.connect(it) },
                onRetryApi = { wifiDirectViewModel.retryApi() }
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
                    navController.navigate(Routes.NETWORK_SETTINGS)
                },
                
                onUploadHistoryClick = {
                    navController.navigate(Routes.UPLOAD_HISTORY)
                },

                onPipelinesClick = {
                    navController.navigate(Routes.PIPELINES)
                },

                onDismissOperationMessage =
                    dashboardViewModel::clearOperationMessage,

                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.NETWORK_SETTINGS) {
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
                onSelectAccessPoint = networkSettingsViewModel::selectAccessPoint
            )
        }
        
        composable(Routes.STORAGE) {
            DeviceStorageScreen(
                state = storageState,
                onBack = {
                    if (!storageViewModel.navigateBack()) {
                        navController.popBackStack()
                    }
                },
                onRefresh = storageViewModel::refresh,
                onRootClick = { storageViewModel.selectRoot(it) },
                onDirectoryClick = { storageViewModel.selectDirectory(it) },
                onUploadClick = { rootId, path ->
                    navController.navigate(
                        "upload_confirm/${Uri.encode(rootId)}?path=${Uri.encode(path)}"
                    )
                }
            )
        }

        composable(
            route = Routes.UPLOAD_CONFIRM,
            arguments = listOf(
                navArgument("rootId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val rootId = backStackEntry.arguments?.getString("rootId").orEmpty()
            val path = backStackEntry.arguments?.getString("path").orEmpty()
            UploadConfirmScreen(
                rootId = rootId,
                path = path,
                targets = uploadState.targets,
                isLoading = uploadState.isLoading,
                error = uploadState.error,
                onBack = { navController.popBackStack() },
                onRefresh = uploadViewModel::refresh,
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
                isLoading = uploadState.isLoading,
                error = uploadState.error,
                onCancel = uploadViewModel::cancelCurrentUpload,
                onRetry = uploadViewModel::retryCurrentUpload,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.UPLOAD_HISTORY) {
            UploadHistoryScreen(
                history = uploadState.history,
                isLoading = uploadState.isLoading,
                error = uploadState.error,
                onRefresh = uploadViewModel::refresh,
                onJobClick = { job ->
                    uploadViewModel.openJob(job)
                    navController.navigate(Routes.UPLOAD_PROGRESS)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PIPELINES) {
            PipelineListScreen(
                state = pipelineState,
                onBack = { navController.popBackStack() },
                onRefresh = pipelineViewModel::refresh,
                onAdd = {
                    pipelineViewModel.beginCreate()
                    navController.navigate(Routes.PIPELINE_EDITOR)
                },
                onControl = pipelineViewModel::control,
                onRemove = pipelineViewModel::remove,
                onClearMessage = pipelineViewModel::clearMessage
            )
        }

        composable(Routes.PIPELINE_EDITOR) {
            PipelineEditorScreen(
                state = pipelineState,
                onBack = { navController.popBackStack() },
                onPick = { target ->
                    pipelineViewModel.beginPick(target)
                    navController.navigate(Routes.PIPELINE_PICKER)
                },
                onIdChange = pipelineViewModel::setId,
                onLabelChange = pipelineViewModel::setLabel,
                onWritableDirectoryChange = pipelineViewModel::setWritableDirectory,
                onAutostartChange = pipelineViewModel::setAutostart,
                onRegister = pipelineViewModel::register
            )
        }

        composable(Routes.PIPELINE_PICKER) {
            PipelinePickerScreen(
                roots = pipelineState.roots,
                state = pipelineState.picker,
                onBack = {
                    if (!pipelineViewModel.navigatePickerBack()) {
                        navController.popBackStack()
                    }
                },
                onRefresh = pipelineViewModel::refreshPicker,
                onRootClick = pipelineViewModel::selectPickerRoot,
                onDirectoryClick = pipelineViewModel::openPickerDirectory,
                onFileClick = { entry ->
                    if (pipelineViewModel.selectPickerFile(entry)) {
                        navController.popBackStack()
                    }
                },
                onSelectCurrentDirectory = {
                    if (pipelineViewModel.selectCurrentPickerDirectory()) {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
