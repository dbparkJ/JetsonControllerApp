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

internal object WifiDirectPermissionPolicy {

    fun requestPermissions(sdkInt: Int): Array<String> =
        when {
            sdkInt >= 37 ->
                arrayOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_LOCAL_NETWORK
                )
            sdkInt >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            else ->
                BluetoothPermissionPolicy.locationRequestPermissions()
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
        return WifiDirectPermissionPolicy.requestPermissions(
            Build.VERSION.SDK_INT
        )
    }

    private fun connectivityPermissions(): Array<String> {
        val permissions = bluetoothPermissions().toMutableList()
        permissions += WifiDirectPermissionPolicy.requestPermissions(
            Build.VERSION.SDK_INT
        )
        return permissions.distinct().toTypedArray()
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
                val missingConnectivityPermissions = connectivityPermissions().filter {
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        it
                    ) != PackageManager.PERMISSION_GRANTED
                }
                if (missingConnectivityPermissions.isNotEmpty()) {
                    permissionLauncher.launch(missingConnectivityPermissions.toTypedArray())
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
        refreshPermissionState()
    }
}
