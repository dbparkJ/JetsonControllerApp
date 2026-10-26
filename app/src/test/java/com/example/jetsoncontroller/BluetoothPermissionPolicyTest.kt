package com.example.jetsoncontroller

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPermissionPolicyTest {

    @Test
    fun android12AndNewerRequireBluetoothAndPreciseLocation() {
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
    fun android11RequestsLocationWithoutQueryingUnavailableBluetoothPermissions() {
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            BluetoothPermissionPolicy.requiredPermissions(30))
        assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            BluetoothPermissionPolicy.requestPermissions(30).toList())
        val queried = mutableListOf<String>()
        assertTrue(BluetoothPermissionPolicy.hasPermissions(30) { permission ->
            queried += permission
            permission == Manifest.permission.ACCESS_FINE_LOCATION
        })
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), queried)
    }

    @Test
    fun preciseLocationIsRequiredAndNearbyPermissionsAreRequiredOnlyFromAndroid12() {
        listOf(30, 31, 33).forEach { sdk ->
            val required = BluetoothPermissionPolicy.requiredPermissions(sdk).toSet()
            assertTrue(BluetoothPermissionPolicy.hasPermissions(sdk, required::contains))
            required.forEach { denied ->
                val granted = required - denied
                assertFalse("API $sdk must require $denied",
                    BluetoothPermissionPolicy.hasPermissions(sdk, granted::contains))
            }
            val coarseOnly = (required - Manifest.permission.ACCESS_FINE_LOCATION) + Manifest.permission.ACCESS_COARSE_LOCATION
            assertFalse(BluetoothPermissionPolicy.hasPermissions(sdk, coarseOnly::contains))
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

    @Test
    fun backgroundDiscoveryOnAndroid11NeedsASeparateLocationGrant() {
        val foreground = setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(BluetoothPermissionPolicy.hasPermissions(30, foreground::contains))
        assertFalse(BluetoothPermissionPolicy.hasBackgroundScanPermissions(30, foreground::contains))
        val alwaysAllowed = foreground + Manifest.permission.ACCESS_BACKGROUND_LOCATION
        assertTrue(BluetoothPermissionPolicy.hasBackgroundScanPermissions(30, alwaysAllowed::contains))
        assertFalse(BluetoothPermissionPolicy.hasBackgroundScanPermissions(30) {
            it == Manifest.permission.ACCESS_BACKGROUND_LOCATION
        })
        listOf(31, 33).forEach { sdk ->
            val granted = BluetoothPermissionPolicy.requiredPermissions(sdk).toSet()
            assertTrue(BluetoothPermissionPolicy.hasBackgroundScanPermissions(sdk, granted::contains))
        }
    }
}
