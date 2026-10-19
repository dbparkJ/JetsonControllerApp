package com.example.jetsoncontroller.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.alerts.AlertCenterViewModel
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.dashboard.DashboardViewModel
import com.example.jetsoncontroller.ui.devices.DeviceListViewModel
import com.example.jetsoncontroller.ui.network.NetworkSettingsViewModel
import com.example.jetsoncontroller.ui.pairing.PairingViewModel
import com.example.jetsoncontroller.ui.pipelines.PipelineViewModel
import com.example.jetsoncontroller.ui.sensors.CameraPreviewViewModel
import com.example.jetsoncontroller.ui.settings.AlertSettingsViewModel
import com.example.jetsoncontroller.ui.storage.DeviceStorageViewModel
import com.example.jetsoncontroller.ui.storage.ServerStorageViewModel
import com.example.jetsoncontroller.ui.survey.SurveyRunViewModel
import com.example.jetsoncontroller.ui.upload.UploadViewModel
import com.example.jetsoncontroller.ui.wifi.WifiDirectViewModel
import kotlinx.coroutines.flow.first

/** App-scoped state is refreshed without rebuilding a destination or its saved-state owner. */
internal class AppNavigationState(
    val navController: NavHostController,
    val developerModePreference: com.example.jetsoncontroller.ui.settings.DeveloperModePreference,
    val developerModeEnabled: Boolean,
    val repository: JetsonRepository,
    val fieldContext: android.content.Context,
    val fieldState: com.example.jetsoncontroller.ui.field.FieldState,
    val deviceState: com.example.jetsoncontroller.ui.devices.DeviceListUiState,
    val pairingState: com.example.jetsoncontroller.ui.pairing.PairingUiState,
    val dashboardState: com.example.jetsoncontroller.ui.dashboard.DashboardUiState,
    val networkSettingsState: com.example.jetsoncontroller.ui.network.NetworkSettingsUiState,
    val wifiDirectState: com.example.jetsoncontroller.data.network.WifiDirectState,
    val dataStorageState: com.example.jetsoncontroller.ui.storage.DeviceStorageUiState,
    val routeStorageState: com.example.jetsoncontroller.ui.storage.DeviceStorageUiState,
    val directServerState: com.example.jetsoncontroller.ui.storage.DirectServerUiState,
    val serverTicketState: com.example.jetsoncontroller.ui.tasks.ServerTicketUiState,
    val ticketPreparation: com.example.jetsoncontroller.ui.tasks.TicketPreparation?,
    val runResultState: com.example.jetsoncontroller.ui.survey.RunResultUiState,
    val resultLogState: com.example.jetsoncontroller.ui.survey.RunLogUiState,
    private val latestTicketProfileIdState: State<String?>,
    val serverStorageState: com.example.jetsoncontroller.ui.storage.ServerStorageUiState,
    val localTrashState: com.example.jetsoncontroller.ui.storage.LocalTrashUiState,
    val surveyRunState: com.example.jetsoncontroller.ui.survey.SurveyRunUiState,
    val cameraPreviewState: com.example.jetsoncontroller.ui.sensors.CameraPreviewUiState,
    val alertSettings: com.example.jetsoncontroller.data.alerts.AlertSettings,
    val alertCenterState: com.example.jetsoncontroller.ui.alerts.AlertCenterUiState,
    val lanEndpoints: List<com.example.jetsoncontroller.model.DeviceEndpoint>,
    val lanDiscoveryError: String?,
    val lanConnectionError: String?,
    val connectingLanDeviceId: String?,
    val transportState: TransportState,
    val currentRoute: String?,
    val serverUploadEnabled: Boolean,
    val serverUploadDisabledReason: String,
    val fullControlConnected: Boolean,
    val selectedDeviceId: String?,
    private val latestSelectedDeviceIdState: State<String?>,
    val pipelineState: com.example.jetsoncontroller.ui.pipelines.PipelineUiState,
    val uploadState: com.example.jetsoncontroller.ui.upload.UploadUiState,
    val selectedDeviceName: String,
    val deviceDashboardState: com.example.jetsoncontroller.ui.dashboard.DashboardUiState,
    val deviceSnapshots: Map<String, com.example.jetsoncontroller.data.devices.DeviceSnapshot>,
    val currentRegisteredDevice: RegisteredDevice?,
    val connectRegisteredDevice: (RegisteredDevice) -> Unit,
    val selectedSnapshot: com.example.jetsoncontroller.data.devices.DeviceSnapshot?,
    val selectedConnection: com.example.jetsoncontroller.ui.connection.DeviceConnectionUi,
    val deviceAttention: com.example.jetsoncontroller.ui.devices.DeviceAttention?,
    val deviceRunActive: Boolean,
    val openPipelinePreparation: (com.example.jetsoncontroller.model.ManagedPipeline) -> Unit,
    val openSurveyPreparation: () -> Unit,
    val openHistoryResult: (com.example.jetsoncontroller.model.TaskRun) -> Unit,
    val selectedRegisteredDeviceName: String?,
    val uploadJobById: (String) -> com.example.jetsoncontroller.model.UploadJob?,
    val onSectionSelected: (ControlSection) -> Unit,
    val deviceViewModel: DeviceListViewModel,
    val pairingViewModel: PairingViewModel,
    val dashboardViewModel: DashboardViewModel,
    val networkSettingsViewModel: NetworkSettingsViewModel,
    val wifiDirectViewModel: WifiDirectViewModel,
    val dataStorageViewModel: DeviceStorageViewModel,
    val routeStorageViewModel: DeviceStorageViewModel,
    val fieldToolsViewModel: com.example.jetsoncontroller.ui.field.FieldToolsViewModel,
    val directServerViewModel: com.example.jetsoncontroller.ui.storage.DirectServerViewModel,
    val serverTicketViewModel: com.example.jetsoncontroller.ui.tasks.ServerTicketViewModel,
    val serverStorageViewModel: ServerStorageViewModel,
    val localTrashViewModel: com.example.jetsoncontroller.ui.storage.LocalTrashViewModel,
    val uploadViewModel: UploadViewModel,
    val pipelineViewModel: PipelineViewModel,
    val surveyRunViewModel: SurveyRunViewModel,
    val cameraPreviewViewModel: CameraPreviewViewModel,
    val alertSettingsViewModel: AlertSettingsViewModel,
    val alertCenterViewModel: AlertCenterViewModel,
    val bluetoothPermissionGranted: Boolean,
    val cameraPermissionGranted: Boolean,
    val nearbyWifiPermissionGranted: Boolean,
    val wifiScanPermissionGranted: Boolean,
    val localNetworkPermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val onRequestBluetoothPermission: () -> Unit,
    val onRequestCameraPermission: () -> Unit,
    val onRequestNearbyWifiPermission: () -> Unit,
    val onRequestWifiScanPermission: () -> Unit,
    val onRequestLocalNetworkPermission: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val pendingDashboardTransportState: androidx.compose.runtime.MutableState<TransportType?>,
    val requestedMapHistoryEntryIdState: androidx.compose.runtime.MutableState<String?>,
    val requestedMapActiveRunIdState: androidx.compose.runtime.MutableState<String?>
) {
    val latestTicketProfileId by latestTicketProfileIdState
    val latestSelectedDeviceId by latestSelectedDeviceIdState
    var pendingDashboardTransport by pendingDashboardTransportState
    var requestedMapHistoryEntryId by requestedMapHistoryEntryIdState
    var requestedMapActiveRunId by requestedMapActiveRunIdState
}

