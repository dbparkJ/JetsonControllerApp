package com.example.jetsoncontroller.ui.navigation

import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.settings.AlertSettingsScreen

internal const val SETTINGS_SERVER_ROUTE = "settings/server"
private const val SETTINGS_ABOUT_ROUTE = "settings/about"
private const val SETTINGS_HELP_ROUTE = "settings/help"

internal fun NavGraphBuilder.settingsGraph(state: State<AppNavigationState>, includeRoot: Boolean = false) {
    if (includeRoot) {
        composable(Routes.SETTINGS) {
            with(state.value) {
                StatusPollingLifecycleEffect(dashboardViewModel)
                // Keyed per entry, not through a shared SaveableStateHolder (see Routes.DATA).
                androidx.compose.runtime.key(selectedDeviceId) {
                    com.example.jetsoncontroller.ui.settings.SettingsHubScreen(
                        deviceDashboardState, selectedDeviceId, alertCenterState.unreadCount,
                        onDevices = { navigateToDeviceRoot(navController) },
                        onAlerts = { navController.navigateSingle(Routes.ALERTS) },
                        onNetwork = { navController.navigateSingle(Routes.NETWORK_SETTINGS) },
                        onSensors = { navController.navigateSingle(Routes.SENSORS) },
                        onTargets = { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.UPLOAD_TARGETS.route) },
                        onDiagnostics = { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.DIAGNOSTICS.route) },
                        onAlertSettings = { navController.navigateSingle(Routes.ALERT_SETTINGS) },
                        onServerStorage = { navController.navigateSingle(Routes.SERVER_STORAGE) },
                        onRefreshFan = dashboardViewModel::refreshFan, onFanAuto = dashboardViewModel::setFanAuto,
                        onFanManual = dashboardViewModel::setFanManual, onReboot = dashboardViewModel::reboot,
                        onShutdown = dashboardViewModel::shutdown, onDismissMessage = dashboardViewModel::clearOperationMessage,
                        onSection = onSectionSelected,
                        onDeveloper = { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.HUB.route) },
                        onAdminTools = { navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.HUB.route) },
                        developerModeEnabled = developerModeEnabled,
                        onDeveloperModeChange = developerModePreference::updateEnabled,
                        onTheme = { navController.navigateSingle(Routes.THEME) },
                        onServerConnection = { navController.navigateSingle(SETTINGS_SERVER_ROUTE) },
                        onAbout = { navController.navigateSingle(SETTINGS_ABOUT_ROUTE) },
                        onHelp = { navController.navigateSingle(SETTINGS_HELP_ROUTE) },
                        serverProfileName = directServerState.profiles.firstOrNull {
                            it.profile.profileId == directServerState.selectedProfileId
                        }?.profile?.displayName,
                        alertSettings = alertSettings,
                        notificationPermissionGranted = notificationPermissionGranted,
                        onRequestNotificationPermission = onRequestNotificationPermission
                    )
                }
            }
        }
    }
        composable(Routes.THEME) {
            com.example.jetsoncontroller.ui.settings.ThemeScreen(onBack = { state.value.navController.popBackStack() })
        }
        composable(Routes.ALERT_SETTINGS) {
            with(state.value) {
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
                    onOpenDiagnostics = {
                        if (developerModeEnabled) navController.navigateSingle(com.example.jetsoncontroller.ui.dev.DevDestination.DIAGNOSTICS.route)
                    },
                    developerModeEnabled = developerModeEnabled,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(SETTINGS_ABOUT_ROUTE) {
            with(state.value) {
                com.example.jetsoncontroller.ui.settings.SettingsAboutScreen(
                    onBack = { navController.popBackStack() },
                    developerModeEnabled = developerModeEnabled,
                    onDeveloperUnlocked = {
                        developerModePreference.updateEnabled(true)
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(SETTINGS_HELP_ROUTE) {
            com.example.jetsoncontroller.ui.settings.SettingsHelpScreen(
                onBack = { state.value.navController.popBackStack() }
            )
        }

        composable(SETTINGS_SERVER_ROUTE) {
            val current = state.value
            val viewModel: com.example.jetsoncontroller.ui.settings.ServerConnectionViewModel =
                viewModel(
                    factory = com.example.jetsoncontroller.ui.settings.ServerConnectionViewModel.Factory(
                        current.fieldContext
                    )
                )
            val connectionState by viewModel.uiState.collectAsState()
            val profile = current.directServerState.profiles.firstOrNull {
                it.profile.profileId == current.directServerState.selectedProfileId
            }
            com.example.jetsoncontroller.ui.settings.ServerConnectionScreen(
                profile = profile,
                state = connectionState,
                onBack = { current.navController.popBackStack() },
                onCheck = viewModel::check,
                onRelogin = { selectedProfile, token ->
                    current.directServerViewModel.selectProfile(selectedProfile.profileId)
                    viewModel.relogin(selectedProfile, token)
                }
            )
        }

}
