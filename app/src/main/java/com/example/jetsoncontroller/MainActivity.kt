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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsoncontroller.ui.JetsonApp
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import com.example.jetsoncontroller.ui.startup.AppRestoreState
import com.example.jetsoncontroller.ui.startup.AppRestoreViewModel
import com.example.jetsoncontroller.ui.startup.StartupContent
import com.example.jetsoncontroller.ui.startup.StartupTiming

internal object BluetoothPermissionPolicy {

    fun locationRequestPermissions(): Array<String> =
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

    fun requiredPermissions(
        sdkInt: Int
    ): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun requestPermissions(
        sdkInt: Int
    ): Array<String> {
        val locationPermissions =
            locationRequestPermissions().toList()

        return if (sdkInt >= Build.VERSION_CODES.S) {
            (
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) + locationPermissions
            ).toTypedArray()
        } else {
            locationPermissions.toTypedArray()
        }
    }
}

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
        BluetoothPermissionPolicy
            .requiredPermissions(Build.VERSION.SDK_INT)
            .all { permission ->
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun nearbyWifiPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            BluetoothPermissionPolicy.locationRequestPermissions()
        }
    }

    private fun hasWifiScanPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
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
        val timing = StartupTiming(SystemClock.uptimeMillis(), splashVisible = true)
        setTheme(R.style.Theme_JetsonController)
        super.onCreate(
            savedInstanceState
        )

        refreshPermissionState()

        val app =
            application
                as JetsonApplication

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
        splashScreen.setOnExitAnimationListener { splash ->
            timing.splashRemoved(restoreViewModel.state.value, SystemClock.uptimeMillis())
            splash.remove()
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
    }

    override fun onStop() {
        ConnectionDiagnostics.record("app_background", mapOf("foreground" to false))
        ConnectionDiagnostics.recordPowerState(this)
        super.onStop()
    }
}