/** Today's browsers own their previews internally rather than as separate destinations. */
internal fun AppNavigationState.hasFocusedMediaPreview(): Boolean = when (currentRoute) {
    Routes.DATA -> dataStorageState.preview?.let { mediaPreviewFocused(it.mimeType, it.name) } == true
    Routes.STORAGE_ROUTE -> routeStorageState.preview?.let { mediaPreviewFocused(it.mimeType, it.name) } == true
    Routes.SERVER_STORAGE -> directServerState.preview?.let { mediaPreviewFocused(it.mediaType, it.fileName) } == true
    Routes.SERVER_PROXY_STORAGE -> serverStorageState.preview?.let { mediaPreviewFocused(it.mimeType, it.name) } == true
    else -> false
}

@Composable
internal fun AppNavHost(state: AppNavigationState, modifier: Modifier = Modifier) {
    val latest = rememberUpdatedState(state)
    // The VM starts with an empty list before its first subscription. Read the restored store
    // once instead, and freeze the entry route so subsequent registrations never rebuild graphs.
    val restoredEntry by produceState<String?>(null, state.repository) {
        value = runCatching {
            val app = state.fieldContext.applicationContext as com.example.jetsoncontroller.JetsonApplication
            initialWorkRoute(app.credentialStore.registeredDevices.first().size)
        }.getOrDefault(Routes.TASKS) // Restore-error scenes belong to the startup slice.
    }
    val firstWorkRoute = restoredEntry ?: return
    NavHost(state.navController, startDestination = tabGraphRoute(Routes.TASKS), modifier = modifier) {
        primaryTabRoutes.forEach { root ->
            navigation(startDestination = if (root == Routes.TASKS) firstWorkRoute else root, route = tabGraphRoute(root)) {
                workGraph(latest, includeRoot = root == Routes.TASKS)
                deviceGraph(latest, includeRoot = root == Routes.DEVICES)
                transferGraph(latest, includeRoot = root == Routes.TRANSFERS)
                settingsGraph(latest, includeRoot = root == Routes.SETTINGS)
                devGraph(latest)
                focusedRoutes(latest)
            }
        }
    }
    BackHandler(enabled = state.hasFocusedMediaPreview() || rootBackDestination(state.currentRoute) != null) {
        if (state.hasFocusedMediaPreview()) {
            when (state.currentRoute) {
                Routes.DATA -> state.dataStorageViewModel.navigateBack()
                Routes.STORAGE_ROUTE -> state.routeStorageViewModel.navigateBack()
                Routes.SERVER_STORAGE -> state.directServerViewModel.navigateBack()
                Routes.SERVER_PROXY_STORAGE -> state.serverStorageViewModel.navigateBack()
            }
        } else navigateToWorkRoot(state.navController)
    }
}
