package com.example.jetsoncontroller.ui

import com.example.jetsoncontroller.ui.model.localDeviceWord
import android.net.Uri
import com.example.jetsoncontroller.ui.navigation.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoAppShell
import com.example.jetsoncontroller.ui.components.geo.*
import androidx.compose.foundation.layout.*
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.alerts.AlertDestination
import com.example.jetsoncontroller.data.alerts.AlertHistoryStore
import com.example.jetsoncontroller.data.alerts.AlertPreferencesStore
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.data.transport.canStartServerUpload
import com.example.jetsoncontroller.data.transport.serverUploadUnavailableMessage
import com.example.jetsoncontroller.ui.alerts.AlertCenterViewModel
import com.example.jetsoncontroller.ui.dashboard.DashboardViewModel
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.devices.DeviceListViewModel
import com.example.jetsoncontroller.ui.devices.formatSeenAt
import com.example.jetsoncontroller.ui.devices.shouldRecordSnapshot
import com.example.jetsoncontroller.ui.devices.snapshotFromStatus
import com.example.jetsoncontroller.ui.devices.unsentCollectionAttention
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.data.devices.DeviceSnapshotStore
import com.example.jetsoncontroller.data.devices.deviceSnapshotKey
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.ui.model.Tone
import androidx.compose.runtime.rememberUpdatedState
import com.example.jetsoncontroller.ui.pairing.PairingViewModel
import com.example.jetsoncontroller.ui.pairing.PairingPhase
import com.example.jetsoncontroller.ui.network.NetworkSettingsViewModel
import com.example.jetsoncontroller.ui.wifi.WifiDirectViewModel
import com.example.jetsoncontroller.ui.storage.DeviceStorageViewModel
import com.example.jetsoncontroller.ui.storage.ServerStorageViewModel
import com.example.jetsoncontroller.ui.upload.UploadViewModel
import com.example.jetsoncontroller.ui.pipelines.PipelineViewModel
import com.example.jetsoncontroller.ui.survey.SurveyRunViewModel
import com.example.jetsoncontroller.ui.sensors.CameraPreviewViewModel
import com.example.jetsoncontroller.ui.settings.AlertSettingsViewModel
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
import com.example.jetsoncontroller.ui.components.NoticeDismissalProvider
import com.example.jetsoncontroller.ui.settings.rememberDeveloperModePreference
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay


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

private val developerOnlyRoutes = setOf(
    Routes.SERVER_PROXY_STORAGE,
    Routes.UPLOAD_SERVERS,
    Routes.PIPELINE_EDITOR,
    Routes.PIPELINE_PICKER,
    Routes.PIPELINE_LOGS,
    Routes.PIPELINE_CONFIG,
    Routes.PIPELINE_DETAIL,
    Routes.DIAGNOSTICS,
    "developer"
)

internal fun routeAllowed(route: String?, developerModeEnabled: Boolean): Boolean =
    developerModeEnabled || (route !in developerOnlyRoutes && route != "dev" && route?.startsWith("dev/") != true &&
        route != com.example.jetsoncontroller.ui.dev.DevDestination.PROXY_PREVIEW.route)

internal fun developerRevocationDestination(
    route: String?,
    developerModeEnabled: Boolean
): String? = if (routeAllowed(route, developerModeEnabled)) null else Routes.SETTINGS

/** Device cross-links stay on the originating tab; only tab taps switch graphs. */
internal fun navigateToDeviceRoot(navController: NavHostController) {
    if (currentTabRoot(navController) == Routes.DEVICES) {
        navController.popBackStack(Routes.DEVICES, inclusive = false)
    } else navController.navigateSingle(Routes.DEVICE_BROWSE)
}

internal enum class TabTap { STAY, RETURN_TO_TAB_ROOT, OPEN_TAB_ROOT }

internal fun tabTap(
    currentRoute: String?,
    tabRootInBackStack: Boolean,
    tabRoute: String,
    currentTabRoute: String? = when (currentRoute) {
        Routes.DEVICES, Routes.TASKS, Routes.TRANSFERS, Routes.SETTINGS -> currentRoute
        else -> null
    }
): TabTap = when {
    currentRoute == tabRoute -> TabTap.STAY
    currentTabRoute != null && currentTabRoute != tabRoute -> TabTap.OPEN_TAB_ROOT
    tabRootInBackStack -> TabTap.RETURN_TO_TAB_ROOT
    else -> TabTap.OPEN_TAB_ROOT
}

internal fun navigateToTab(navController: NavHostController, route: String) {
    require(route in primaryTabRoutes)
    val inBackStack = runCatching { navController.getBackStackEntry(route) }.isSuccess
    when (tabTap(navController.currentDestination?.route, inBackStack, route, currentTabRoot(navController))) {
        TabTap.STAY -> Unit
        TabTap.RETURN_TO_TAB_ROOT -> navController.popBackStack(route, inclusive = false)
        TabTap.OPEN_TAB_ROOT -> navController.navigateSingle(tabGraphRoute(route)) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
    }
}

internal fun NavHostController.navigateSingle(
    route: String,
    options: androidx.navigation.NavOptionsBuilder.() -> Unit = {}
) {
    navigate(route) { options(); launchSingleTop = true }
}

