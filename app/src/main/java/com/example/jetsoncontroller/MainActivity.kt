package com.example.jetsoncontroller

import android.Manifest
import android.content.pm.PackageManager
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

    private var permissionGranted
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


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        permissionGranted =
            hasBluetoothPermissions()

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

                    permissionGranted =
                        hasBluetoothPermissions()
                }


            LaunchedEffect(Unit) {

                if (
                    !hasBluetoothPermissions()
                ) {

                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission
                                .BLUETOOTH_SCAN,

                            Manifest.permission
                                .BLUETOOTH_CONNECT
                        )
                    )
                }
            }


            JetsonControllerTheme {

                JetsonApp(
                    repository =
                        app.repository,
                    bluetoothPermissionGranted =
                        permissionGranted
                )
            }
        }
    }
}
