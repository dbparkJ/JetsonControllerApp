package com.example.jetsoncontroller.ui

import android.net.Uri
import com.example.jetsoncontroller.ui.components.ConnectionRecoveryLayout
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
import androidx.navigation.NavHostController
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
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
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
import com.example.jetsoncontroller.ui.survey.SurveyRunScreen
import com.example.jetsoncontroller.ui.survey.SurveyRunViewModel
import com.example.jetsoncontroller.ui.sensors.SensorScreen
import com.example.jetsoncontroller.ui.sensors.CameraPreviewScreen
import com.example.jetsoncontroller.ui.sensors.CameraPreviewViewModel
import com.example.jetsoncontroller.ui.sensors.GnssMapScreen
import com.example.jetsoncontroller.ui.settings.AlertSettingsScreen
import com.example.jetsoncontroller.ui.diagnostics.ConnectionDiagnosticsScreen
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
    const val SERVER_PROXY_STORAGE = "server_proxy_storage"

    const val STORAGE_ROUTE =
        "storage?rootId={rootId}&path={path}"

    const val UPLOAD_CONFIRM =
        "upload_confirm/{rootId}?path={path}&runId={runId}"
        
    const val UPLOAD_PROGRESS =
        "upload_progress"

    const val UPLOAD_QUEUE =
        "upload_queue"

    const val UPLOAD_SERVERS =
        "upload_servers"

    const val PIPELINES =
        "pipelines"
    const val TASK_HISTORY = "task_history"

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
    const val ADMIN_TOOLS = "admin_tools"
    const val ALERT_SETTINGS = "alert_settings"
    const val DATA = "data_hub"
    const val LOCAL_TRASH = "local_trash"
    const val PIPELINE_DETAIL = "pipeline_detail/{pipelineId}"
    const val SURVEY_RUN = "survey_run/{pipelineId}"

    // 업무 단계형 IA에서 새로 생긴 두 목적지.
    // 이전 구조에는 '진행 중인 수집' 과 '종료 후 결과 요약' 에 해당하는 라우트가 없어서,
    // 시작을 누른 직원이 갈 곳이 작업 목록밖에 없었습니다.
    const val ACTIVE_RUN = "active_run"
    const val RUN_RESULT = "run_result"

    const val DIAGNOSTICS = "connection_diagnostics"

    const val ALERTS = "alerts"
}

