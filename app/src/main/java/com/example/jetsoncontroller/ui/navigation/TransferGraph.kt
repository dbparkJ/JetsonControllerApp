package com.example.jetsoncontroller.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.transfers.*
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.data.server.TicketReportDeliveryState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import com.google.gson.Gson
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.storage.DeviceStorageScreen
import com.example.jetsoncontroller.ui.upload.UploadConfirmScreen
import com.example.jetsoncontroller.ui.upload.UploadProgressScreen
import com.example.jetsoncontroller.ui.upload.UploadQueueScreen
import com.example.jetsoncontroller.ui.tasks.WorkDeviceChoiceRow
import com.example.jetsoncontroller.ui.model.Tone
import kotlinx.coroutines.delay

internal fun NavGraphBuilder.transferGraph(state: State<AppNavigationState>, includeRoot: Boolean = false) {
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
            with(state.value) {
                val rootId = backStackEntry.arguments?.getString("rootId").orEmpty()
                val initialPath = backStackEntry.arguments?.getString("path").orEmpty()
                LaunchedEffect(rootId, initialPath) {
                    if (rootId.isNotEmpty()) {
                        routeStorageViewModel.openLocation(rootId, initialPath)
                    } else {
                        routeStorageViewModel.openCollection()
                    }
                }
                DeviceStorageScreen(
                    onDismissMessage = routeStorageViewModel::dismissMessage,
                    onTransferQueue = { navController.navigateSingle(Routes.DATA) },
                    onTrash = { navController.navigateSingle(Routes.LOCAL_TRASH) },
                    onConnectionRemedy = { navigateToDeviceRoot(navController) },
                    onReconnect = { currentRegisteredDevice?.let(connectRegisteredDevice) },
                    ongoingRun = { TransferOngoingRun(state.value) },
                    thumbnailLoader = { entry -> repository.getFile(routeStorageState.currentRoot!!.id, entry.relativePath) },
                    state = routeStorageState,
                    serverUploadEnabled = serverUploadEnabled,
                    serverUploadDisabledReason = serverUploadDisabledReason,
                    onBack = {
                        if (!routeStorageViewModel.navigateBack()) {
                            navController.popBackStack()
                        }
                    },
                    onRefresh = routeStorageViewModel::refresh,
                    onDirectoryClick = { routeStorageViewModel.selectDirectory(it) },
                    onFileClick = routeStorageViewModel::openFile,
                    onDeleteClick = routeStorageViewModel::deleteEntry,
                    onUndoDelete = routeStorageViewModel::undoDelete,
                    onUploadClick = { rootId, path ->
                        navController.navigateSingle(
                            "upload_confirm/${Uri.encode(rootId)}?path=${Uri.encode(path)}"
                        )
                    },
                    onSectionSelected = onSectionSelected,
                    onServerDataClick = {
                        navController.navigateSingle(Routes.SERVER_STORAGE) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(Routes.SERVER_STORAGE) {
            with(state.value) {
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
                    onEmptyTrash = directServerViewModel::emptyTrash,
                    onRemoveProfile = directServerViewModel::removeSelectedProfile,
                    onDismissMessage = directServerViewModel::dismissMessage,
                    developerModeEnabled = developerModeEnabled,
                    showTechnicalInfo = com.example.jetsoncontroller.ui.dev.rememberTechnicalInfo().value,
                    onServerConnection = { navController.navigateSingle(SETTINGS_SERVER_ROUTE) },
                    onRelogin = { navController.navigateSingle(SETTINGS_SERVER_ROUTE) },
                    onDeveloperTrash = if (developerModeEnabled) ({ navController.navigateSingle("dev/server-trash") }) else null,
                    ongoingRun = { TransferOngoingRun(state.value) },
                    onDeviceData = {
                        navController.navigateSingle(Routes.DATA) { launchSingleTop = true }
                    },
                    onJetsonServerProxy = if (developerModeEnabled && serverStorageState.controlAvailable) {
                        { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.SERVER_PROXY.route) { launchSingleTop = true } }
                    } else null
                )
            }
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
            with(state.value) {
                val rootId = backStackEntry.arguments?.getString("rootId").orEmpty()
                val path = backStackEntry.arguments?.getString("path").orEmpty()
                val linkedRunId = backStackEntry.arguments?.getString("runId")?.takeIf(String::isNotBlank)
                val confirmationDevice = androidx.compose.runtime.saveable.rememberSaveable { selectedDeviceId.orEmpty() }
                LaunchedEffect(selectedDeviceId) {
                    if (confirmationDevice != selectedDeviceId.orEmpty()) {
                        uploadViewModel.confirmationClosedForDeviceChange()
                        navController.popBackStack()
                    }
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
                    serverUploadEnabled = serverUploadEnabled && uploadState.controlAvailable && dashboardState.capabilities.uploads && confirmationDevice == selectedDeviceId.orEmpty(),
                    serverUploadDisabledReason = serverUploadDisabledReason,
                    isLoading = uploadState.isLoading,
                    error = uploadState.error,
                    onBack = { navController.popBackStack() },
                    onRefresh = {
                        uploadViewModel.refresh()
                        uploadViewModel.loadSourceSummary(rootId, path, force = true, linkedRunId = linkedRunId)
                    },
                    onManageTargets = {
                        navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.UPLOAD_TARGETS.route)
                    },
                    onConfirm = { targetId ->
                        uploadViewModel.startUpload(rootId, path, targetId, expectedDeviceId = confirmationDevice) {
                            openTransferDetail(selectedDeviceId.orEmpty(), it)
                        }
                    },
                    onConnectionRemedy = { navController.navigateSingle(Routes.NETWORK_SETTINGS) },
                    ongoingRun = { TransferOngoingRun(state.value) },
                    developerModeEnabled = developerModeEnabled
                )
            }
        }


        composable(Routes.UPLOAD_PROGRESS) { entry ->
            GeoPaneLayout(singlePane = GeoPaneSelection.DETAIL,
                list = { TransferRootRoute(state) }, detail = { TransferDetailDestination(state, entry) })
        }

    (listOf(Routes.TRANSFER_BROWSE) + if (includeRoot) listOf(Routes.TRANSFERS) else emptyList()).forEach { rootRoute ->
        composable(rootRoute) { entry ->
            GeoPaneLayout(singlePane = GeoPaneSelection.LIST,
                list = { TransferRootRoute(state) }, detail = {
                    val current = state.value
                    val job = current.uploadState.queue.firstOrNull { it.verification?.matched == true }
                        ?: current.uploadState.currentJob ?: current.uploadState.queue.firstOrNull()
                    key(current.selectedDeviceId, job?.id) { TransferDetailDestination(state, entry, job) }
                })
        }
    }

        composable(Routes.UPLOAD_QUEUE) {
            with(state.value) {
                val logDeviceId = rememberSaveable { mutableStateOf(selectedDeviceId) }.value
                var cachedQueue by remember { mutableStateOf(uploadState.queue) }
                val sameDevice = logDeviceId.equals(selectedDeviceId, true)
                if (sameDevice) SideEffect { cachedQueue = uploadState.queue }
                UploadQueueScreen(
                    queue = if (sameDevice) uploadState.queue else cachedQueue,
                    targets = uploadState.targets,
                    isLoading = sameDevice && uploadState.isLoading,
                    error = if (sameDevice) uploadState.error else null,
                    message = if (sameDevice) uploadState.message else null,
                    onRefresh = { if (sameDevice) uploadViewModel.loadQueue() },
                    onManageTargets = {
                        navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.UPLOAD_TARGETS.route)
                    },
                    onJobClick = { job ->
                        openTransferDetail(logDeviceId.orEmpty(), job)
                    },
                    onDeleteJob = uploadViewModel::deleteJobFromQueue,
                    mutationEnabled = fullControlConnected && uploadState.controlAvailable && sameDevice,
                    deviceId = logDeviceId,
                    currentDeviceId = selectedDeviceId,
                    onDeviceConfirmationClosed = uploadViewModel::confirmationClosedForDeviceChange,
                    onFixedDelete = { uploadViewModel.mutateTransfer(it, TransferMutation.DELETE_RECORD) },
                    ongoingRun = { OngoingRunStatus(state.value, logDeviceId,
                        deviceState.registeredDevices.firstOrNull { it.deviceId.equals(logDeviceId, true) }?.deviceName ?: "선택한 장치") },
                    jobDates = fieldState.runs.mapNotNull { run -> uploadState.queue.firstOrNull {
                        it.context?.runId == run.runId }?.id?.let { it to run.startedAt } }.toMap(),
                    onBack = { navController.popBackStack() },
                    developerModeEnabled = developerModeEnabled,
                    onHome = { resetToDeviceRoot(navController) },
                    onFiles = { navController.navigateSingle(Routes.DATA) { launchSingleTop = true } },
                    onDismissMessage = uploadViewModel::dismissMessage
                )
            }
        }

        composable(Routes.DATA) {
            with(state.value) {
                com.example.jetsoncontroller.ui.storage.StorageSendBrowser(
                    state = dataStorageState,
                    targets = uploadState.targets,
                    summary = uploadState.sourceSummary,
                    calculating = uploadState.isCalculatingSource,
                    sending = uploadState.isLoading,
                    sendEnabled = serverUploadEnabled && uploadState.controlAvailable && dashboardState.capabilities.uploads,
                    disabledReason = serverUploadDisabledReason,
                    onBack = { if (!dataStorageViewModel.navigateBack()) navController.popBackStack() },
                    onParent = { dataStorageViewModel.navigateBack() },
                    onDirectory = dataStorageViewModel::selectDirectory,
                    onFilePreview = dataStorageViewModel::openFile,
                    onSelect = { root, path ->
                        uploadViewModel.loadSourceSummary(root, path)
                        transferSendDestination(TransferSendEvent.SELECT_SOURCE)?.let { navController.navigateSingle(it) }
                    },
                    onStart = { root, path, target -> uploadViewModel.startUpload(root, path, target, expectedDeviceId = selectedDeviceId) {
                        openTransferDetail(selectedDeviceId.orEmpty(), it)
                    } },
                    onRefresh = { dataStorageViewModel.refresh(); uploadViewModel.refresh() },
                    onRemedy = { navController.navigateSingle(Routes.NETWORK_SETTINGS) },
                    onConnection = { navigateToDeviceRoot(navController) },
                    onReconnect = { currentRegisteredDevice?.let(connectRegisteredDevice) },
                    onDeleteClick = dataStorageViewModel::deleteEntry,
                    onTrash = { navController.navigateSingle(Routes.LOCAL_TRASH) },
                    onDismissMessage = dataStorageViewModel::dismissMessage,
                    onUndoDelete = dataStorageViewModel::undoDelete,
                    error = uploadState.error,
                    ongoingRun = { TransferOngoingRun(state.value) }
                )
            }
        }

        composable(Routes.LOCAL_TRASH) {
            with(state.value) {
                com.example.jetsoncontroller.ui.storage.LocalTrashScreen(
                    state = localTrashState,
                    onBack = { navController.popBackStack() },
                    onRefresh = localTrashViewModel::refresh,
                    onRestore = localTrashViewModel::restore,
                    onEmptyTrash = localTrashViewModel::emptyTrash,
                    onDismissMessage = localTrashViewModel::dismissMessage,
                    ongoingRun = { TransferOngoingRun(state.value) },
                    developerModeEnabled = developerModeEnabled,
                    showTechnicalInfo = com.example.jetsoncontroller.ui.dev.rememberTechnicalInfo().value
                )
            }
        }

}

