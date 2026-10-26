package com.example.jetsoncontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.jetsoncontroller.data.monitoring.DeviceMonitoringService
import com.example.jetsoncontroller.data.monitoring.deviceMonitoringCanStart
import com.example.jetsoncontroller.data.transport.TransportState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsoncontroller.ui.JetsonApp
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import com.example.jetsoncontroller.ui.startup.AppRestoreState
import com.example.jetsoncontroller.ui.startup.AppRestoreViewModel
import com.example.jetsoncontroller.ui.startup.StartupContent
import com.example.jetsoncontroller.ui.startup.StartupTiming

class MainActivity :
    ComponentActivity() {

    private var bluetoothPermissionGranted
        by mutableStateOf(false)

    private var cameraPermissionGranted
        by mutableStateOf(false)

    private var nearbyWifiPermissionGranted
        by mutableStateOf(false)

    private var wifiScanPermissionGranted
        by mutableStateOf(false)

    private var localNetworkPermissionGranted
        by mutableStateOf(false)

    private var notificationPermissionGranted
        by mutableStateOf(false)


    private fun hasBluetoothPermissions(): Boolean =
        BluetoothPermissionPolicy.hasPermissions(Build.VERSION.SDK_INT) { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun bluetoothPermissions(): Array<String> =
        BluetoothPermissionPolicy.requestPermissions(
            Build.VERSION.SDK_INT
        )

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNearbyWifiPermission(): Boolean {
        return WifiPermissionPolicy.hasNearbyPermissions(Build.VERSION.SDK_INT) { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun nearbyWifiPermissions(): Array<String> {
        return WifiPermissionPolicy.nearbyRequestPermissions(Build.VERSION.SDK_INT)
    }

    private fun hasWifiScanPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            WifiPermissionPolicy.scanPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocalNetworkPermission(): Boolean {
        return Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshPermissionState() {
        bluetoothPermissionGranted = hasBluetoothPermissions()
        cameraPermissionGranted = hasCameraPermission()
        nearbyWifiPermissionGranted = hasNearbyWifiPermission()
        wifiScanPermissionGranted = hasWifiScanPermission()
        localNetworkPermissionGranted = hasLocalNetworkPermission()
        notificationPermissionGranted = hasNotificationPermission()
    }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        val timing = StartupTiming(SystemClock.uptimeMillis(),
            splashVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        setTheme(R.style.Theme_JetsonController)
        super.onCreate(
            savedInstanceState
        )

        refreshPermissionState()

        val app =
            application
                as JetsonApplication

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(app.repository.selectedDeviceId, app.repository.transportState,
                    app.repository.automaticConnectionRequested) { selected, transport, requested ->
                    deviceMonitoringCanStart(true, requested, selected,
                        (transport as? TransportState.Connected)?.deviceId)
                }.distinctUntilChanged().collect { shouldStart ->
                    if (shouldStart && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        try {
                            DeviceMonitoringService.start(applicationContext)
                        } catch (error: IllegalStateException) {
                            ConnectionDiagnostics.record("monitor_service_start_failed",
                                mapOf("reasonCode" to error.javaClass.simpleName))
                        } catch (error: SecurityException) {
                            ConnectionDiagnostics.record("monitor_service_start_failed",
                                mapOf("reasonCode" to error.javaClass.simpleName))
                        }
                    }
                }
            }
        }

        val restoreViewModel = ViewModelProvider(this, AppRestoreViewModel.Factory(app.credentialStore))[
            AppRestoreViewModel::class.java
        ]
        WindowCompat.setDecorFitsSystemWindows(window, false)
        var composedState: AppRestoreState = AppRestoreState.Loading
        val root = window.decorView
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = timing.canDraw(composedState, SystemClock.uptimeMillis())
                if (ready) root.viewTreeObserver.removeOnPreDrawListener(this)
                else root.postInvalidateOnAnimation()
                return ready
            }
        })
        // The pre-draw gate releases either a resolved scene or the loader after 300 ms.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash ->
                timing.splashRemoved(restoreViewModel.state.value, SystemClock.uptimeMillis())
                splash.remove()
            }
        }

        setContent {

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    contract =
                        ActivityResultContracts
                            .RequestMultiplePermissions()
                ) {
                    refreshPermissionState()
                }

            val restoreState by restoreViewModel.state.collectAsStateWithLifecycle()
            SideEffect { composedState = restoreState }
            val palette = com.example.jetsoncontroller.ui.theme.rememberAppearancePalette()
            SideEffect {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.colors.surface.toArgb()))
            }
            StartupContent(restoreState, timing, restoreViewModel::retry, palette) {
                JetsonApp(
                    repository =
                        app.repository,
                    alertPreferences =
                        app.alertPreferences,
                    alertHistory =
                        app.alertHistory,
                    bluetoothPermissionGranted =
                        bluetoothPermissionGranted,
                    cameraPermissionGranted =
                        cameraPermissionGranted,
                    nearbyWifiPermissionGranted =
                        nearbyWifiPermissionGranted,
                    wifiScanPermissionGranted =
                        wifiScanPermissionGranted,
                    localNetworkPermissionGranted =
                        localNetworkPermissionGranted,
                    notificationPermissionGranted =
                        notificationPermissionGranted,
                    onRequestBluetoothPermission = {
                        permissionLauncher.launch(
                            bluetoothPermissions()
                        )
                    },
                    onRequestCameraPermission = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA)
                        )
                    },
                    onRequestNearbyWifiPermission = {
                        permissionLauncher.launch(
                            nearbyWifiPermissions()
                        )
                    },
                    onRequestWifiScanPermission = {
                        permissionLauncher.launch(
                            BluetoothPermissionPolicy
                                .locationRequestPermissions()
                        )
                    },
                    onRequestLocalNetworkPermission = {
                        if (Build.VERSION.SDK_INT >= 37) {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
                            )
                        }
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ConnectionDiagnostics.record("app_foreground", mapOf("foreground" to true))
        ConnectionDiagnostics.recordPowerState(this)
        refreshPermissionState()
        (application as JetsonApplication).repository.configureAutomaticConnectivity(
            enabled = true,
            localNetworkPermissionGranted = localNetworkPermissionGranted,
            nearbyWifiPermissionGranted = nearbyWifiPermissionGranted,
            bluetoothPermissionGranted = bluetoothPermissionGranted
        )
    }

    override fun onStop() {
        ConnectionDiagnostics.record("app_background", mapOf("foreground" to false))
        ConnectionDiagnostics.recordPowerState(this)
        super.onStop()
    }
}
