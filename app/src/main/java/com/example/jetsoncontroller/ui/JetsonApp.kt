package com.example.jetsoncontroller.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.alerts.AlertPreferencesStore
import com.example.jetsoncontroller.model.ConnectionState
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
import com.example.jetsoncontroller.ui.onboarding.FirstDeviceOnboardingScreen
import com.example.jetsoncontroller.ui.network.NetworkSettingsScreen
import com.example.jetsoncontroller.ui.network.NetworkSettingsViewModel
import com.example.jetsoncontroller.ui.wifi.WifiDirectScreen
import com.example.jetsoncontroller.ui.wifi.WifiDirectViewModel
import com.example.jetsoncontroller.ui.storage.DeviceStorageScreen
import com.example.jetsoncontroller.ui.storage.DeviceStorageViewModel
import com.example.jetsoncontroller.ui.storage.ServerStorageScreen
import com.example.jetsoncontroller.ui.storage.ServerStorageViewModel
import com.example.jetsoncontroller.ui.upload.UploadConfirmScreen
import com.example.jetsoncontroller.ui.upload.UploadProgressScreen
import com.example.jetsoncontroller.ui.upload.UploadQueueScreen
import com.example.jetsoncontroller.ui.upload.UploadTargetSettingsScreen
import com.example.jetsoncontroller.ui.upload.UploadViewModel
import com.example.jetsoncontroller.ui.pipelines.PipelineEditorScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineConfigScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineListScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineLogScreen
import com.example.jetsoncontroller.ui.pipelines.PipelinePickerScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineViewModel
import com.example.jetsoncontroller.ui.sensors.SensorScreen
import com.example.jetsoncontroller.ui.settings.AlertSettingsScreen
import com.example.jetsoncontroller.ui.settings.AlertSettingsViewModel
import com.example.jetsoncontroller.ui.components.ControlSection

private object Routes {

    const val CONNECTION_HUB =
        "connection_hub"

    const val ONBOARDING =
        "onboarding"

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
        
    const val STORAGE = "storage"

    const val SERVER_STORAGE = "server_storage"

    const val STORAGE_ROUTE =
        "storage?rootId={rootId}&path={path}"

    const val UPLOAD_CONFIRM =
        "upload_confirm/{rootId}?path={path}"
        
    const val UPLOAD_PROGRESS =
        "upload_progress"

    const val UPLOAD_QUEUE =
        "upload_queue"

    const val UPLOAD_SERVERS =
        "upload_servers"

    const val PIPELINES =
        "pipelines"

    const val PIPELINE_EDITOR =
        "pipeline_editor"

    const val PIPELINE_PICKER =
        "pipeline_picker"

    const val PIPELINE_LOGS =
        "pipeline_logs/{pipelineId}"

    const val PIPELINE_CONFIG =
        "pipeline_config/{pipelineId}"

    const val SENSORS = "sensors"

    const val SETTINGS = "settings"
}