/** Back from another root returns to the work root, including the zero-device scene. */
internal fun navigateToWorkRoot(navController: NavHostController) {
    val graph = navController.graph.findNode(tabGraphRoute(Routes.TASKS)) as androidx.navigation.NavGraph
    val root = graph.startDestinationRoute ?: Routes.TASKS
    navigateToTab(navController, Routes.TASKS)
    if (!navController.popBackStack(root, inclusive = false) &&
        navController.currentDestination?.route != root) {
        navController.navigateSingle(root) {
            popUpTo(tabGraphRoute(Routes.TASKS)) { inclusive = false }
        }
    }
}

internal fun completeDeviceRegistration(navController: NavHostController) {
    (navController.graph.findNode(tabGraphRoute(Routes.TASKS)) as androidx.navigation.NavGraph)
        .setStartDestination(Routes.TASKS)
    navController.clearBackStack(tabGraphRoute(Routes.TASKS))
    navController.navigateSingle(tabGraphRoute(Routes.TASKS)) {
        popUpTo(navController.graph.id) { inclusive = false }
    }
}

internal fun navigateToSettingsRoot(navController: NavHostController) {
    // Do not save a revoked developer destination and restore it on the next tab tap.
    navController.popBackStack(currentTabRoot(navController), inclusive = false)
    navigateToTab(navController, Routes.SETTINGS)
    navController.popBackStack(Routes.SETTINGS, inclusive = false)
}

/** The route each primary tab opens. */
internal fun tabRoute(section: GeoTab): String = when (section) {
    GeoTab.TASKS -> Routes.TASKS
    GeoTab.TRANSFERS -> Routes.TRANSFERS
    GeoTab.SETTINGS -> Routes.SETTINGS
    else -> Routes.DEVICES
}

/** 작업 is the registered-device start destination. */
internal fun startRoute(): String = Routes.TASKS

/** The places a device flow can end, and the route each one returns to. */
internal enum class DeviceFlowExit {
    CONNECTION_COMPLETED,
    PAIRING_READY,
    PAIRING_CANCELLED,
    DETAIL_DISCONNECTED,
    DETAIL_BACK
}

internal fun deviceFlowExitRoute(exit: DeviceFlowExit): String = when (exit) {
    DeviceFlowExit.PAIRING_READY -> Routes.TASKS
    DeviceFlowExit.CONNECTION_COMPLETED,
    DeviceFlowExit.PAIRING_CANCELLED,
    DeviceFlowExit.DETAIL_DISCONNECTED,
    DeviceFlowExit.DETAIL_BACK -> Routes.DEVICES
}

/** The connection banner text and tone for a screen. Null when no banner should show. */
internal data class ConnectionNotice(val message: String, val tone: Tone)

/** Screens that cannot work without the device connection. The device root has its own status band instead. */
// Settings are app preferences and never wait for a device, so they are not listed here.
private val routesNeedingConnectionNotice = routesRequiringDeviceConnection + setOf(
    Routes.DATA,
    Routes.PIPELINE_DETAIL,
    Routes.SENSORS,
    Routes.DEVICE_DETAIL
)

/** Routes whose composable uses GeoPaneLayout (workGraph); they span the whole two-pane window. */
private val paneLayoutRoutes = setOf(
    Routes.WORK_BROWSE, Routes.TASKS, Routes.TICKET_DETAIL, Routes.ACTIVE_RUN,
    Routes.DEVICES, Routes.DEVICE_BROWSE, Routes.DEVICE_DETAIL,
    Routes.TRANSFERS, Routes.TRANSFER_BROWSE, Routes.UPLOAD_PROGRESS, Routes.DATA,
    Routes.SETTINGS, Routes.THEME, Routes.ALERT_SETTINGS, "settings/about", "settings/help", "settings/server"
)

private val routesWithOwnConnectionStrip = setOf(Routes.DEVICE_DETAIL, Routes.ACTIVE_RUN, Routes.NETWORK_SETTINGS)

// Only these screens hold input the operator is writing, so only they promise to keep it.
private val routesWithEditableInput = setOf(
    Routes.SURVEY_RUN,
    Routes.NETWORK_SETTINGS,
    Routes.UPLOAD_CONFIRM,
    Routes.PIPELINE_EDITOR,
    Routes.PIPELINE_CONFIG
)