@Composable
private fun TransferDetailDestination(state: State<AppNavigationState>, entry: NavBackStackEntry, initialJob: UploadJob? = null) {
    with(state.value) {
        val previous = navController.previousBackStackEntry?.savedStateHandle
        val requestedDevice by entry.savedStateHandle.getStateFlow<String?>("detailDevice", null).collectAsState()
        val requestedName by entry.savedStateHandle.getStateFlow<String?>("detailName", null).collectAsState()
        val requestedId by entry.savedStateHandle.getStateFlow<String?>("detailId", null).collectAsState()
        val fallbackDevice = rememberSaveable { if (initialJob != null) selectedDeviceId.orEmpty()
            else previous?.get<String>("pushedTransferDevice") ?: selectedDeviceId.orEmpty() }
        val fallbackName = rememberSaveable { if (initialJob != null) selectedDeviceName
            else previous?.get<String>("pushedTransferName") ?: selectedDeviceName }
        val fallbackId = rememberSaveable { mutableStateOf(initialJob?.id ?: previous?.get<String>("pushedTransferId") ?: uploadState.currentJob?.id) }.value
        val targetDevice = requestedDevice ?: fallbackDevice
        val targetName = requestedName ?: fallbackName
        val targetJobId = requestedId ?: fallbackId
        val gson = remember { Gson() }
        var cachedJob by remember(targetDevice, targetJobId) {
            mutableStateOf((entry.savedStateHandle.get<String>("detailSeed") ?: entry.savedStateHandle.get<String>("detailJob") ?: previous?.get<String>("pushedTransferJob"))
                ?.let { runCatching { gson.fromJson(it, UploadJob::class.java) }.getOrNull() }?.takeIf { it.id == targetJobId }
                ?: initialJob?.takeIf { it.id == targetJobId }
                ?: uploadState.currentJob?.takeIf { it.id == targetJobId })
        }
        var cachedRun by remember(targetDevice, targetJobId) { mutableStateOf(entry.savedStateHandle.get<String>("detailRun")
            ?.let { runCatching { gson.fromJson(it, PipelineRun::class.java) }.getOrNull() }) }
        LaunchedEffect(targetDevice, targetJobId) {
            if (targetDevice.equals(selectedDeviceId, true)) cachedJob?.let(uploadViewModel::openJob)
        }
        val sameDevice = targetDevice.equals(selectedDeviceId, true)
        val observed = (listOfNotNull(uploadState.currentJob) + uploadState.queue).firstOrNull {
            sameDevice && it.id == targetJobId
        }
        val job = observed ?: cachedJob
        val run = listOfNotNull(
            uploadState.transferRun.takeIf { sameDevice }, runResultState.run, cachedRun,
            surveyRunState.latestRun
        ).firstOrNull { com.example.jetsoncontroller.ui.upload.uploadSourceReceipt(it, job, targetDevice) != null }
        SideEffect {
            if (observed != null) { cachedJob = observed; entry.savedStateHandle["detailJob"] = gson.toJson(observed) }
            if (run != null) { cachedRun = run; entry.savedStateHandle["detailRun"] = gson.toJson(run) }
        }
        var receivingLabel by rememberSaveable(targetDevice, targetJobId) { mutableStateOf(uploadState.targets.firstOrNull { it.id == job?.targetId }?.let(::transferTargetLabel)) }
        if (sameDevice) SideEffect { uploadState.targets.firstOrNull { it.id == job?.targetId }?.let(::transferTargetLabel)?.let { receivingLabel = it } }
        val reportRunId = job?.context?.takeIf { it.deviceId.equals(targetDevice, true) }?.runId
        val link = reportRunId?.let { serverTicketViewModel.linkForRun(it, targetDevice) }
        LaunchedEffect(link?.profileId) { link?.profileId?.let(serverTicketViewModel::refreshCapabilities) }
        val completion = com.example.jetsoncontroller.ui.tasks.ticketCompletionPresentation(
            attached = link != null,
            serverReceiptConfirmed = job?.let { matchingTransferVerification(it)?.matched } == true,
            role = link?.profileId?.let(serverTicketViewModel::reportRole),
            conflicted = reportRunId?.let(serverTicketViewModel::ticketConflictForRun) == true,
            startRejected = reportRunId?.let(serverTicketViewModel::ticketStartRejected) == true,
            startRejectedMessage = reportRunId?.let(serverTicketViewModel::ticketStartFailureMessage)
        )
        val reportDelivery = reportRunId?.let { serverTicketViewModel.reportDeliveryState(it, true) }
        val reportFailed = reportDelivery == TicketReportDeliveryState.FAILED
        UploadProgressScreen(
            job = job, verification = job?.verification,
            sourceReceipt = com.example.jetsoncontroller.ui.upload.uploadSourceReceipt(run, job, targetDevice),
            isLoading = sameDevice && uploadState.isLoading,
            message = if (sameDevice) uploadState.message else null,
            error = if (sameDevice) uploadState.error else null,
            onCancel = uploadViewModel::cancelCurrentUpload,
            onRetry = uploadViewModel::retryCurrentUpload,
            onVerify = uploadViewModel::verifyCurrentUpload,
            onDeleteSource = uploadViewModel::deleteCurrentSource,
            onRestoreSource = uploadViewModel::restoreCurrentSource,
            onBack = { navController.popBackStack() },
            serverMutationEnabled = sameDevice && uploadState.controlAvailable && serverUploadEnabled,
            serverMutationDisabledReason = serverUploadDisabledReason,
            deviceDeletionEnabled = sameDevice && uploadState.controlAvailable && fullControlConnected,
            deviceId = targetDevice, deviceName = targetName, currentDeviceId = selectedDeviceId,
            onDeviceConfirmationClosed = uploadViewModel::confirmationClosedForDeviceChange,
            developerModeEnabled = developerModeEnabled,
            targetLabel = receivingLabel,
            linkedToTicket = link != null, ticketTitle = link?.title,
            reportEnabled = completion.enabled && (reportFailed || run != null) && reportDelivery !in setOf(
                TicketReportDeliveryState.PENDING, TicketReportDeliveryState.SENT, TicketReportDeliveryState.REJECTED),
            reportMessage = if (reportFailed) "보고 실패 · 서버 연결을 확인하고 다시 보고하세요."
                else if (run == null && reportDelivery == TicketReportDeliveryState.NONE) "수집 결과를 불러온 뒤 완료를 보고할 수 있습니다."
                else link?.rowId?.let(serverTicketViewModel::ticketMessage) ?: completion.message,
            reported = reportDelivery == TicketReportDeliveryState.SENT,
            reportPending = reportDelivery == TicketReportDeliveryState.PENDING,
            reportFailed = reportFailed,
            ticketReportTargetKey = link?.let { listOf(it.profileId, it.rowId, it.ticketRevision, it.runId).toString() },
            onReportTicketCompletion = {
                if (reportFailed) reportRunId?.let { serverTicketViewModel.retryReport(it, true) }
                else run?.let(serverTicketViewModel::reportCompletion)
            },
            onResult = { job?.context?.runId?.let { navController.navigateSingle(runResultRoute(it, targetDevice, job.context?.pipelineId)) } },
            onServerData = { navController.navigateSingle(Routes.SERVER_STORAGE) },
            onConnectionRemedy = { navController.navigateSingle(Routes.NETWORK_SETTINGS) },
            onFixedAction = { target, mutation -> uploadViewModel.mutateTransfer(target, mutation) { updated ->
                if (updated.id != target.jobId) {
                    uploadViewModel.openJob(updated)
                    navController.previousBackStackEntry?.savedStateHandle?.let { source ->
                        source["pushedTransferDevice"] = targetDevice
                        source["pushedTransferName"] = targetName
                        source["pushedTransferId"] = updated.id
                        source["pushedTransferJob"] = gson.toJson(updated)
                    }
                    navController.navigateSingle(Routes.UPLOAD_PROGRESS) {
                        popUpTo(Routes.UPLOAD_PROGRESS) { inclusive = true }
                    }
                }
            } },
            ongoingRun = { OngoingRunStatus(state.value, targetDevice, targetName) },
            onDismissMessage = uploadViewModel::dismissMessage
        )
    }
}