@Composable
fun JetsonApp(
    repository:
        JetsonRepository,
    alertPreferences: AlertPreferencesStore,
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
    notificationPermissionGranted:
        Boolean,
    onRequestCameraPermission:
        () -> Unit,
    onRequestNearbyWifiPermission:
        () -> Unit,
    onRequestWifiScanPermission:
        () -> Unit,
    onRequestLocalNetworkPermission:
        () -> Unit,
    onRequestNotificationPermission:
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

    val serverStorageViewModel:
        ServerStorageViewModel =
        viewModel(
            factory =
                ServerStorageViewModel.Factory(
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

    val alertSettingsViewModel: AlertSettingsViewModel =
        viewModel(factory = AlertSettingsViewModel.Factory(alertPreferences))

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

    val serverStorageState by
        serverStorageViewModel
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

    val alertSettings by
        alertSettingsViewModel.settings.collectAsStateWithLifecycle()

    val lanEndpoints by
        repository.lanEndpoints.collectAsStateWithLifecycle()

    val lanDiscovering by
        repository.isLanDiscovering.collectAsStateWithLifecycle()

    val lanLastSeenAtEpochMillis by
        repository.lanLastSeenAtEpochMillis.collectAsStateWithLifecycle()

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
    var openDashboardAfterHubConnection by remember { mutableStateOf(false) }

    val onSectionSelected: (ControlSection) -> Unit = { section ->
        val route = when (section) {
            ControlSection.OVERVIEW -> Routes.DASHBOARD
            ControlSection.DATA -> Routes.STORAGE
            ControlSection.PIPELINES -> Routes.PIPELINES
            ControlSection.SENSORS -> Routes.SENSORS
            ControlSection.SETTINGS -> Routes.SETTINGS
        }
        navController.navigate(route) {
            popUpTo(Routes.DASHBOARD) { inclusive = false }
            launchSingleTop = true
        }
    }


    LaunchedEffect(
        bluetoothPermissionGranted
    ) {

        deviceViewModel
            .onPermissionResult(
                bluetoothPermissionGranted
            )
    }


    LaunchedEffect(
        transportState,
        currentRoute
    ) {
        val connectedTransport = transportState as? TransportState.Connected
        val connected = connectedTransport != null
        val connectionRoute = isConnectionEntryRoute(currentRoute) ||
            (
                currentRoute == Routes.CONNECTION_HUB &&
                    (openDashboardAfterHubConnection || connectedTransport?.type ==
                        com.example.jetsoncontroller.data.transport.TransportType.LAN)
            )

        if (connected && connectionRoute) {
            openDashboardAfterHubConnection = false
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

    LaunchedEffect(lanConnectionError) {
        if (lanConnectionError != null) {
            openDashboardAfterHubConnection = false
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
            navController.navigate(Routes.ONBOARDING) {
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
                onAddDevice = { navController.navigate(Routes.ONBOARDING) },
                onOpenDashboard = { navController.navigate(Routes.DASHBOARD) },
                onWifiDirectClick = { navController.navigate(Routes.WIFI_DIRECT) },
                lanEndpoints = lanEndpoints,
                lastSeenAtEpochMillis = lanLastSeenAtEpochMillis,
                registeredDevices = deviceState.registeredDevices,
                transportState = transportState,
                lanDiscovering = lanDiscovering,
                lanError = lanConnectionError ?: lanDiscoveryError,
                connectingLanDeviceId = connectingLanDeviceId,
                localNetworkPermissionGranted = localNetworkPermissionGranted,
                onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                onRefreshLan = { repository.startLanDiscovery() },
                onConnectLan = { endpoint ->
                    openDashboardAfterHubConnection = true
                    repository.connectLan(endpoint)
                }
            )
        }

        composable(Routes.ONBOARDING) {
            FirstDeviceOnboardingScreen(
                onScanQr = { navController.navigate(Routes.QR_SCANNER) },
                onBack = { navController.popBackStack() }
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
                onForget = {
                    device ->
                    deviceViewModel.forget(device)
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
            StatusPollingLifecycleEffect(dashboardViewModel)

            DashboardScreen(
                state =
                    dashboardState,

                pipelines = pipelineState.pipelines,

                uploads = uploadState.queue,

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

                onWifiDirectClick = {
                    navController.navigate(Routes.WIFI_DIRECT)
                },
                
                onUploadQueueClick = {
                    navController.navigate(Routes.UPLOAD_QUEUE)
                },

                onPipelinesClick = {
                    navController.navigate(Routes.PIPELINES)
                },

                onSectionSelected = onSectionSelected,

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
                onSelectAccessPoint = networkSettingsViewModel::selectAccessPoint,
                onWifiDirectClick = { navController.navigate(Routes.WIFI_DIRECT) }
            )
        }
        
        composable(
            route = Routes.STORAGE_ROUTE,
            arguments = listOf(
                navArgument("rootId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val rootId = backStackEntry.arguments?.getString("rootId").orEmpty()
            val initialPath = backStackEntry.arguments?.getString("path").orEmpty()
            LaunchedEffect(rootId, initialPath) {
                if (rootId.isNotEmpty()) {
                    storageViewModel.openLocation(rootId, initialPath)
                } else {
                    storageViewModel.openCollection()
                }
            }
            DeviceStorageScreen(
                state = storageState,
                onBack = {
                    if (!storageViewModel.navigateBack()) {
                        navController.popBackStack()
                    }
                },
                onRefresh = storageViewModel::refresh,
                onDirectoryClick = { storageViewModel.selectDirectory(it) },
                onFileClick = storageViewModel::openFile,
                onUploadClick = { rootId, path ->
                    navController.navigate(
                        "upload_confirm/${Uri.encode(rootId)}?path=${Uri.encode(path)}"
                    )
                },
                onSectionSelected = onSectionSelected,
                onServerDataClick = {
                    navController.navigate(Routes.SERVER_STORAGE) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.SERVER_STORAGE) {
            ServerStorageScreen(
                state = serverStorageState,
                onBack = {
                    if (!serverStorageViewModel.navigateBack()) {
                        navController.popBackStack()
                    }
                },
                onDeviceDataClick = {
                    navController.navigate(Routes.STORAGE) {
                        launchSingleTop = true
                    }
                },
                onRefresh = serverStorageViewModel::refresh,
                onTargetSelected = serverStorageViewModel::selectTarget,
                onSessionClick = serverStorageViewModel::openSession,
                onDirectoryClick = serverStorageViewModel::openDirectory,
                onFileClick = serverStorageViewModel::openFile,
                onLoadMore = serverStorageViewModel::loadMoreSessions,
                onSectionSelected = onSectionSelected
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
                onManageTargets = {
                    navController.navigate(Routes.UPLOAD_SERVERS)
                },
                onConfirm = { targetId ->
                    uploadViewModel.startUpload(rootId, path, targetId)
                    navController.navigate(Routes.UPLOAD_PROGRESS) {
                        popUpTo(Routes.STORAGE_ROUTE) { inclusive = false }
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

        composable(Routes.UPLOAD_QUEUE) {
            UploadQueueScreen(
                queue = uploadState.queue,
                targets = uploadState.targets,
                isLoading = uploadState.isLoading,
                error = uploadState.error,
                onRefresh = uploadViewModel::loadQueue,
                onManageTargets = {
                    navController.navigate(Routes.UPLOAD_SERVERS)
                },
                onJobClick = { job ->
                    uploadViewModel.openJob(job)
                    navController.navigate(Routes.UPLOAD_PROGRESS)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.UPLOAD_SERVERS) {
            UploadTargetSettingsScreen(
                targets = uploadState.targets,
                isLoading = uploadState.isLoading || uploadState.isSavingTarget,
                message = uploadState.message,
                error = uploadState.error,
                onSave = uploadViewModel::saveTarget,
                onDelete = uploadViewModel::deleteTarget,
                onRefresh = uploadViewModel::refreshTargets,
                onClearFeedback = uploadViewModel::clearFeedback,
                onBack = {
                    uploadViewModel.clearFeedback()
                    navController.popBackStack()
                }
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
                onLogs = { pipeline ->
                    navController.navigate("pipeline_logs/${Uri.encode(pipeline.id)}")
                },
                onConfig = { pipeline ->
                    navController.navigate("pipeline_config/${Uri.encode(pipeline.id)}")
                },
                onOutput = { pipeline ->
                    val rootId = pipeline.outputRootId ?: return@PipelineListScreen
                    val path = pipeline.outputPath ?: return@PipelineListScreen
                    navController.navigate(
                        "storage?rootId=${Uri.encode(rootId)}&path=${Uri.encode(path)}"
                    )
                },
                onSectionSelected = onSectionSelected,
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

        composable(
            route = Routes.PIPELINE_LOGS,
            arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pipelineId = backStackEntry.arguments?.getString("pipelineId").orEmpty()
            LaunchedEffect(pipelineId) { pipelineViewModel.loadLogs(pipelineId) }
            PipelineLogScreen(
                state = pipelineState,
                onBack = { navController.popBackStack() },
                onRefresh = { pipelineViewModel.loadLogs(pipelineId) }
            )
        }

        composable(
            route = Routes.PIPELINE_CONFIG,
            arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pipelineId = backStackEntry.arguments?.getString("pipelineId").orEmpty()
            LaunchedEffect(pipelineId) { pipelineViewModel.loadConfig(pipelineId) }
            PipelineConfigScreen(
                state = pipelineState,
                onBack = { navController.popBackStack() },
                onValueChange = pipelineViewModel::setConfigValue,
                onSave = pipelineViewModel::saveConfig
            )
        }

        composable(Routes.SENSORS) {
            StatusPollingLifecycleEffect(dashboardViewModel)
            SensorScreen(
                status = dashboardState.status,
                onSectionSelected = onSectionSelected
            )
        }

        composable(Routes.SETTINGS) {
            AlertSettingsScreen(
                settings = alertSettings,
                notificationPermissionGranted = notificationPermissionGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onStorageEnabledChange = alertSettingsViewModel::setStorageEnabled,
                onStorageThresholdChange = alertSettingsViewModel::setStorageThreshold,
                onTemperatureEnabledChange = alertSettingsViewModel::setTemperatureEnabled,
                onTemperatureThresholdChange = alertSettingsViewModel::setTemperatureThreshold,
                onPipelineStartedEnabledChange =
                    alertSettingsViewModel::setPipelineStartedEnabled,
                onPipelineFailedEnabledChange =
                    alertSettingsViewModel::setPipelineFailedEnabled,
                onUploadStartedEnabledChange =
                    alertSettingsViewModel::setUploadStartedEnabled,
                onUploadEndedEnabledChange =
                    alertSettingsViewModel::setUploadEndedEnabled,
                onSectionSelected = onSectionSelected
            )
        }
    }
}

@Composable
private fun StatusPollingLifecycleEffect(viewModel: DashboardViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setVisible(true)
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> viewModel.setVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setVisible(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setVisible(false)
        }
    }
}

internal fun isConnectionEntryRoute(route: String?): Boolean = route in setOf(
    "devices_ble",
    "wifi_direct"
)