internal fun connectionNoticeFor(
    route: String?,
    fullControlConnected: Boolean,
    transportType: TransportType?,
    registrationRequired: Boolean,
    hasRegisteredDevice: Boolean = true
): ConnectionNotice? {
    if (registrationRequired && deviceRegistrationRedirectAllowed(route)) {
        return ConnectionNotice("장치 인증 정보를 확인할 수 없습니다. QR로 다시 등록해 주세요.", Tone.ERROR)
    }
    // With no registered device there is nothing to wait for; the 장치 root asks for a QR registration.
    if (!hasRegisteredDevice || fullControlConnected || route !in routesNeedingConnectionNotice) return null
    // These screens show their own connection strip under their top bar; a second banner would repeat it.
    if (route in routesWithOwnConnectionStrip) return null
    val outcome = if (route in routesWithEditableInput) "작성한 내용은 유지됩니다." else "연결되면 최신 정보로 바뀝니다."
    return if (transportType == TransportType.BLE) {
        ConnectionNotice("전체 제어 연결이 필요합니다. $outcome", Tone.WARNING)
    } else {
        ConnectionNotice("장치 연결을 기다리고 있습니다. $outcome", Tone.WARNING)
    }
}

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
    val developerModePreference = rememberDeveloperModePreference()
    val developerModeEnabled = developerModePreference.enabled

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
        
    // Two browsers, two ViewModels: the 데이터 hub and the device storage route each keep their
    // own location, so opening one never moves the other.
    val dataStorageViewModel: DeviceStorageViewModel =
        viewModel(
            key = "device-storage-data",
            factory = DeviceStorageViewModel.Factory(repository)
        )
    val routeStorageViewModel: DeviceStorageViewModel =
        viewModel(
            key = "device-storage-route",
            factory = DeviceStorageViewModel.Factory(repository)
        )

    val fieldContext = androidx.compose.ui.platform.LocalContext.current
    val fieldToolsViewModel: com.example.jetsoncontroller.ui.field.FieldToolsViewModel = viewModel(
        factory = com.example.jetsoncontroller.ui.field.FieldToolsViewModel.Factory(repository))
    val fieldState by fieldToolsViewModel.state.collectAsStateWithLifecycle()

    val directServerViewModel: com.example.jetsoncontroller.ui.storage.DirectServerViewModel =
        viewModel(factory = com.example.jetsoncontroller.ui.storage.DirectServerViewModel.Factory(fieldContext))
    val serverTicketViewModel: com.example.jetsoncontroller.ui.tasks.ServerTicketViewModel = viewModel(
        factory = com.example.jetsoncontroller.ui.tasks.ServerTicketViewModel.Factory(fieldContext)
    )
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
        factory = SurveyRunViewModel.Factory(repository, fieldContext) { draft ->
            val tickets = serverTicketViewModel.uiState.value
            if (com.example.jetsoncontroller.ui.survey.ticketStillMatches(draft, tickets.profileId, tickets.tickets)) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException(com.example.jetsoncontroller.ui.survey.TicketChangedMessage))
            }
        }
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
            
    val dataStorageState by dataStorageViewModel.uiState.collectAsStateWithLifecycle()
    val routeStorageState by routeStorageViewModel.uiState.collectAsStateWithLifecycle()

    val directServerState by directServerViewModel.uiState.collectAsStateWithLifecycle()
    val serverTicketState by serverTicketViewModel.uiState.collectAsStateWithLifecycle()
    val ticketPreparation by serverTicketViewModel.preparation.collectAsStateWithLifecycle()
    val runResultState by surveyRunViewModel.resultState.collectAsStateWithLifecycle()
    val resultLogState by surveyRunViewModel.resultLog.collectAsStateWithLifecycle()
    val latestTicketProfileIdState = rememberUpdatedState(directServerState.selectedProfileId)
    val latestTicketProfileId by latestTicketProfileIdState
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
    val pendingDashboardTransportState = remember { mutableStateOf<TransportType?>(null) }
    var pendingDashboardTransport by pendingDashboardTransportState
    val connectedTransportType =
        (transportState as? TransportState.Connected)?.type
    val serverUploadEnabled = canStartServerUpload(connectedTransportType)
    val serverUploadDisabledReason = serverUploadUnavailableMessage(connectedTransportType)
    val fullControlConnected = connectedTransportType == TransportType.LAN ||
        connectedTransportType == TransportType.WIFI_DIRECT

    val selectedDeviceId by repository.selectedDeviceId.collectAsStateWithLifecycle()
    val latestSelectedDeviceIdState = rememberUpdatedState(selectedDeviceId)
    val latestSelectedDeviceId by latestSelectedDeviceIdState
    // Do not briefly label A's retained state as B while ViewModel collectors switch workspaces.
    val pipelineState = rawPipelineState.takeIf { it.deviceId.equals(selectedDeviceId, true) }
        ?: com.example.jetsoncontroller.ui.pipelines.PipelineUiState(deviceId = selectedDeviceId)
    val uploadState = rawUploadState.takeIf { it.deviceId.equals(selectedDeviceId, true) }
        ?: com.example.jetsoncontroller.ui.upload.UploadUiState(deviceId = selectedDeviceId)
    // Operators read device names, never ids (no UUIDs on screen), so the fallback is a plain label.
    val selectedDeviceName = deviceState.registeredDevices.firstOrNull {
        it.deviceId.equals(selectedDeviceId, ignoreCase = true)
    }?.deviceName ?: "선택된 장치"
    val deviceDashboardState = dashboardState.copy(deviceName = selectedDeviceName)

    // Device snapshots keep the last known state of each device for offline use. They are written
    // only from fresh status (shouldRecordSnapshot), never from a stale or failed read.
    val deviceSnapshotStore = remember(fieldContext) { DeviceSnapshotStore(fieldContext) }
    val deviceSnapshots by deviceSnapshotStore.snapshots.collectAsStateWithLifecycle(
        initialValue = emptyMap()
    )
    val statusObservedAt by repository.statusUpdatedAtEpochMillis.collectAsStateWithLifecycle()
    val latestDashboardForSnapshot by rememberUpdatedState(deviceDashboardState)
    val latestPendingTransferCount by rememberUpdatedState(
        uploadState.queue.count {
            it.state == UploadJobState.QUEUED ||
                it.state == UploadJobState.SCANNING ||
                it.state == UploadJobState.UPLOADING
        }
    )
    val latestRunActive by rememberUpdatedState(surveyRunState.activeRun?.active == true)
    LaunchedEffect(selectedDeviceId, statusObservedAt) {
        val deviceId = selectedDeviceId ?: return@LaunchedEffect
        val observedAt = statusObservedAt ?: return@LaunchedEffect
        val live = latestDashboardForSnapshot
        if (!shouldRecordSnapshot(live.isOnline, live.statusFreshness == StatusFreshness.CURRENT, observedAt)) {
            return@LaunchedEffect
        }
        deviceSnapshotStore.record(
            snapshotFromStatus(
                deviceId = deviceId,
                deviceName = live.deviceName,
                status = live.status,
                observedAtEpochMillis = observedAt,
                pendingTransferCount = latestPendingTransferCount,
                runningLabel = if (latestRunActive) "수집 진행 중" else null
            )
        )
    }

    // The one connect path for a registered device: a discovered endpoint over LAN, otherwise
    // automatic reconnection. The root, the connect sheet and the detail all use it.
    val currentRegisteredDevice = deviceState.registeredDevices.firstOrNull {
        it.deviceId.equals(selectedDeviceId, ignoreCase = true)
    } ?: deviceState.registeredDevices.firstOrNull()
    val connectRegisteredDevice: (RegisteredDevice) -> Unit = { device ->
        val endpoint = lanEndpoints.firstOrNull { it.deviceId.equals(device.deviceId, ignoreCase = true) }
        if (endpoint != null) {
            pendingDashboardTransport = TransportType.LAN
            repository.connectLan(endpoint)
        } else {
            repository.connectRegisteredAutomatically(device.deviceId)
        }
    }
    val selectedSnapshot = selectedDeviceId?.let { deviceSnapshots[deviceSnapshotKey(it)] }
    val selectedConnection = deviceConnectionUi(
        localDeviceWord = localDeviceWord(),
        isOnline = deviceDashboardState.isOnline,
        transportType = deviceDashboardState.transportType,
        checking = selectedDeviceId != null &&
            connectingLanDeviceId.equals(selectedDeviceId, ignoreCase = true),
        available = selectedDeviceId != null &&
            lanEndpoints.any { it.deviceId.equals(selectedDeviceId, ignoreCase = true) },
        connectFailed = selectedDeviceId != null && lanConnectionError != null,
        lastSeenText = selectedSnapshot?.lastSeenAtEpochMillis?.let {
            formatSeenAt(it, System.currentTimeMillis())
        }
    )
    val unsentCollection = surveyRunState.latestRun?.let { run ->
        !run.active &&
            com.example.jetsoncontroller.ui.field.runOutputStored(run) &&
            serverReceiptPresentation(
                run,
                (listOfNotNull(uploadState.currentJob) + uploadState.queue).distinctBy { it.id }
            ).label == null
    } == true
    val deviceAttention = unsentCollectionAttention(selectedDeviceName, unsentCollection)
    val tasksFresh = com.example.jetsoncontroller.ui.pipelines.tasksAreFresh(
        pipelineState.controlAvailable, pipelineState.observedAtMillis, System.currentTimeMillis()
    )
    val discoveredRunningPipeline = discoverableCurrentPipeline(selectedDeviceId, pipelineState.pipelines, tasksFresh)
    LaunchedEffect(
        selectedDeviceId, discoveredRunningPipeline?.id, discoveredRunningPipeline?.activeRunId,
        surveyRunState.deviceId, surveyRunState.pipelineId, surveyRunState.restoringLocalState,
        surveyRunState.contextLocked
    ) {
        val discovered = discoveredRunningPipeline ?: return@LaunchedEffect
        if (surveyRunState.deviceId.equals(selectedDeviceId, true) &&
            shouldAdoptDiscoveredPipeline(surveyRunState, discovered)) {
            if (surveyRunState.pipelineId == discovered.id) surveyRunViewModel.refresh()
            else surveyRunViewModel.open(discovered.id, discovered.label)
        }
    }
    // One answer to "is a collection running on the selected device": the app's own active run,
    // or a run adopted from fresh device evidence. Power actions and the band both read it (B3).
    val deviceRunActive = surveyRunState.activeRun?.active == true ||
        discoverableCurrentPipeline(
            selectedDeviceId,
            pipelineState.pipelines,
            com.example.jetsoncontroller.ui.pipelines.tasksAreFresh(
                pipelineState.controlAvailable,
                pipelineState.observedAtMillis,
                System.currentTimeMillis()
            )
        ) != null
    val requestedMapHistoryEntryIdState = androidx.compose.runtime.saveable.rememberSaveable(selectedDeviceId) {
        mutableStateOf<String?>(null)
    }
    val requestedMapActiveRunIdState = androidx.compose.runtime.saveable.rememberSaveable(selectedDeviceId) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(developerModeEnabled, currentRoute) {
        developerRevocationDestination(currentRoute, developerModeEnabled)?.let {
            navigateToSettingsRoot(navController)
        }
    }

    val openPipelinePreparation: (com.example.jetsoncontroller.model.ManagedPipeline) -> Unit = { pipeline ->
        when (surveyOpenDecision(surveyRunViewModel.uiState.value, pipeline.id)) {
            SurveyOpenDecision.REQUESTED_PIPELINE -> {
                surveyRunViewModel.open(pipeline.id, pipeline.label)
                navController.navigate("survey_run/${Uri.encode(pipeline.id)}") { launchSingleTop = true }
            }
            SurveyOpenDecision.CURRENT_ACTIVE -> navController.navigate(Routes.ACTIVE_RUN) { launchSingleTop = true }
            SurveyOpenDecision.CURRENT_RESULT -> surveyRunViewModel.uiState.value.latestRun?.let { run ->
                navController.navigate(runResultRoute(run.runId, run.deviceId, run.pipelineId)) { launchSingleTop = true }
            }
            SurveyOpenDecision.WAIT_FOR_RESTORE -> Unit
        }
    }
    // "수집 시작" from the device root, the device detail and the pinned card on the 작업 root:
    // open the preparation screen of the field-collection task directly (the last one opened, or
    // the only task on the device). An ambiguous choice opens the task sheet.
    var chooseSurveyTask by androidx.compose.runtime.saveable.rememberSaveable(selectedDeviceId) { mutableStateOf(false) }
    val openSurveyPreparation: () -> Unit = openSurvey@ {
        selectedDeviceId?.let { serverTicketViewModel.clearPendingSelectionIfUnarmed(it) }
        val pipelines = pipelineState.pipelines
        val pipeline = surveyRunState.pipelineId?.takeIf(String::isNotBlank)?.let { id ->
            pipelines.firstOrNull { it.id == id }
        } ?: pipelines.singleOrNull()
        if (pipeline == null) {
            chooseSurveyTask = true
            return@openSurvey
        }
        openPipelinePreparation(pipeline)
    }
    if (chooseSurveyTask) com.example.jetsoncontroller.ui.survey.TaskChoiceSheet(
        state = pipelineState, deviceId = selectedDeviceId.orEmpty(), deviceName = selectedDeviceName,
        ticketTitle = null, onSelect = { pipeline -> chooseSurveyTask = false; openPipelinePreparation(pipeline) },
        onDismissRequest = { chooseSurveyTask = false })
    val openHistoryResult: (com.example.jetsoncontroller.model.TaskRun) -> Unit = { history ->
        val deviceId = history.deviceId ?: history.contextSnapshot?.deviceId ?: fieldState.deviceId
        if (deviceId != null) {
            navController.navigate(historyResultRoute(history, deviceId)) { launchSingleTop = true }
            navController.currentBackStackEntry?.savedStateHandle?.set(
                "resultHistory", com.google.gson.Gson().toJson(history)
            )
        }
    }
    val selectedRegisteredDeviceName = deviceState.registeredDevices.firstOrNull {
        it.deviceId.equals(selectedDeviceId, ignoreCase = true)
    }?.deviceName
    val uploadJobById: (String) -> com.example.jetsoncontroller.model.UploadJob? = { jobId ->
        (listOfNotNull(uploadState.currentJob) + uploadState.queue).firstOrNull { it.id == jobId }
    }

    // Tabs open their roots directly. The 작업 tab no longer chooses between screens by hidden
    // state; the collection screens are reached from the task root itself.
    val onSectionSelected: (GeoTab) -> Unit = { section -> navigateToTab(navController, tabRoute(section)) }

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
            if (currentRoute == Routes.WIFI_DIRECT) {
                navController.popBackStack()
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
            completeDeviceRegistration(navController)
        }
    }

    // Registration problems are shown as an inline banner (connectionNoticeFor). The app
    // never moves the operator to onboarding in the background.

    LaunchedEffect(pipelineState.registrationComplete) {
        if (pipelineState.registrationComplete) {
            navController.popBackStack(Routes.PIPELINES, inclusive = false)
            pipelineViewModel.consumeRegistrationComplete()
        }
    }

    LaunchedEffect(currentRoute, surveyRunState.activeRun?.runId, surveyRunState.online) {
        while (currentRoute == Routes.ACTIVE_RUN && surveyRunState.activeRun?.active == true && surveyRunState.online) {
            surveyRunViewModel.refreshRunTelemetry()
            delay(5_000L)
        }
    }

    // A finished collection stays on the active-run screen until the operator opens its
    // result in place ("결과 보기"). The app does not redirect in the background.

    LaunchedEffect(
        surveyRunState.acceptedStart?.runId,
        surveyRunState.acceptedStart?.clientRequestId,
        surveyRunState.ticketStartRequestId,
        surveyRunState.activeRun?.runId,
        surveyRunState.latestRun?.runId,
        surveyRunState.deviceId,
        surveyRunState.pipelineId
    ) {
        val run = surveyRunState.activeRun ?: surveyRunState.latestRun
        val clientRequestId = surveyRunState.ticketStartRequestId
            ?: surveyRunState.acceptedStart?.clientRequestId
        if (run != null) {
            serverTicketViewModel.observeRun(run, clientRequestId)
        } else {
            val acceptedRunId = surveyRunState.acceptedStart?.runId
            val deviceId = surveyRunState.deviceId
            val pipelineId = surveyRunState.pipelineId
            if (acceptedRunId != null && deviceId != null && pipelineId != null) {
                serverTicketViewModel.observeRunIdentity(acceptedRunId, deviceId, pipelineId, clientRequestId)
            }
        }
    }

    val ticketLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(ticketLifecycleOwner, serverTicketViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serverTicketViewModel.retryPendingReports()
        }
        ticketLifecycleOwner.lifecycle.addObserver(observer)
        if (ticketLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            serverTicketViewModel.retryPendingReports()
        }
        onDispose { ticketLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val connectionNotice = connectionNoticeFor(
        route = currentRoute,
        fullControlConnected = fullControlConnected,
        transportType = connectedTransportType,
        registrationRequired = deviceState.connectionState is ConnectionState.RegistrationRequired,
        hasRegisteredDevice = deviceState.registeredDevices.isNotEmpty()
    )
    val navigationState = AppNavigationState(
        navController = navController,
        developerModePreference = developerModePreference,
        developerModeEnabled = developerModeEnabled,
        repository = repository,
        fieldContext = fieldContext,
        fieldState = fieldState,
        deviceState = deviceState,
        pairingState = pairingState,
        dashboardState = dashboardState,
        networkSettingsState = networkSettingsState,
        wifiDirectState = wifiDirectState,
        dataStorageState = dataStorageState,
        routeStorageState = routeStorageState,
        directServerState = directServerState,
        serverTicketState = serverTicketState,
        ticketPreparation = ticketPreparation,
        runResultState = runResultState,
        resultLogState = resultLogState,
        latestTicketProfileIdState = latestTicketProfileIdState,
        serverStorageState = serverStorageState,
        localTrashState = localTrashState,
        surveyRunState = surveyRunState,
        cameraPreviewState = cameraPreviewState,
        alertSettings = alertSettings,
        alertCenterState = alertCenterState,
        lanEndpoints = lanEndpoints,
        lanDiscoveryError = lanDiscoveryError,
        lanConnectionError = lanConnectionError,
        connectingLanDeviceId = connectingLanDeviceId,
        transportState = transportState,
        currentRoute = currentRoute,
        serverUploadEnabled = serverUploadEnabled,
        serverUploadDisabledReason = serverUploadDisabledReason,
        fullControlConnected = fullControlConnected,
        selectedDeviceId = selectedDeviceId,
        latestSelectedDeviceIdState = latestSelectedDeviceIdState,
        pipelineState = pipelineState,
        uploadState = uploadState,
        selectedDeviceName = selectedDeviceName,
        deviceDashboardState = deviceDashboardState,
        deviceSnapshots = deviceSnapshots,
        currentRegisteredDevice = currentRegisteredDevice,
        connectRegisteredDevice = connectRegisteredDevice,
        selectedSnapshot = selectedSnapshot,
        selectedConnection = selectedConnection,
        deviceAttention = deviceAttention,
        deviceRunActive = deviceRunActive,
        openPipelinePreparation = openPipelinePreparation,
        openSurveyPreparation = openSurveyPreparation,
        openHistoryResult = openHistoryResult,
        selectedRegisteredDeviceName = selectedRegisteredDeviceName,
        uploadJobById = uploadJobById,
        onSectionSelected = onSectionSelected,
        deviceViewModel = deviceViewModel,
        pairingViewModel = pairingViewModel,
        dashboardViewModel = dashboardViewModel,
        networkSettingsViewModel = networkSettingsViewModel,
        wifiDirectViewModel = wifiDirectViewModel,
        dataStorageViewModel = dataStorageViewModel,
        routeStorageViewModel = routeStorageViewModel,
        fieldToolsViewModel = fieldToolsViewModel,
        directServerViewModel = directServerViewModel,
        serverTicketViewModel = serverTicketViewModel,
        serverStorageViewModel = serverStorageViewModel,
        localTrashViewModel = localTrashViewModel,
        uploadViewModel = uploadViewModel,
        pipelineViewModel = pipelineViewModel,
        surveyRunViewModel = surveyRunViewModel,
        cameraPreviewViewModel = cameraPreviewViewModel,
        alertSettingsViewModel = alertSettingsViewModel,
        alertCenterViewModel = alertCenterViewModel,
        bluetoothPermissionGranted = bluetoothPermissionGranted,
        cameraPermissionGranted = cameraPermissionGranted,
        nearbyWifiPermissionGranted = nearbyWifiPermissionGranted,
        wifiScanPermissionGranted = wifiScanPermissionGranted,
        localNetworkPermissionGranted = localNetworkPermissionGranted,
        notificationPermissionGranted = notificationPermissionGranted,
        onRequestBluetoothPermission = onRequestBluetoothPermission,
        onRequestCameraPermission = onRequestCameraPermission,
        onRequestNearbyWifiPermission = onRequestNearbyWifiPermission,
        onRequestWifiScanPermission = onRequestWifiScanPermission,
        onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
        onRequestNotificationPermission = onRequestNotificationPermission,
        pendingDashboardTransportState = pendingDashboardTransportState,
        requestedMapHistoryEntryIdState = requestedMapHistoryEntryIdState,
        requestedMapActiveRunIdState = requestedMapActiveRunIdState
    )
    NoticeDismissalProvider {
        GeoAppShell(
            selected = currentTab(navController),
            navigationVisible = primaryNavigationVisible(currentRoute) &&
                deviceState.registeredDevices.isNotEmpty(),
            onSelect = onSectionSelected,
            mediaInsets = currentRoute in setOf(Routes.CAMERA_PREVIEW, Routes.QR_SCANNER) || navigationState.hasFocusedMediaPreview(),
            paneLayout = currentRoute in paneLayoutRoutes,
            focusedMediaPreview = navigationState.hasFocusedMediaPreview(),
            adaptivePreview = currentRoute == Routes.DATA,
            tabletNavigationVisible = currentRoute == Routes.ACTIVE_RUN && deviceState.registeredDevices.isNotEmpty(),
            attentionSections = alertCenterState.alerts.asSequence().filter {
                !it.read && System.currentTimeMillis() - it.createdAtEpochMillis in 0..86_400_000L
            }.mapNotNull { alert -> when (alert.destination) {
                AlertDestination.DASHBOARD, AlertDestination.STORAGE, AlertDestination.SENSORS -> GeoTab.DEVICES
                AlertDestination.UPLOAD_QUEUE -> GeoTab.TRANSFERS
                else -> null
            } }.toSet()
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!fullControlConnected) connectionNotice?.let { notice ->
                    GeoInlineNote(notice.message, tone = notice.tone, kind = GeoNoteKind.LIVE_ERROR,
                        actionLabel = "연결 문제 해결", onAction = { navigateToDeviceRoot(navController) })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    com.example.jetsoncontroller.ui.zone.ZoneObservationHost(navigationState) {
                        AppNavHost(navigationState, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusPollingLifecycleEffect(viewModel: DashboardViewModel) {
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
    navController.navigateSingle(Routes.PIPELINES)
}

internal fun resetToDeviceRoot(navController: NavHostController) = navigateToDeviceRoot(navController)

private fun resultRoutePart(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

internal fun resultReturnRoute(sourceRoute: String?): String = sourceRoute ?: Routes.TASKS

internal fun runResultRoute(runId: String, deviceId: String, pipelineId: String? = null, historyId: String? = null): String =
    "run_result/${resultRoutePart(runId)}?deviceId=${resultRoutePart(deviceId)}" +
        (pipelineId?.let { "&pipelineId=${resultRoutePart(it)}" } ?: "") +
        (historyId?.let { "&historyId=${resultRoutePart(it)}" } ?: "")

internal fun historyResultRoute(run: com.example.jetsoncontroller.model.TaskRun, deviceId: String): String {
    val runId = run.runId?.takeIf(String::isNotBlank)
        ?: com.example.jetsoncontroller.ui.field.canonicalHistoryRunId(run)
    return if (runId != null) runResultRoute(runId, deviceId, run.pipelineId, run.id)
    else "history_result/${resultRoutePart(run.id)}?deviceId=${resultRoutePart(deviceId)}"
}

internal fun resultLogEntry(run: com.example.jetsoncontroller.model.PipelineRun): com.example.jetsoncontroller.model.TaskRun =
    com.example.jetsoncontroller.model.TaskRun(
        id = run.runId, pipelineId = run.pipelineId, label = "수집 결과", logId = run.logId,
        startedAt = run.startedAt, finishedAt = run.finishedAt, state = run.state,
        runId = run.runId, deviceId = run.deviceId, active = run.active
    )

internal fun resultAwaitingReview(state: com.example.jetsoncontroller.ui.survey.SurveyRunUiState): Boolean {
    val run = state.latestRun ?: return false
    return !run.active && !state.resultAcknowledged && state.pendingStart == null &&
        state.unconfirmedRunId == null && run.runId.isNotBlank() &&
        run.deviceId.equals(state.deviceId, ignoreCase = true) && run.pipelineId == state.pipelineId
}

internal enum class SurveyOpenDecision {
    REQUESTED_PIPELINE,
    CURRENT_ACTIVE,
    CURRENT_RESULT,
    WAIT_FOR_RESTORE
}

internal fun surveyOpenDecision(
    state: com.example.jetsoncontroller.ui.survey.SurveyRunUiState,
    requestedPipelineId: String
): SurveyOpenDecision = when {
    state.restoringLocalState -> SurveyOpenDecision.WAIT_FOR_RESTORE
    state.pendingStart != null || state.unconfirmedRunId != null || state.activeRun?.active == true ||
        state.acceptedStart != null -> SurveyOpenDecision.CURRENT_ACTIVE
    resultAwaitingReview(state) -> SurveyOpenDecision.CURRENT_RESULT
    else -> SurveyOpenDecision.REQUESTED_PIPELINE
}

internal fun collectionOpenDecision(
    state: com.example.jetsoncontroller.ui.survey.SurveyRunUiState
): SurveyOpenDecision = surveyOpenDecision(state, state.pipelineId.orEmpty())

internal fun matchingStopTarget(
    run: com.example.jetsoncontroller.model.PipelineRun?,
    pipelines: List<com.example.jetsoncontroller.model.ManagedPipeline>
): com.example.jetsoncontroller.model.ManagedPipeline? {
    if (run?.active != true) return null
    return pipelines.firstOrNull { pipeline ->
        pipeline.id == run.pipelineId &&
            pipeline.state == com.example.jetsoncontroller.model.PipelineState.RUNNING &&
            (pipeline.activeRunId == run.runId ||
                (pipeline.execution?.runId == run.runId && pipeline.execution.active))
    }
}

/** Finds route history only when it belongs to the exact live contextual run. */
internal fun activeCollectionHistoryRun(
    activeRun: com.example.jetsoncontroller.model.PipelineRun?,
    history: List<com.example.jetsoncontroller.model.TaskRun>
): com.example.jetsoncontroller.model.TaskRun? {
    if (activeRun?.active != true || activeRun.runId.isBlank()) return null
    return history.firstOrNull { run ->
        run.runId == activeRun.runId && run.pipelineId == activeRun.pipelineId &&
            (run.deviceId == null || run.deviceId.equals(activeRun.deviceId, ignoreCase = true))
    }
}

/** Adopts one exact contextual run discovered from the device after a cold app start. */
internal fun discoverableCurrentPipeline(
    deviceId: String?,
    pipelines: List<com.example.jetsoncontroller.model.ManagedPipeline>,
    tasksFresh: Boolean
): com.example.jetsoncontroller.model.ManagedPipeline? {
    if (!tasksFresh || deviceId.isNullOrBlank()) return null
    return pipelines.filter { pipeline ->
        pipeline.state == com.example.jetsoncontroller.model.PipelineState.RUNNING &&
            !pipeline.activeRunId.isNullOrBlank() &&
            pipeline.contextualStart?.let { receipt ->
                receipt.runId == pipeline.activeRunId &&
                    receipt.contextSnapshot.deviceId.equals(deviceId, ignoreCase = true) &&
                    receipt.preflightSnapshot.pipelineId == pipeline.id
            } == true
    }.singleOrNull()
}

/** Switches Home to fresh device evidence once the previous collection is fully resolved. */
internal fun shouldAdoptDiscoveredPipeline(
    state: com.example.jetsoncontroller.ui.survey.SurveyRunUiState,
    discovered: com.example.jetsoncontroller.model.ManagedPipeline?
): Boolean {
    if (discovered == null || state.restoringLocalState || state.contextLocked) return false
    val knownActiveRunId = state.activeRun?.takeIf { it.active }?.runId
        ?: state.latestRun?.takeIf { it.active }?.runId
    return state.pipelineId != discovered.id || knownActiveRunId != discovered.activeRunId
}

internal data class ServerReceiptPresentation(
    val label: String?,
    val tone: com.example.jetsoncontroller.ui.model.Tone
)

internal fun serverReceiptPresentation(
    run: com.example.jetsoncontroller.model.PipelineRun?,
    jobs: List<com.example.jetsoncontroller.model.UploadJob>
): ServerReceiptPresentation {
    if (run == null) return ServerReceiptPresentation(
        null,
        com.example.jetsoncontroller.ui.model.Tone.UNKNOWN
    )
    val expectedContext = run.uploadContext
    val exactJobs = jobs.filter { candidate ->
        candidate.context?.let { context ->
            context.runId == expectedContext.runId &&
                context.deviceId.equals(expectedContext.deviceId, ignoreCase = true) &&
                context.pipelineId == expectedContext.pipelineId &&
                context.surveyProjectId == expectedContext.surveyProjectId &&
                context.surveySectionId == expectedContext.surveySectionId &&
                context.sourceRevision == expectedContext.sourceRevision &&
                context.configSha256 == expectedContext.configSha256 &&
                context.outputId == expectedContext.outputId
        } == true
    }
    if (exactJobs.isEmpty()) return ServerReceiptPresentation(
        null,
        com.example.jetsoncontroller.ui.model.Tone.UNKNOWN
    )
    if (exactJobs.any { it.verification?.matched == true }) return ServerReceiptPresentation(
        "서버 수신 확인됨",
        com.example.jetsoncontroller.ui.model.Tone.SUCCESS
    )
    if (exactJobs.any { job ->
            job.state in setOf(
                com.example.jetsoncontroller.model.UploadJobState.QUEUED,
                com.example.jetsoncontroller.model.UploadJobState.SCANNING,
                com.example.jetsoncontroller.model.UploadJobState.UPLOADING
            )
        }
    ) return ServerReceiptPresentation(
        "파일 전송 중",
        com.example.jetsoncontroller.ui.model.Tone.PENDING
    )
    if (exactJobs.any { job ->
            job.state == com.example.jetsoncontroller.model.UploadJobState.COMPLETED &&
                job.verification?.matched == null
        }
    ) return ServerReceiptPresentation(
        "전송 완료 · 수신 검증 필요",
        com.example.jetsoncontroller.ui.model.Tone.PENDING
    )
    // Callers provide the current attempt first, followed by the newest queued history.
    val job = exactJobs.first()
    val verification = job.verification
    return when {
        verification?.matched == false -> ServerReceiptPresentation(
            "서버 수신 검증 불일치",
            com.example.jetsoncontroller.ui.model.Tone.WARNING
        )
        else -> ServerReceiptPresentation(
            "서버 수신 확인 필요",
            com.example.jetsoncontroller.ui.model.Tone.WARNING
        )
    }
}

internal fun connectionAttemptCompleted(
    expectedTransport: TransportType?,
    transportState: TransportState
): Boolean = expectedTransport != null &&
    transportState is TransportState.Connected &&
    transportState.type == expectedTransport

internal fun alertDestinationRoute(alert: com.example.jetsoncontroller.data.alerts.AlertRecord): String {
    val target = alert.target?.takeIf { it.deviceId.isNotBlank() } ?: return alertDestinationRoute(alert.destination)
    return when {
        target.transferJob != null -> Routes.UPLOAD_PROGRESS
        !target.runId.isNullOrBlank() -> runResultRoute(target.runId, target.deviceId, target.pipelineId)
        alert.destination in setOf(AlertDestination.DASHBOARD, AlertDestination.STORAGE, AlertDestination.SENSORS) -> Routes.DEVICE_DETAIL
        else -> alertDestinationRoute(alert.destination)
    }
}

internal fun alertDestinationRoute(destination: AlertDestination): String = when (destination) {
    AlertDestination.DASHBOARD -> Routes.DEVICE_BROWSE
    AlertDestination.STORAGE -> Routes.DATA
    AlertDestination.SENSORS -> Routes.SENSORS
    AlertDestination.PIPELINES -> Routes.WORK_BROWSE
    AlertDestination.UPLOAD_QUEUE -> Routes.TRANSFER_BROWSE
}

internal fun deviceRegistrationRedirectAllowed(currentRoute: String?): Boolean =
    currentRoute != "server_storage"
