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

    private fun refreshPermissionState() {
        bluetoothPermissionGranted = hasBluetoothPermissions()
        cameraPermissionGranted = hasCameraPermission()
        nearbyWifiPermissionGranted = hasNearbyWifiPermission()
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
                    bluetoothPermissionGranted =
                        bluetoothPermissionGranted,
                    cameraPermissionGranted =
                        cameraPermissionGranted,
                    nearbyWifiPermissionGranted =
                        nearbyWifiPermissionGranted,
                    onRequestCameraPermission = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA)
                        )
                    },
                    onRequestNearbyWifiPermission = {
                        permissionLauncher.launch(
                            nearbyWifiPermissions()
                        )
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
