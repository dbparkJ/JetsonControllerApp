package com.example.jetsoncontroller.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.diagnostics.ConnectionDiagnosticsScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineConfigScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineEditorScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineLogScreen
import com.example.jetsoncontroller.ui.pipelines.PipelinePickerScreen
import com.example.jetsoncontroller.ui.storage.ServerStorageScreen
import com.example.jetsoncontroller.ui.upload.UploadTargetSettingsScreen

internal fun NavGraphBuilder.devGraph(state: State<AppNavigationState>) {
        composable(Routes.SERVER_PROXY_STORAGE) serverProxy@ {
            with(state.value) {
                if (!developerModeEnabled) return@serverProxy
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
                    onDeviceDataClick = { navController.navigateSingle(Routes.DATA) { launchSingleTop = true } },
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
        }

        composable(Routes.UPLOAD_SERVERS) uploadServers@ {
            with(state.value) {
                if (!developerModeEnabled) return@uploadServers
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
        }

        composable(Routes.PIPELINE_EDITOR) pipelineEditor@ {
            with(state.value) {
                if (!developerModeEnabled) return@pipelineEditor
                PipelineEditorScreen(
                    state = pipelineState,
                    onBack = { navController.popBackStack() },
                    onPick = { target ->
                        pipelineViewModel.beginPick(target)
                        navController.navigateSingle(Routes.PIPELINE_PICKER)
                    },
                    onLabelChange = pipelineViewModel::setLabel,
                    onAutostartChange = pipelineViewModel::setAutostart,
                    onRegister = pipelineViewModel::register
                )
            }
        }

        composable(Routes.PIPELINE_PICKER) pipelinePicker@ {
            with(state.value) {
                if (!developerModeEnabled) return@pipelinePicker
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

        composable(
            route = Routes.PIPELINE_LOGS,
            arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })
        ) pipelineLogs@ { backStackEntry ->
            with(state.value) {
                if (!developerModeEnabled) return@pipelineLogs
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
        }

        composable(
            route = Routes.PIPELINE_CONFIG,
            arguments = listOf(navArgument("pipelineId") { type = NavType.StringType })
        ) pipelineConfig@ { backStackEntry ->
            with(state.value) {
                if (!developerModeEnabled) return@pipelineConfig
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
        }

        composable(Routes.DIAGNOSTICS) diagnostics@ {
            with(state.value) {
                if (!developerModeEnabled) return@diagnostics
                ConnectionDiagnosticsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.ADMIN_TOOLS) adminTools@ {
            with(state.value) {
                if (!developerModeEnabled) return@adminTools
                StatusPollingLifecycleEffect(dashboardViewModel)
                com.example.jetsoncontroller.ui.settings.AdminToolsScreen(
                    state = deviceDashboardState,
                    deviceId = selectedDeviceId,
                    onBack = { navController.popBackStack() },
                    onNetwork = { navController.navigateSingle(Routes.NETWORK_SETTINGS) },
                    onTargets = { navController.navigateSingle(Routes.UPLOAD_SERVERS) },
                    onDiagnostics = { navController.navigateSingle(Routes.DIAGNOSTICS) },
                    onJetsonServerProxy = { navController.navigateSingle(Routes.SERVER_PROXY_STORAGE) },
                    onDeviceRegistration = { navigateToDeviceRoot(navController) },
                    onRefreshFan = dashboardViewModel::refreshFan,
                    onFanAuto = dashboardViewModel::setFanAuto,
                    onFanManual = dashboardViewModel::setFanManual,
                    onReboot = dashboardViewModel::reboot,
                    onShutdown = dashboardViewModel::shutdown,
                    onDismissMessage = dashboardViewModel::clearOperationMessage
                )
            }
        }

        composable("developer") developerTools@ {
            with(state.value) {
                if (!developerModeEnabled) return@developerTools
                com.example.jetsoncontroller.ui.field.DeveloperScreen(fieldState,
                    onBack = { navController.popBackStack() }, onExecute = fieldToolsViewModel::execute,
                    onLogs = { navController.navigateSingle(Routes.PIPELINES) },
                    onDiagnostics = { navController.navigateSingle(Routes.DIAGNOSTICS) },
                    developerModeEnabled = developerModeEnabled,
                    onDeveloperModeChange = developerModePreference::updateEnabled,
                    onDeveloperDisabled = {
                        navigateToSettingsRoot(navController)
                    },
                    onAdminTools = { navController.navigateSingle(Routes.ADMIN_TOOLS) },
                    onRefreshNetwork = fieldToolsViewModel::refreshNetwork,
                    onTestSensor = fieldToolsViewModel::testSensor,
                    onCameraPreview = { navController.navigateSingle(Routes.CAMERA_PREVIEW) },
                    onGpsMap = { navController.navigateSingle(Routes.GNSS_MAP) })
            }
        }

}
