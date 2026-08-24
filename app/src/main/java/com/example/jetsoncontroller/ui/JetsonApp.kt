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
import com.example.jetsoncontroller.data.alerts.AlertDestination
import com.example.jetsoncontroller.data.alerts.AlertHistoryStore
import com.example.jetsoncontroller.data.alerts.AlertPreferencesStore
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.data.transport.canStartServerUpload
import com.example.jetsoncontroller.data.transport.serverUploadUnavailableMessage
import com.example.jetsoncontroller.ui.alerts.AlertCenterScreen
import com.example.jetsoncontroller.ui.alerts.AlertCenterViewModel
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
import com.example.jetsoncontroller.ui.sensors.CameraPreviewScreen
import com.example.jetsoncontroller.ui.sensors.CameraPreviewViewModel
import com.example.jetsoncontroller.ui.sensors.GnssMapScreen
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

    const val CAMERA_PREVIEW = "camera_preview"

    const val GNSS_MAP = "gnss_map"

    const val SETTINGS = "settings"

    const val ALERTS = "alerts"
}

private val routesRequiringDeviceConnection = setOf(
    Routes.NETWORK_SETTINGS,
    Routes.STORAGE_ROUTE,
    Routes.SERVER_STORAGE,
    Routes.UPLOAD_CONFIRM,
    Routes.UPLOAD_PROGRESS,
    Routes.UPLOAD_QUEUE,
    Routes.UPLOAD_SERVERS,
    Routes.PIPELINES,
    Routes.PIPELINE_EDITOR,
    Routes.PIPELINE_PICKER,
    Routes.PIPELINE_LOGS,
    Routes.PIPELINE_CONFIG,
    Routes.CAMERA_PREVIEW,
    Routes.SETTINGS
)