@Composable
private fun TransferOngoingRun(state: AppNavigationState) = OngoingRunStatus(state)

internal fun AppNavigationState.openTransferDetail(deviceId: String, job: UploadJob) {
    navController.currentBackStackEntry?.savedStateHandle?.let {
        it["pushedTransferDevice"] = deviceId
        it["pushedTransferName"] = deviceState.registeredDevices.firstOrNull { device -> device.deviceId.equals(deviceId, true) }
            ?.deviceName ?: selectedDeviceName
        it["pushedTransferId"] = job.id
        it["pushedTransferJob"] = Gson().toJson(job)
    }
    if (deviceId.equals(selectedDeviceId, true)) uploadViewModel.openJob(job)
    navController.navigateSingle(requireNotNull(transferSendDestination(TransferSendEvent.START_ACCEPTED)))
    navController.currentBackStackEntry?.savedStateHandle?.let {
        it["detailDevice"] = deviceId
        it["detailName"] = deviceState.registeredDevices.firstOrNull { device -> device.deviceId.equals(deviceId, true) }
            ?.deviceName ?: selectedDeviceName
        it["detailSeed"] = Gson().toJson(job)
        it["detailId"] = job.id
    }
}

@Composable
private fun TransferRootRoute(state: State<AppNavigationState>) {

    with(state.value) {
        LaunchedEffect(selectedDeviceId, fullControlConnected) {
            if (fullControlConnected) uploadViewModel.loadQueue()
        }
        val transfersLifecycleOwner = LocalLifecycleOwner.current
        val selectedServerCredentialRevision = directServerState.profiles.firstOrNull {
            it.profile.profileId == directServerState.selectedProfileId
        }?.credentialRevision
        LaunchedEffect(
            selectedDeviceId,
            directServerState.selectedProfileId,
            selectedServerCredentialRevision,
            transfersLifecycleOwner
        ) {
            val deviceId = selectedDeviceId?.takeIf(String::isNotBlank)
            val profileId = directServerState.selectedProfileId?.takeIf(String::isNotBlank)
            if (deviceId != null && profileId != null) {
                transfersLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        directServerViewModel.refreshDeviceEventStatus(profileId, deviceId)
                        delay(60_000L)
                    }
                }
            }
        }
        val deviceEventStatus = directServerState.deviceEventStatuses.firstOrNull { snapshot ->
            snapshot.profileId == directServerState.selectedProfileId &&
                snapshot.deviceId.equals(selectedDeviceId, ignoreCase = true) &&
                directServerState.profiles.firstOrNull {
                    it.profile.profileId == snapshot.profileId
                }?.credentialRevision == snapshot.credentialRevision
        }?.status
        val transfersState = com.example.jetsoncontroller.ui.transfers.buildTransfersHomeUiState(
            com.example.jetsoncontroller.ui.transfers.TransfersHomeInput(
                deviceName = selectedRegisteredDeviceName,
                transport = (transportState as? TransportState.Connected)
                    ?.takeIf { it.deviceId.equals(selectedDeviceId, ignoreCase = true) }
                    ?.type,
                upload = uploadState,
                runs = fieldState.runs,
                developerModeEnabled = developerModeEnabled,
                deviceEventStatus = deviceEventStatus
            )
        )
        var devicePicker by rememberSaveable(selectedDeviceId) { mutableStateOf(false) }
        if (devicePicker) GeoSheet("장치 선택", { devicePicker = false }, kind = GeoSheetKind.CHOICE) {
            deviceState.registeredDevices.forEach { device ->
                val current = device.deviceId.equals(selectedDeviceId, true)
                val snapshot = deviceSnapshots[device.deviceId.lowercase()]
                WorkDeviceChoiceRow(device.deviceName, current, {
                    devicePicker = false
                    if (!current) connectRegisteredDevice(device)
                }, word = if (current) {
                    if (deviceDashboardState.isOnline && deviceDashboardState.transportType ==
                        com.example.jetsoncontroller.data.transport.TransportType.BLE) "기본 연결" else selectedConnection.label
                } else "오프라인",
                    tone = if (current) selectedConnection.tone else Tone.UNKNOWN,
                    detail = if (current) null else snapshot?.lastSeenAtEpochMillis?.let {
                        "마지막 응답 ${com.example.jetsoncontroller.ui.devices.formatSeenAt(it, System.currentTimeMillis())}"
                    } ?: "마지막 응답 미확인")
            }
            GeoButton("장치 관리", { devicePicker = false; navigateToDeviceRoot(navController) }, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
        }
        val attention = uploadState.otherDeviceQueues.mapNotNull { (deviceId, jobs) ->
            val count = jobs.count { transferDetailActions(it).word in setOf("실패", "내용 불일치") }
            val device = deviceState.registeredDevices.firstOrNull { it.deviceId.equals(deviceId, true) }
            if (count == 0 || device == null) null else TransferDeviceAttention(deviceId, device.deviceName, count)
        }
        Box(Modifier.fillMaxSize()) {
        TransfersHomeScreen(
            state = transfersState,
            deviceId = selectedDeviceId,
            deviceName = selectedDeviceName,
            readiness = when {
                fullControlConnected && !uploadState.controlAvailable -> "$selectedDeviceName · 확인 중"
                serverUploadEnabled && uploadState.controlAvailable && dashboardState.capabilities.uploads ->
                    "$selectedDeviceName · 같은 네트워크 · 지금 보낼 수 있습니다."
                serverUploadEnabled && !dashboardState.capabilities.uploads -> "$selectedDeviceName · 장치 전송 지원 여부를 확인하세요."
                else -> "$selectedDeviceName · $serverUploadDisabledReason"
            },
            attention = attention,
            sendEnabled = serverUploadEnabled && uploadState.controlAvailable && dashboardState.capabilities.uploads,
            controlAvailable = fullControlConnected && uploadState.controlAvailable,
            onFixedCancel = { uploadViewModel.mutateTransfer(it, TransferMutation.CANCEL) },
            onDeviceConfirmationClosed = uploadViewModel::confirmationClosedForDeviceChange,
            onSelectDevice = { id -> deviceState.registeredDevices.firstOrNull { it.deviceId.equals(id, true) }
                ?.let(connectRegisteredDevice) },
            onDevicePicker = { devicePicker = true },
            connectionTone = workRootDeviceTone(),
            onAlerts = { navController.navigateSingle(Routes.ALERTS) },
            onRefresh = { uploadViewModel.refresh(); fieldToolsViewModel.refresh(retainLoaded = true) },
            refreshVisible = uploadState.error != null,
            onConnectionRemedy = { navController.navigateSingle(Routes.NETWORK_SETTINGS) },
            ongoingRun = { TransferOngoingRun(state.value) },
            onPickFiles = { navController.navigateSingle(requireNotNull(transferSendDestination(TransferSendEvent.PICK_FILES))) },
            onOpenServerFiles = { navController.navigateSingle(Routes.SERVER_STORAGE) { launchSingleTop = true } },
            onOpenTransfer = { jobId -> uploadJobById(jobId)?.let { job ->
                openTransferDetail(selectedDeviceId.orEmpty(), job)
            } },
            onCancelTransfer = { jobId -> uploadJobById(jobId)?.let { job ->
                selectedDeviceId?.let { uploadViewModel.mutateTransfer(transferActionTarget(it, job), TransferMutation.CANCEL) }
            } },
            onRetryTransfer = { jobId -> uploadJobById(jobId)?.let(uploadViewModel::retryJob) },
            onOpenRun = { runId -> fieldState.runs.firstOrNull {
                com.example.jetsoncontroller.ui.field.canonicalHistoryRunId(it) == runId && it.deviceId.equals(selectedDeviceId, true)
            }?.let { navController.navigateSingle(runResultRoute(runId, selectedDeviceId.orEmpty(), it.pipelineId, it.id)) } },
            onSendRun = { runId ->
                fieldState.runs.firstOrNull { com.example.jetsoncontroller.ui.field.canonicalHistoryRunId(it) == runId }
                    ?.takeIf { it.deviceId.equals(selectedDeviceId, true) }?.let(::storedRunSendRoute)?.let { navController.navigateSingle(it) }
            },
            onOpenHistory = { navController.navigateSingle(requireNotNull(transferSendDestination(TransferSendEvent.OPEN_LOG))) }
        )
        Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
            com.example.jetsoncontroller.ui.components.geo.GeoOperationMessageHost(uploadState.message, uploadViewModel::dismissMessage)
        }
        }
    }
        
}