private val routesRequiringDeviceConnection = setOf(
    Routes.NETWORK_SETTINGS,
    Routes.STORAGE_ROUTE,
    Routes.SERVER_PROXY_STORAGE,
    Routes.UPLOAD_CONFIRM,
    Routes.UPLOAD_PROGRESS,
    Routes.UPLOAD_SERVERS,
    Routes.PIPELINES,
    Routes.PIPELINE_EDITOR,
    Routes.PIPELINE_PICKER,
    Routes.PIPELINE_LOGS,
    Routes.PIPELINE_CONFIG,
    Routes.SURVEY_RUN,
    Routes.ACTIVE_RUN,
    Routes.LOCAL_TRASH,
    Routes.CAMERA_PREVIEW
)
// RUN_RESULT 은 의도적으로 제외합니다. 연결이 끊겨도 마지막으로 확인한 저장 결과는
// 볼 수 있어야 하고, 그 화면이 스스로 "마지막으로 확인한 값" 이라고 말합니다.


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

    val fieldContext = androidx.compose.ui.platform.LocalContext.current
    val fieldToolsViewModel: com.example.jetsoncontroller.ui.field.FieldToolsViewModel = viewModel(
        factory = com.example.jetsoncontroller.ui.field.FieldToolsViewModel.Factory(repository))
    val fieldState by fieldToolsViewModel.state.collectAsStateWithLifecycle()

    val directServerViewModel: com.example.jetsoncontroller.ui.storage.DirectServerViewModel =
        viewModel(factory = com.example.jetsoncontroller.ui.storage.DirectServerViewModel.Factory(fieldContext))
    val serverStorageViewModel: ServerStorageViewModel =
        viewModel(factory = ServerStorageViewModel.Factory(repository))
    val localTrashViewModel: com.example.jetsoncontroller.ui.storage.LocalTrashViewModel =
        viewModel(factory = com.example.jetsoncontroller.ui.storage.LocalTrashViewModel.Factory(repository))
        
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

    val surveyRunViewModel: SurveyRunViewModel = viewModel(
        factory = SurveyRunViewModel.Factory(repository, fieldContext)
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

    val directServerState by directServerViewModel.uiState.collectAsStateWithLifecycle()
    val serverStorageState by serverStorageViewModel.uiState.collectAsStateWithLifecycle()
    val localTrashState by localTrashViewModel.state.collectAsStateWithLifecycle()
            
    val rawUploadState by
        uploadViewModel
            .uiState
            .collectAsStateWithLifecycle()

    val rawPipelineState by
        pipelineViewModel
            .uiState
            .collectAsStateWithLifecycle()

    val surveyRunState by surveyRunViewModel.uiState.collectAsStateWithLifecycle()

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

    val selectedDeviceId by repository.selectedDeviceId.collectAsStateWithLifecycle()
    // Do not briefly label A's retained state as B while ViewModel collectors switch workspaces.
    val pipelineState = rawPipelineState.takeIf { it.deviceId.equals(selectedDeviceId, true) }
        ?: com.example.jetsoncontroller.ui.pipelines.PipelineUiState(deviceId = selectedDeviceId)
    val uploadState = rawUploadState.takeIf { it.deviceId.equals(selectedDeviceId, true) }
        ?: com.example.jetsoncontroller.ui.upload.UploadUiState(deviceId = selectedDeviceId)
    val selectedDeviceName = deviceState.registeredDevices.firstOrNull {
        it.deviceId.equals(selectedDeviceId, ignoreCase = true)
    }?.deviceName ?: if (selectedDeviceId == null) "선택된 장비 없음" else selectedDeviceId.orEmpty()
    val deviceDashboardState = dashboardState.copy(deviceName = selectedDeviceName)
    val deviceUiState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    val onSectionSelected: (ControlSection) -> Unit = onSectionSelected@ { section ->
        if (section == ControlSection.OVERVIEW) {
            navigateToDashboard(navController)
            return@onSectionSelected
        }
        // 두 번째 탭은 '이력' 입니다. 이전에는 이 탭이 수집 프로그램 목록(작업 시작)으로
        // 갔는데, 조사를 시작하려는 직원이 프로그램을 고르는 화면을 만나는 구조였습니다.
        // 시작은 현장 홈의 단계 카드가 책임지고, 이 탭은 지나간 조사를 봅니다.
        if (section == ControlSection.PIPELINES) {
            navController.navigate(Routes.TASK_HISTORY) {
                popUpTo(Routes.DASHBOARD) { inclusive = false; saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            return@onSectionSelected
        }
        val route = when (section) {
            ControlSection.OVERVIEW -> Routes.DASHBOARD
            ControlSection.DATA -> Routes.DATA
            ControlSection.PIPELINES -> Routes.PIPELINES
            ControlSection.SENSORS -> Routes.SENSORS
            ControlSection.SETTINGS -> Routes.SETTINGS
        }
        navController.navigate(route) {
            popUpTo(Routes.DASHBOARD) { inclusive = false; saveState = true }
            launchSingleTop = true
            restoreState = true
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

    LaunchedEffect(deviceState.connectionState, currentRoute) {
        if (
            deviceState.connectionState
            is ConnectionState.RegistrationRequired &&
            deviceRegistrationRedirectAllowed(currentRoute)
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


    ConnectionRecoveryLayout(
        deviceId = selectedDeviceId,
        connectionAvailable = fullControlConnected,
        message = if (!fullControlConnected && (currentRoute in routesRequiringDeviceConnection || currentRoute in setOf(Routes.DASHBOARD, Routes.DATA, Routes.SETTINGS, Routes.PIPELINE_DETAIL, Routes.SENSORS))) {
            if (connectedTransportType == TransportType.BLE) {
                "전체 제어 연결이 필요합니다. 작성한 내용은 유지됩니다."
            } else {
                "장비 연결을 기다리고 있습니다. 작성한 내용은 유지됩니다."
            }
        } else {
            null
        },
        onResolveConnection = {
            navController.navigate(Routes.CONNECTION_HUB) { launchSingleTop = true }
        }
    ) { screenModifier ->
    NavHost(
        modifier = screenModifier,
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
                onDirectConnect = { device ->
                    repository.prepareManualWifiDirect(device.deviceId)
                    navController.navigate(Routes.WIFI_DIRECT)
                },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onUploadHistoryClick = { navController.navigate(Routes.UPLOAD_QUEUE) },
                onServerDataClick = { navController.navigate(Routes.SERVER_STORAGE) },
                onOpenDashboard = { navController.navigate(Routes.DASHBOARD) },
                unreadAlertCount = alertCenterState.unreadCount,
                onAlertsClick = { navController.navigate(Routes.ALERTS) },
                lanEndpoints = lanEndpoints,
                registeredDevices = deviceState.registeredDevices,
                transportState = transportState,
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
                onRetryApi = { wifiDirectViewModel.retryApi() },
                onCancel = {
                    pendingDashboardTransport = null
                    repository.cancelWifiDirectConnection()
                }
            )
        }


        composable(
            Routes.DASHBOARD
        ) {
            StatusPollingLifecycleEffect(dashboardViewModel)

            DashboardScreen(
                state =
                    deviceDashboardState,

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
                onSensorsClick = { navController.navigate(Routes.SENSORS) },
                onCameraClick = { navController.navigate(Routes.CAMERA_PREVIEW) },
                onGnssClick = { navController.navigate(Routes.GNSS_MAP) },
                onConnectionClick = {
                    navController.navigate(Routes.CONNECTION_HUB) { launchSingleTop = true }
                },
                tasksConfirmed = com.example.jetsoncontroller.ui.pipelines.tasksAreFresh(
                    pipelineState.controlAvailable, pipelineState.observedAtMillis, System.currentTimeMillis()),
                taskObservedAt = pipelineState.observedAtMillis,
                pendingTaskActions = pipelineState.pendingActions,

                // 업무 단계 판정. 조사 선택·점검 상태는 SurveyRunViewModel에, 연결·실행 상태는
                // Dashboard/Pipeline 쪽에 있으므로 두 상태를 모두 보는 이 계층에서 계산합니다.
                stagePlan = com.example.jetsoncontroller.ui.field.fieldStagePlan(
                    com.example.jetsoncontroller.ui.field.FieldStageInput(
                        connected = deviceDashboardState.isOnline,
                        runStateConfirmed = com.example.jetsoncontroller.ui.pipelines.tasksAreFresh(
                            pipelineState.controlAvailable,
                            pipelineState.observedAtMillis,
                            System.currentTimeMillis()
                        ),
                        activeRunId = surveyRunState.activeRun?.runId?.takeIf {
                            surveyRunState.activeRun?.active == true
                        },
                        pendingStartRequestId = surveyRunState.pendingStart?.let { "pending" },
                        unconfirmedRunId = surveyRunState.unconfirmedRunId,
                        runAwaitingResultId = surveyRunState.activeRun
                            ?.takeIf { it.active != true && it.output.manifestState.uppercase() == "PENDING" }
                            ?.runId,
                        surveySelected = surveyRunState.selectionComplete,
                        preflightReady = surveyRunState.preflight?.ready == true,
                        surveyLabel = surveyRunState.selectedProject?.let { project ->
                            surveyRunState.selectedSection?.let { "${project.label} · ${it.label}" }
                        },
                        lastObservedLabel = pipelineState.observedAtMillis?.let {
                            java.text.DateFormat.getTimeInstance().format(java.util.Date(it))
                        },
                        registeredDeviceCount = deviceState.registeredDevices.size
                    )
                ),
                onStageAction = { destination ->
                    when (destination) {
                        com.example.jetsoncontroller.ui.field.FieldDestination.CONNECT ->
                            navController.navigate(Routes.CONNECTION_HUB) { launchSingleTop = true }
                        com.example.jetsoncontroller.ui.field.FieldDestination.SURVEY_PREP,
                        com.example.jetsoncontroller.ui.field.FieldDestination.PREFLIGHT ->
                            navigateToTaskStart(navController)
                        com.example.jetsoncontroller.ui.field.FieldDestination.ACTIVE_RUN ->
                            navController.navigate(Routes.ACTIVE_RUN) { launchSingleTop = true }
                        com.example.jetsoncontroller.ui.field.FieldDestination.RUN_RESULT ->
                            navController.navigate(Routes.RUN_RESULT) { launchSingleTop = true }
                    }
                },

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
                onDismissMessage = storageViewModel::dismissMessage,
                onTransferQueue = { navController.navigate(Routes.DATA) },
                thumbnailLoader = { entry -> repository.getFile(storageState.currentRoot!!.id, entry.relativePath) },
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
                onUndoDelete = storageViewModel::undoDelete,
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
            com.example.jetsoncontroller.ui.storage.DirectServerScreen(
                state = directServerState,
                onBack = {
                    if (!directServerViewModel.navigateBack()) {
                        navController.popBackStack()
                    }
                },
                onSection = directServerViewModel::selectSection,
                onSelectProfile = directServerViewModel::selectProfile,
                onSaveProfile = directServerViewModel::saveProfile,
                onConnect = directServerViewModel::connect,
                onRefreshJobs = directServerViewModel::refreshJobs,
                onLoadMoreJobs = directServerViewModel::loadMoreJobs,
                onOpenJob = directServerViewModel::openJob,
                onOpenDirectory = directServerViewModel::openDirectory,
                onOpenFile = directServerViewModel::openFile,
                onReceipt = directServerViewModel::loadReceipt,
                onMoveToTrash = directServerViewModel::moveToTrash,
                onRestore = directServerViewModel::restore,
                onUndoTrash = directServerViewModel::undoTrash,
                onRefreshTrash = directServerViewModel::refreshTrash,
                onRemoveProfile = directServerViewModel::removeSelectedProfile,
                onDismissMessage = directServerViewModel::dismissMessage
            )
        }

        composable(Routes.SERVER_PROXY_STORAGE) {
            LaunchedEffect(Unit) { serverStorageViewModel.refresh() }
            ServerStorageScreen(
                onDismissMessage = serverStorageViewModel::dismissMessage,
                thumbnailLoader = { entry -> repository.getUploadLibraryFile(
                    serverStorageState.selectedTarget!!.id,
                    serverStorageState.selectedSession!!.sessionId,
                    entry.relativePath
                ) },
                state = serverStorageState,
                onBack = {
                    if (!serverStorageViewModel.navigateBack()) navController.popBackStack()
                },
                onDeviceDataClick = { navController.navigate(Routes.STORAGE) { launchSingleTop = true } },
                onRefresh = serverStorageViewModel::refresh,
                onTargetSelected = serverStorageViewModel::selectTarget,
                onSessionClick = serverStorageViewModel::openSession,
                onDeleteSession = serverStorageViewModel::deleteSession,
                onDirectoryClick = serverStorageViewModel::openDirectory,
                onFileClick = serverStorageViewModel::openFile,
                onLoadMore = serverStorageViewModel::loadMoreSessions,
                onSectionSelected = onSectionSelected,
                deletionEnabled = false
            )
        }

        composable(
            route = Routes.UPLOAD_CONFIRM,
            arguments = listOf(
                navArgument("rootId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("runId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val rootId = backStackEntry.arguments?.getString("rootId").orEmpty()
            val path = backStackEntry.arguments?.getString("path").orEmpty()
            val linkedRunId = backStackEntry.arguments?.getString("runId")?.takeIf(String::isNotBlank)
            val confirmationDevice = androidx.compose.runtime.saveable.rememberSaveable { selectedDeviceId.orEmpty() }
            LaunchedEffect(selectedDeviceId) {
                if (confirmationDevice != selectedDeviceId.orEmpty()) navController.popBackStack()
            }
            LaunchedEffect(rootId, path, linkedRunId, selectedDeviceId) {
                uploadViewModel.loadSourceSummary(rootId, path, linkedRunId = linkedRunId)
            }
            UploadConfirmScreen(
                deviceId = selectedDeviceId,
                deviceName = selectedDeviceName,
                rootId = rootId,
                path = path,
                targets = uploadState.targets,
                sourceSummary = uploadState.sourceSummary,
                linkedRunId = linkedRunId,
                linkedRun = uploadState.linkedRun,
                isCalculatingSource = uploadState.isCalculatingSource,
                serverUploadEnabled = serverUploadEnabled && dashboardState.capabilities.uploads && confirmationDevice == selectedDeviceId.orEmpty(),
                serverUploadDisabledReason = serverUploadDisabledReason,
                isLoading = uploadState.isLoading,
                error = uploadState.error,
                onBack = { navController.popBackStack() },
                onRefresh = {
                    uploadViewModel.refresh()
                    uploadViewModel.loadSourceSummary(rootId, path, force = true, linkedRunId = linkedRunId)
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
                onRestoreSource = uploadViewModel::restoreCurrentSource,
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
                mutationEnabled = fullControlConnected,
                deviceId = uploadState.deviceId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.UPLOAD_SERVERS) {
            UploadTargetSettingsScreen(
                controlAvailable = fullControlConnected,
                deviceId = uploadState.deviceId,
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

        listOf(Routes.PIPELINES, Routes.PIPELINE_DETAIL).forEach { taskRoute ->
        composable(taskRoute) { taskEntry ->
            StatusPollingLifecycleEffect(dashboardViewModel)
            PipelineListScreen(
                onHistory = { navController.navigate(Routes.TASK_HISTORY) { launchSingleTop = true } },

                state = pipelineState,
                onBack = { navController.popBackStack() },
                onRefresh = pipelineViewModel::refresh,
                onAdd = {
                    pipelineViewModel.beginCreate()
                    navController.navigate(Routes.PIPELINE_EDITOR)
                },
                onControl = pipelineViewModel::control,
                onPrepareRun = { pipeline ->
                    navController.navigate("survey_run/${Uri.encode(pipeline.id)}")
                },
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
                onClearMessage = pipelineViewModel::clearMessage,
                deviceName = selectedDeviceName,
                unreadCount = alertCenterState.unreadCount,
                onAlerts = { navController.navigate(Routes.ALERTS) },
                onDetails = { navController.navigate("pipeline_detail/${Uri.encode(it.id)}") },
                detailId = taskEntry.arguments?.getString("pipelineId"),
                startCapability = dashboardState.capabilities.pipelines && dashboardState.capabilities.mobileTimeSync
            )
        }
        }

        composable(
            route = Routes.SURVEY_RUN,
            arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })
        ) { entry ->
            val pipelineId = entry.arguments?.getString("pipelineId").orEmpty()
            val pipelineLabel = pipelineState.pipelines.firstOrNull { it.id == pipelineId }?.label ?: pipelineId
            LaunchedEffect(selectedDeviceId, pipelineId, pipelineLabel) {
                surveyRunViewModel.open(pipelineId, pipelineLabel)
            }
            SurveyRunScreen(
                state = surveyRunState,
                onBack = { pipelineViewModel.refresh(); navController.popBackStack() },
                onRefresh = surveyRunViewModel::refresh,
                onSelectProject = surveyRunViewModel::selectProject,
                onSelectSection = surveyRunViewModel::selectSection,
                onCreateProject = surveyRunViewModel::createProject,
                onCreateSection = surveyRunViewModel::createSection,
                onSensorRequirement = surveyRunViewModel::setSensorRequirement,
                onMinFreeBytes = surveyRunViewModel::setMinFreeBytes,
                onOutputMinFiles = surveyRunViewModel::setOutputMinFiles,
                onOutputMinBytes = surveyRunViewModel::setOutputMinBytes,
                onOutputPatterns = surveyRunViewModel::setOutputPatterns,
                onOutputRoot = surveyRunViewModel::setOutputRoot,
                onOutputPath = surveyRunViewModel::setOutputPath,
                onSavePolicy = surveyRunViewModel::savePolicy,
                onPreflight = surveyRunViewModel::runPreflight,
                // 시작 직후 갈 곳이 있다는 것이 이번 재편의 핵심입니다. 시작 요청의 결과가
                // 아직 없어도 수집 중 화면으로 보냅니다 — 그 화면이 '결과 대기' 를 정직하게
                // 표시하므로, 목록으로 돌려보내 같은 버튼을 다시 누르게 하는 것보다 안전합니다.
                onStart = {
                    surveyRunViewModel.start()
                    navController.navigate(Routes.ACTIVE_RUN) { launchSingleTop = true }
                },
                onRetryPendingStart = surveyRunViewModel::retryPendingStart,
                onDismissMessage = surveyRunViewModel::clearMessage
            )
        }

        composable(Routes.ACTIVE_RUN) {
            val status = deviceDashboardState.status
            val statusFresh = deviceDashboardState.isOnline &&
                deviceDashboardState.statusFreshness == StatusFreshness.CURRENT
            val activeRun = surveyRunState.activeRun
            val stopTarget = activeRun?.let { r ->
                pipelineState.pipelines.firstOrNull { it.id == r.pipelineId }
            }
            com.example.jetsoncontroller.ui.field.ActiveRunScreen(
                deviceName = selectedDeviceName,
                connectionLabel = com.example.jetsoncontroller.ui.connection
                    .userConnectionStage(deviceDashboardState.isOnline, deviceDashboardState.transportType).label,
                connectionTone = com.example.jetsoncontroller.ui.connection
                    .userConnectionStage(deviceDashboardState.isOnline, deviceDashboardState.transportType).tone,
                run = activeRun,
                // 장비가 경과 시간을 제공하지 않으므로 앱이 만들어내지 않습니다.
                elapsedLabel = null,
                lastObservedLabel = null,
                sensorSummary = if (!deviceDashboardState.isOnline || !status.sensorTelemetryAvailable) null
                    else listOf(
                        status.cameraSensor.active,
                        status.gnssSensor.active,
                        status.imuSensor.active
                    ).count { it }.let { "${it}개 수신" },
                storageSummary = if (statusFresh) "${status.storagePercent}% 사용" else null,
                stopInProgress = pipelineState.busyPipelineId != null,
                online = deviceDashboardState.isOnline,
                unreadCount = alertCenterState.unreadCount,
                onDevices = { navController.navigate(Routes.CONNECTION_HUB) },
                onAlerts = { navController.navigate(Routes.ALERTS) },
                onBack = { navController.popBackStack() },
                onRefresh = { surveyRunViewModel.refresh(); pipelineViewModel.refresh() },
                onStop = { stopTarget?.let { pipelineViewModel.control(it, "stop") } },
                onCamera = { navController.navigate(Routes.CAMERA_PREVIEW) },
                onMap = { navController.navigate(Routes.GNSS_MAP) }
            )
        }

        composable(Routes.RUN_RESULT) {
            com.example.jetsoncontroller.ui.field.RunResultScreen(
                deviceName = selectedDeviceName,
                connectionLabel = com.example.jetsoncontroller.ui.connection
                    .userConnectionStage(deviceDashboardState.isOnline, deviceDashboardState.transportType).label,
                connectionTone = com.example.jetsoncontroller.ui.connection
                    .userConnectionStage(deviceDashboardState.isOnline, deviceDashboardState.transportType).tone,
                run = surveyRunState.activeRun,
                uploadEnabled = serverUploadEnabled,
                uploadDisabledReason = serverUploadDisabledReason,
                // 이 실행의 서버 수신 결과를 조회하는 경로는 아직 연결하지 않았습니다.
                // null 은 '전송하지 않음' 이 아니라 '앱이 아직 모른다' 로 표시됩니다.
                serverReceiptLabel = null,
                online = deviceDashboardState.isOnline,
                unreadCount = alertCenterState.unreadCount,
                onDevices = { navController.navigate(Routes.CONNECTION_HUB) },
                onAlerts = { navController.navigate(Routes.ALERTS) },
                onBack = { navController.navigate(Routes.TASK_HISTORY) },
                onRefresh = surveyRunViewModel::refresh,
                onPrepareUpload = { navController.navigate(Routes.DATA) },
                onOpenFiles = { navController.navigate(Routes.STORAGE) },
                onNewSurvey = { navigateToDashboard(navController) }
            )
        }

        composable(Routes.TASK_HISTORY) {
            LaunchedEffect(Unit) { fieldToolsViewModel.refresh() }
            com.example.jetsoncontroller.ui.field.GeoRunDashboard(
                state = fieldState, pipelines = pipelineState.pipelines, deviceName = selectedDeviceName,
                unreadCount = alertCenterState.unreadCount,
                onBack = { navController.popBackStack() }, onAlerts = { navController.navigate(Routes.ALERTS) },
                onSection = onSectionSelected, onNew = { navigateToTaskStart(navController) },
                onRefresh = { fieldToolsViewModel.refresh() }, onMore = { fieldToolsViewModel.refresh(true) },
                onLog = fieldToolsViewModel::openLog,
                onRoute = { fieldToolsViewModel.selectRun(it); navController.navigate(Routes.GNSS_MAP) },
                onDismissLog = fieldToolsViewModel::dismissLog,
                onPipeline = { navController.navigate("pipeline_detail/${Uri.encode(it.id)}") },
                onDeleteRun = fieldToolsViewModel::deleteRun,
                onUndoDelete = fieldToolsViewModel::undoDeleteRun,
                onUploadOutput = { run ->
                    val output = run.output
                    val runId = run.runId ?: run.id.takeIf { run.contextSnapshot != null }
                    if (output != null && runId != null) {
                        navController.navigate(
                            "upload_confirm/${Uri.encode(output.rootId)}?path=${Uri.encode(output.path)}&runId=${Uri.encode(runId)}"
                        )
                    }
                },
                onDismissMessage = fieldToolsViewModel::dismissMessage
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
                onSave = pipelineViewModel::saveConfig,
                onReload = pipelineViewModel::reloadConfig
            )
        }

        composable(Routes.SENSORS) {
            StatusPollingLifecycleEffect(dashboardViewModel)
            SensorScreen(
                deviceName = selectedDeviceName,
                onDevices = { navController.navigate(Routes.CONNECTION_HUB) },
                onAlerts = { navController.navigate(Routes.ALERTS) },
                unreadCount = alertCenterState.unreadCount,
                status = dashboardState.status,
                deviceOnline = transportState is TransportState.Connected,
                fullControlAvailable = fullControlConnected,
                onCameraClick = { navController.navigate(Routes.CAMERA_PREVIEW) },
                onGnssClick = { navController.navigate(Routes.GNSS_MAP) },
                onSectionSelected = onSectionSelected
            )
        }

        composable(Routes.DIAGNOSTICS) {
            ConnectionDiagnosticsScreen(onBack = { navController.popBackStack() })
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
                captureBusy = fieldState.captureBusy,
                captureMessage = fieldState.captureMessage,
                onCapture = { device, mobile -> fieldToolsViewModel.capture(fieldContext, device, mobile) },
                onRefresh = cameraPreviewViewModel::refresh
            )
        }

        composable(Routes.GNSS_MAP) {
            DisposableEffect(selectedDeviceId) {
                fieldToolsViewModel.selectRun(fieldState.selectedRun ?: fieldState.runs.firstOrNull { it.state == "RUNNING" } ?: fieldState.runs.firstOrNull())
                onDispose { fieldToolsViewModel.stopRoutePolling() }
            }
            StatusPollingLifecycleEffect(dashboardViewModel)
            GnssMapScreen(
                route = fieldState.route,
                quality = fieldState.routeQuality,
                routeLabel = fieldState.selectedRun?.label,
                gnss = dashboardState.status.gnssSensor,
                telemetryFresh = dashboardState.status.sensorTelemetryFresh,
                deviceOnline = transportState is TransportState.Connected,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DATA) {
            deviceUiState.SaveableStateProvider("data-${selectedDeviceId}") {
                com.example.jetsoncontroller.ui.storage.DataHubScreen(
                    selectedDeviceName, pipelineState.pipelines, uploadState,
                    serverUploadEnabled && dashboardState.capabilities.uploads,
                    if (!serverUploadEnabled) serverUploadDisabledReason else "장비 업로드 지원 여부 미확인",
                    alertCenterState.unreadCount,
                    onDevices = { navController.navigate(Routes.CONNECTION_HUB) },
                    onAlerts = { navController.navigate(Routes.ALERTS) },
                    onFiles = { navController.navigate(Routes.STORAGE) },
                    onHistory = { navController.navigate(Routes.UPLOAD_QUEUE) },
                    onTargets = { navController.navigate(Routes.UPLOAD_SERVERS) },
                    onTransfer = { root, path -> navController.navigate("upload_confirm/${Uri.encode(root)}?path=${Uri.encode(path)}") },
                    onSection = onSectionSelected,
                    onServerData = { navController.navigate(Routes.SERVER_STORAGE) },
                    onTrash = { navController.navigate(Routes.LOCAL_TRASH) })
            }
        }
        composable(Routes.LOCAL_TRASH) {
            com.example.jetsoncontroller.ui.storage.LocalTrashScreen(
                state = localTrashState,
                onBack = { navController.popBackStack() },
                onRefresh = localTrashViewModel::refresh,
                onRestore = localTrashViewModel::restore,
                onDismissMessage = localTrashViewModel::dismissMessage
            )
        }
        composable(Routes.SETTINGS) {
            StatusPollingLifecycleEffect(dashboardViewModel)
            deviceUiState.SaveableStateProvider("settings-${selectedDeviceId}") {
                com.example.jetsoncontroller.ui.settings.SettingsHubScreen(
                    deviceDashboardState, selectedDeviceId, alertCenterState.unreadCount,
                    onDevices = { navController.navigate(Routes.CONNECTION_HUB) },
                    onAlerts = { navController.navigate(Routes.ALERTS) },
                    onNetwork = { navController.navigate(Routes.NETWORK_SETTINGS) },
                    onSensors = { navController.navigate(Routes.SENSORS) },
                    onTargets = { navController.navigate(Routes.UPLOAD_SERVERS) },
                    onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onAlertSettings = { navController.navigate(Routes.ALERT_SETTINGS) },
                    onServerStorage = { navController.navigate(Routes.SERVER_STORAGE) },
                    onRefreshFan = dashboardViewModel::refreshFan, onFanAuto = dashboardViewModel::setFanAuto,
                    onFanManual = dashboardViewModel::setFanManual, onReboot = dashboardViewModel::reboot,
                    onShutdown = dashboardViewModel::shutdown, onDismissMessage = dashboardViewModel::clearOperationMessage,
                    onSection = onSectionSelected,
                    onDeveloper = { navController.navigate("developer") },
                    onAdminTools = { navController.navigate(Routes.ADMIN_TOOLS) })
            }
        }
        composable(Routes.ADMIN_TOOLS) {
            StatusPollingLifecycleEffect(dashboardViewModel)
            com.example.jetsoncontroller.ui.settings.AdminToolsScreen(
                state = deviceDashboardState,
                deviceId = selectedDeviceId,
                onBack = { navController.popBackStack() },
                onNetwork = { navController.navigate(Routes.NETWORK_SETTINGS) },
                onTargets = { navController.navigate(Routes.UPLOAD_SERVERS) },
                onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onJetsonServerProxy = { navController.navigate(Routes.SERVER_PROXY_STORAGE) },
                onDeviceRegistration = { navController.navigate(Routes.CONNECTION_HUB) },
                onRefreshFan = dashboardViewModel::refreshFan,
                onFanAuto = dashboardViewModel::setFanAuto,
                onFanManual = dashboardViewModel::setFanManual,
                onReboot = dashboardViewModel::reboot,
                onShutdown = dashboardViewModel::shutdown,
                onDismissMessage = dashboardViewModel::clearOperationMessage
            )
        }
        composable("developer") {
            com.example.jetsoncontroller.ui.field.DeveloperScreen(fieldState,
                onBack = { navController.popBackStack() }, onExecute = fieldToolsViewModel::execute,
                onLogs = { navController.navigate(Routes.PIPELINES) },
                onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) })
        }

        composable(Routes.ALERT_SETTINGS) {
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
                onSectionSelected = onSectionSelected,
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
            )
        }
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

internal fun navigateToTaskStart(navController: NavHostController) {
    navController.clearBackStack(Routes.PIPELINES)
    navController.navigate(Routes.PIPELINES) {
        popUpTo(Routes.DASHBOARD) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

internal fun navigateToDashboard(navController: NavHostController) {
    navController.clearBackStack(Routes.DASHBOARD)
    navController.navigate(Routes.DASHBOARD) {
        popUpTo(Routes.DASHBOARD) { inclusive = true; saveState = false }
        launchSingleTop = true
        restoreState = false
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

internal fun deviceRegistrationRedirectAllowed(currentRoute: String?): Boolean =
    currentRoute != "server_storage"
