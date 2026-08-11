package com.example.jetsoncontroller.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.ui.dashboard.DashboardScreen
import com.example.jetsoncontroller.ui.dashboard.DashboardViewModel
import com.example.jetsoncontroller.ui.devices.DeviceListScreen
import com.example.jetsoncontroller.ui.devices.DeviceListViewModel

private object Routes {

    const val DEVICES =
        "devices"

    const val DASHBOARD =
        "dashboard"
}


@Composable
fun JetsonApp(
    repository:
        JetsonRepository,
    bluetoothPermissionGranted:
        Boolean
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

    val dashboardViewModel:
        DashboardViewModel =
        viewModel(
            factory =
                DashboardViewModel.Factory(
                    repository
                )
        )

    val deviceState by
        deviceViewModel
            .uiState
            .collectAsStateWithLifecycle()

    val dashboardState by
        dashboardViewModel
            .uiState
            .collectAsStateWithLifecycle()


    LaunchedEffect(
        bluetoothPermissionGranted
    ) {

        deviceViewModel
            .onPermissionResult(
                bluetoothPermissionGranted
            )
    }


    LaunchedEffect(
        deviceState.connectionState
    ) {

        if (
            deviceState
                .connectionState
                is ConnectionState.Ready
        ) {

            navController.navigate(
                Routes.DASHBOARD
            ) {

                launchSingleTop =
                    true
            }
        }
    }


    NavHost(
        navController =
            navController,
        startDestination =
            Routes.DEVICES
    ) {

        composable(
            Routes.DEVICES
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
                }
            )
        }


        composable(
            Routes.DASHBOARD
        ) {

            DashboardScreen(
                state =
                    dashboardState,

                onDisconnect = {

                    dashboardViewModel
                        .disconnect()

                    navController
                        .popBackStack(
                            Routes.DEVICES,
                            inclusive = false
                        )
                },

                onStartSystem = {
                    dashboardViewModel
                        .startSystem()
                },

                onStopSystem = {
                    dashboardViewModel
                        .stopSystem()
                },

                onRestartServices = {
                    dashboardViewModel
                        .restartServices()
                },

                onReboot = {
                    dashboardViewModel
                        .reboot()
                },

                onShutdown = {
                    dashboardViewModel
                        .shutdown()
                }
            )
        }
    }
}
