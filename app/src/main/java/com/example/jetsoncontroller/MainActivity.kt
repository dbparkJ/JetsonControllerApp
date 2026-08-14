package com.example.jetsoncontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.jetsoncontroller.ui.JetsonApp
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme

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


    private fun hasBluetoothPermissions():
        Boolean {

        val scan =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission
                        .BLUETOOTH_SCAN
                ) ==
                PackageManager
                    .PERMISSION_GRANTED

        val connect =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission
                        .BLUETOOTH_CONNECT
                ) ==
                PackageManager
                    .PERMISSION_GRANTED

        return scan &&
            connect
    }

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
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
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

        super.onCreate(
            savedInstanceState
        )

        refreshPermissionState()

        val app =
            application
                as JetsonApplication

        setContent {

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    contract =
                        ActivityResultContracts
                            .RequestMultiplePermissions()
                ) {
                    refreshPermissionState()
                }

            LaunchedEffect(Unit) {
                if (!hasBluetoothPermissions()) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    )
                }
            }


            JetsonControllerTheme {

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
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
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
        refreshPermissionState()
    }
}
