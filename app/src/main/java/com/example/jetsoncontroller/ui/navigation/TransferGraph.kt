package com.example.jetsoncontroller.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.storage.DeviceStorageScreen
import com.example.jetsoncontroller.ui.upload.UploadConfirmScreen
import com.example.jetsoncontroller.ui.upload.UploadProgressScreen
import com.example.jetsoncontroller.ui.upload.UploadQueueScreen
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
                    onDeviceData = {
                        navController.navigateSingle(Routes.DATA) { launchSingleTop = true }
                    },
                    onJetsonServerProxy = if (developerModeEnabled && serverStorageState.controlAvailable) {
                        { navController.navigateSingle(Routes.SERVER_PROXY_STORAGE) { launchSingleTop = true } }
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
                        navController.navigateSingle(Routes.UPLOAD_SERVERS)
                    },
                    onConfirm = { targetId ->
                        uploadViewModel.startUpload(rootId, path, targetId)
                        navController.navigateSingle(Routes.UPLOAD_PROGRESS) {
                            popUpTo(Routes.STORAGE_ROUTE) { inclusive = false }
                        }
                    },
                    developerModeEnabled = developerModeEnabled
                )
            }
        }


        composable(Routes.UPLOAD_PROGRESS) {
            with(state.value) {
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
                    deviceDeletionEnabled = fullControlConnected,
                    developerModeEnabled = developerModeEnabled,
                    targetLabel = uploadState.currentJob?.targetId?.let { targetId ->
                        uploadState.targets.firstOrNull { it.id == targetId }?.label
                    },
                    onServerData = {
                        navController.navigateSingle(Routes.SERVER_STORAGE) { launchSingleTop = true }
                    },
                    onHome = { resetToDeviceRoot(navController) },
                    onFiles = { navController.navigateSingle(Routes.DATA) { launchSingleTop = true } },
                    onDismissMessage = uploadViewModel::dismissMessage
                )
            }
        }

    (listOf(Routes.TRANSFER_BROWSE) + if (includeRoot) listOf(Routes.TRANSFERS) else emptyList()).forEach { rootRoute ->
        composable(rootRoute) {
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
                androidx.compose.material3.Scaffold(
                    containerColor = com.example.jetsoncontroller.ui.theme.LocalGeoColors.current.canvas,
                    bottomBar = { ControlNavigationBar(ControlSection.TRANSFERS, onSectionSelected) }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        com.example.jetsoncontroller.ui.transfers.TransfersHomeScreen(
                            state = transfersState,
                            onPickFiles = { navController.navigateSingle(Routes.DATA) { launchSingleTop = true } },
                            onOpenServerFiles = {
                                navController.navigateSingle(Routes.SERVER_STORAGE) { launchSingleTop = true }
                            },
                            onOpenTransfer = { jobId ->
                                uploadJobById(jobId)?.let { job ->
                                    uploadViewModel.openJob(job)
                                    navController.navigateSingle(Routes.UPLOAD_PROGRESS) { launchSingleTop = true }
                                }
                            },
                            onCancelTransfer = { jobId ->
                                uploadJobById(jobId)?.let { job ->
                                    uploadViewModel.openJob(job)
                                    uploadViewModel.cancelCurrentUpload()
                                }
                            },
                            onRetryTransfer = { jobId -> uploadJobById(jobId)?.let(uploadViewModel::retryJob) },
                            onSendRun = { runId ->
                                fieldState.runs.firstOrNull {
                                    com.example.jetsoncontroller.ui.field.canonicalHistoryRunId(it) == runId
                                }?.output?.takeIf { it.rootId.isNotBlank() && it.path.isNotBlank() }?.let { output ->
                                    navController.navigateSingle(
                                        "upload_confirm/${Uri.encode(output.rootId)}" +
                                            "?path=${Uri.encode(output.path)}&runId=${Uri.encode(runId)}"
                                    )
                                }
                            },
                            onOpenHistory = { navController.navigateSingle(Routes.UPLOAD_QUEUE) { launchSingleTop = true } }
                        )
                    }
                }
            }
        }
    }
        composable(Routes.UPLOAD_QUEUE) {
            with(state.value) {
                UploadQueueScreen(
                    queue = uploadState.queue,
                    targets = uploadState.targets,
                    isLoading = uploadState.isLoading,
                    error = uploadState.error,
                    message = uploadState.message,
                    onRefresh = uploadViewModel::loadQueue,
                    onManageTargets = {
                        navController.navigateSingle(Routes.UPLOAD_SERVERS)
                    },
                    onJobClick = { job ->
                        uploadViewModel.openJob(job)
                        navController.navigateSingle(Routes.UPLOAD_PROGRESS)
                    },
                    onDeleteJob = uploadViewModel::deleteJobFromQueue,
                    mutationEnabled = fullControlConnected,
                    deviceId = uploadState.deviceId,
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
                // Per-device UI state lives in this entry. A SaveableStateHolder key shared across
                // back-stack entries crashed when two entries were composed during a transition.
                androidx.compose.runtime.key(selectedDeviceId) {
                    com.example.jetsoncontroller.ui.storage.DataHubScreen(
                        state = dataStorageState,
                        serverUploadEnabled = serverUploadEnabled && dashboardState.capabilities.uploads,
                        unavailableReason = if (!serverUploadEnabled) serverUploadDisabledReason
                            else "장치 업로드 지원 여부 미확인",
                        unreadCount = alertCenterState.unreadCount,
                        onDevices = { navigateToDeviceRoot(navController) },
                        onAlerts = { navController.navigateSingle(Routes.ALERTS) },
                        onRefresh = dataStorageViewModel::refresh,
                        onNavigateBack = { dataStorageViewModel.navigateBack() },
                        onDirectoryClick = dataStorageViewModel::selectDirectory,
                        onFileClick = dataStorageViewModel::openFile,
                        onDeleteClick = dataStorageViewModel::deleteEntry,
                        onHistory = { navController.navigateSingle(Routes.UPLOAD_QUEUE) },
                        onTransfer = { root, path -> navController.navigateSingle("upload_confirm/${Uri.encode(root)}?path=${Uri.encode(path)}") },
                        onSection = onSectionSelected,
                        onServerData = { navController.navigateSingle(Routes.SERVER_STORAGE) },
                        onTrash = { navController.navigateSingle(Routes.LOCAL_TRASH) },
                        onDismissMessage = dataStorageViewModel::dismissMessage,
                        onUndoDelete = dataStorageViewModel::undoDelete
                    )
                }
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
                    onDismissMessage = localTrashViewModel::dismissMessage
                )
            }
        }

}
