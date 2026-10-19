package com.example.jetsoncontroller.ui.navigation

import androidx.compose.runtime.State
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.jetsoncontroller.ui.*
import com.example.jetsoncontroller.ui.settings.AlertSettingsScreen

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
                        onTargets = { navController.navigateSingle(Routes.UPLOAD_SERVERS) },
                        onDiagnostics = { navController.navigateSingle(Routes.DIAGNOSTICS) },
                        onAlertSettings = { navController.navigateSingle(Routes.ALERT_SETTINGS) },
                        onServerStorage = { navController.navigateSingle(Routes.SERVER_STORAGE) },
                        onRefreshFan = dashboardViewModel::refreshFan, onFanAuto = dashboardViewModel::setFanAuto,
                        onFanManual = dashboardViewModel::setFanManual, onReboot = dashboardViewModel::reboot,
                        onShutdown = dashboardViewModel::shutdown, onDismissMessage = dashboardViewModel::clearOperationMessage,
                        onSection = onSectionSelected,
                        onDeveloper = { navController.navigateSingle("developer") },
                        onAdminTools = { navController.navigateSingle(Routes.ADMIN_TOOLS) },
                        developerModeEnabled = developerModeEnabled,
                        onDeveloperModeChange = developerModePreference::updateEnabled,
                        onTheme = { navController.navigateSingle(Routes.THEME) })
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
                        if (developerModeEnabled) navController.navigateSingle(Routes.DIAGNOSTICS)
                    },
                    developerModeEnabled = developerModeEnabled
                )
            }
        }

}