@Composable
fun JetsonApp(
    repository:
        JetsonRepository,
    alertPreferences: AlertPreferencesStore,
    alertHistory: AlertHistoryStore,
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
    onRequestBluetoothPermission:
        () -> Unit,
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

    val cameraPreviewViewModel: CameraPreviewViewModel =
        viewModel(factory = CameraPreviewViewModel.Factory(repository))

    val alertSettingsViewModel: AlertSettingsViewModel =
        viewModel(factory = AlertSettingsViewModel.Factory(alertPreferences))

    val alertCenterViewModel: AlertCenterViewModel =
        viewModel(factory = AlertCenterViewModel.Factory(alertHistory))

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

    val cameraPreviewState by
        cameraPreviewViewModel
            .uiState
            .collectAsStateWithLifecycle()

    val alertSettings by
        alertSettingsViewModel.settings.collectAsStateWithLifecycle()

    val dashboardHealthDismissals by
        alertSettingsViewModel.dashboardHealthDismissals.collectAsStateWithLifecycle()

    val alertCenterState by
        alertCenterViewModel.uiState.collectAsStateWithLifecycle()

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
    var pendingDashboardTransport by remember { mutableStateOf<TransportType?>(null) }
    val connectedTransportType =
        (transportState as? TransportState.Connected)?.type
    val serverUploadEnabled = canStartServerUpload(connectedTransportType)
    val serverUploadDisabledReason = serverUploadUnavailableMessage(connectedTransportType)
    val fullControlConnected = connectedTransportType == TransportType.LAN ||
        connectedTransportType == TransportType.WIFI_DIRECT

    val onSectionSelected: (ControlSection) -> Unit = onSectionSelected@ { section ->
        if (
            section != ControlSection.OVERVIEW && section != ControlSection.SENSORS &&
            !fullControlConnected
        ) {
            return@onSectionSelected
        }
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
        bluetoothPermissionGranted,
        nearbyWifiPermissionGranted,
        localNetworkPermissionGranted
    ) {
        repository.configureAutomaticConnectivity(
            enabled = true,
            localNetworkPermissionGranted = localNetworkPermissionGranted,
            nearbyWifiPermissionGranted = nearbyWifiPermissionGranted,
            bluetoothPermissionGranted = bluetoothPermissionGranted
        )
    }


    LaunchedEffect(transportState, pendingDashboardTransport) {
        if (connectionAttemptCompleted(pendingDashboardTransport, transportState)) {
            pendingDashboardTransport = null
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
            if (pendingDashboardTransport == TransportType.LAN) {
                pendingDashboardTransport = null
            }
        }
    }

    LaunchedEffect(pairingState.phase, currentRoute) {
        if (pairingState.phase == PairingPhase.READY && currentRoute == Routes.PAIRING) {
            navController.navigate(Routes.DASHBOARD) {
                popUpTo(Routes.CONNECTION_HUB) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(transportState, currentRoute) {
        if (
            (transportState is TransportState.Disconnected || transportState is TransportState.Error) &&
            currentRoute in routesRequiringDeviceConnection
        ) {
            navController.navigate(Routes.CONNECTION_HUB) {
                popUpTo(Routes.CONNECTION_HUB) { inclusive = false }
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
            ConnectionHubScreen(
                onAddDevice = { navController.navigate(Routes.ONBOARDING) },
                onOpenDashboard = { navController.navigate(Routes.DASHBOARD) },
                unreadAlertCount = alertCenterState.unreadCount,
                onAlertsClick = { navController.navigate(Routes.ALERTS) },
                lanEndpoints = lanEndpoints,
                registeredDevices = deviceState.registeredDevices,
                transportState = transportState,
                lanDiscovering = lanDiscovering,
                lanError = lanConnectionError ?: lanDiscoveryError,
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
                    navController.navigate(Routes.DASHBOARD) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            FirstDeviceOnboardingScreen(
                onScanQr = {
                    pairingViewModel.beginQrPairing()
                    navController.navigate(Routes.QR_SCANNER)
                },
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
                    navController.navigate(Routes.QR_SCANNER)
                },
                onBack = {
                    if (pendingDashboardTransport == TransportType.BLE) {
                        pendingDashboardTransport = null
                    }
                    navController.popBackStack()
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
                    pairingViewModel.cancelPairing()
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
                    if (bluetoothPermissionGranted) {
                        pairingViewModel.startPairing()
                    } else {
                        onRequestBluetoothPermission()
                    }
                },
                onCancel = {
                    pairingViewModel.cancelPairing()
                    navController.popBackStack(Routes.CONNECTION_HUB, false)
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

                unreadAlertCount = alertCenterState.unreadCount,

                healthDeviceId = (transportState as? TransportState.Connected)
                    ?.deviceId
                    ?: dashboardState.deviceName,

                dismissedHealthKeys = dashboardHealthDismissals,

                onHealthDismissalsChange =
                    alertSettingsViewModel::replaceDashboardHealthDismissals,

                onAlertsClick = { navController.navigate(Routes.ALERTS) },

                onDisconnect = {

                    dashboardViewModel
                        .disconnect()

                    pendingDashboardTransport = null
                    if (!navController.popBackStack(Routes.CONNECTION_HUB, inclusive = false)) {
                        navController.navigate(Routes.CONNECTION_HUB) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    }
                },

                onRefreshFan = dashboardViewModel::refreshFan,

                onSetFanAuto = dashboardViewModel::setFanAuto,

                onSetFanManual = dashboardViewModel::setFanManual,

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

                onUploadQueueClick = {
                    navController.navigate(Routes.UPLOAD_QUEUE)
                },

                onPipelinesClick = {
                    navController.navigate(Routes.PIPELINES)
                },

                onSectionSelected = onSectionSelected,

                onDismissOperationMessage =
                    dashboardViewModel::clearOperationMessage,

                onBack = {
                    pendingDashboardTransport = null
                    if (!navController.popBackStack(Routes.CONNECTION_HUB, inclusive = false)) {
                        navController.navigate(Routes.CONNECTION_HUB) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(Routes.ALERTS) {
            AlertCenterScreen(
                state = alertCenterState,
                onBack = { navController.popBackStack() },
                onAlertClick = { alert ->
                    alertCenterViewModel.markRead(alert.id)
                    navController.navigate(alertDestinationRoute(alert.destination)) {
                        popUpTo(Routes.ALERTS) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onDelete = alertCenterViewModel::delete,
                onMarkAllRead = alertCenterViewModel::markAllRead,
                onClear = alertCenterViewModel::clear
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
                serverUploadEnabled = serverUploadEnabled,
                serverUploadDisabledReason = serverUploadDisabledReason,
                onBack = {
                    if (!storageViewModel.navigateBack()) {
                        navController.popBackStack()
                    }
                },
                onRefresh = storageViewModel::refresh,
                onDirectoryClick = { storageViewModel.selectDirectory(it) },
                onFileClick = storageViewModel::openFile,
                onDeleteClick = storageViewModel::deleteEntry,
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
                onDeleteSession = serverStorageViewModel::deleteSession,
                onDirectoryClick = serverStorageViewModel::openDirectory,
                onFileClick = serverStorageViewModel::openFile,
                onLoadMore = serverStorageViewModel::loadMoreSessions,
                onSectionSelected = onSectionSelected,
                deletionEnabled = fullControlConnected
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
            LaunchedEffect(rootId, path) {
                uploadViewModel.loadSourceSummary(rootId, path)
            }
            UploadConfirmScreen(
                rootId = rootId,
                path = path,
                targets = uploadState.targets,
                sourceSummary = uploadState.sourceSummary,
                isCalculatingSource = uploadState.isCalculatingSource,
                serverUploadEnabled = serverUploadEnabled,
                serverUploadDisabledReason = serverUploadDisabledReason,
                isLoading = uploadState.isLoading,
                error = uploadState.error,
                onBack = { navController.popBackStack() },
                onRefresh = {
                    uploadViewModel.refresh()
                    uploadViewModel.loadSourceSummary(rootId, path, force = true)
                },
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
                verification = uploadState.verification,
                isLoading = uploadState.isLoading,
                message = uploadState.message,
                error = uploadState.error,
                onCancel = uploadViewModel::cancelCurrentUpload,
                onRetry = uploadViewModel::retryCurrentUpload,
                onVerify = uploadViewModel::verifyCurrentUpload,
                onDeleteSource = uploadViewModel::deleteCurrentSource,
                onBack = { navController.popBackStack() },
                serverMutationEnabled = serverUploadEnabled,
                serverMutationDisabledReason = serverUploadDisabledReason,
                deviceDeletionEnabled = fullControlConnected
            )
        }

        composable(Routes.UPLOAD_QUEUE) {
            UploadQueueScreen(
                queue = uploadState.queue,
                targets = uploadState.targets,
                isLoading = uploadState.isLoading,
                error = uploadState.error,
                message = uploadState.message,
                onRefresh = uploadViewModel::loadQueue,
                onManageTargets = {
                    navController.navigate(Routes.UPLOAD_SERVERS)
                },
                onJobClick = { job ->
                    uploadViewModel.openJob(job)
                    navController.navigate(Routes.UPLOAD_PROGRESS)
                },
                onDeleteJob = uploadViewModel::deleteJobFromQueue,
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
            StatusPollingLifecycleEffect(dashboardViewModel)
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
                onLabelChange = pipelineViewModel::setLabel,
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
            DisposableEffect(pipelineId) {
                pipelineViewModel.startLogStreaming(pipelineId)
                onDispose { pipelineViewModel.stopLogStreaming() }
            }
            PipelineLogScreen(
                state = pipelineState,
                onBack = { navController.popBackStack() },
                onRefresh = pipelineViewModel::refreshLogs,
                onLogSelected = pipelineViewModel::selectLogFile
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
                deviceOnline = transportState is TransportState.Connected,
                fullControlAvailable = fullControlConnected,
                onCameraClick = { navController.navigate(Routes.CAMERA_PREVIEW) },
                onGnssClick = { navController.navigate(Routes.GNSS_MAP) },
                onSectionSelected = onSectionSelected
            )
        }

        composable(Routes.CAMERA_PREVIEW) {
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
                onRefresh = cameraPreviewViewModel::refresh
            )
        }

        composable(Routes.GNSS_MAP) {
            StatusPollingLifecycleEffect(dashboardViewModel)
            GnssMapScreen(
                gnss = dashboardState.status.gnssSensor,
                telemetryFresh = dashboardState.status.sensorTelemetryFresh,
                deviceOnline = transportState is TransportState.Connected,
                onBack = { navController.popBackStack() }
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

internal fun connectionAttemptCompleted(
    expectedTransport: TransportType?,
    transportState: TransportState
): Boolean = expectedTransport != null &&
    transportState is TransportState.Connected &&
    transportState.type == expectedTransport

private fun alertDestinationRoute(destination: AlertDestination): String = when (destination) {
    AlertDestination.DASHBOARD -> Routes.DASHBOARD
    AlertDestination.STORAGE -> Routes.STORAGE
    AlertDestination.SENSORS -> Routes.SENSORS
    AlertDestination.PIPELINES -> Routes.PIPELINES
    AlertDestination.UPLOAD_QUEUE -> Routes.UPLOAD_QUEUE
}
