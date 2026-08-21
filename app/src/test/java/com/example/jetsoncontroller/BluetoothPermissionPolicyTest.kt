package com.example.jetsoncontroller

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothPermissionPolicyTest {

    @Test
    fun allSupportedAndroidVersionsRequireBluetoothAndPreciseLocation() {
        val supportedSdkVersions =
            listOf(
                Build.VERSION_CODES.S,
                Build.VERSION_CODES.S_V2,
                Build.VERSION_CODES.TIRAMISU,
                36,
                37
            )

        supportedSdkVersions.forEach { sdkInt ->
            assertEquals(
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                BluetoothPermissionPolicy.requiredPermissions(sdkInt)
            )

            assertEquals(
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                BluetoothPermissionPolicy
                    .requestPermissions(sdkInt)
                    .toList()
            )
        }
    }

    @Test
    fun locationRequestsAlwaysIncludeCoarseAndFineTogether() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            BluetoothPermissionPolicy
                .locationRequestPermissions()
                .toList()
        )
    }
}
