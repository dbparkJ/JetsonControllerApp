package com.example.jetsoncontroller

import android.Manifest
import org.junit.Assert.*
import org.junit.Test

class WifiPermissionPolicyTest {
    @Test fun nearbyWifiUsesPreciseLocationBeforeAndroid13() {
        listOf(30, 31, 32).forEach { sdk ->
            assertEquals(Manifest.permission.ACCESS_FINE_LOCATION, WifiPermissionPolicy.nearbyPermission(sdk))
            assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                WifiPermissionPolicy.nearbyRequestPermissions(sdk).toList())
            assertTrue(WifiPermissionPolicy.hasNearbyPermissions(sdk) { it == Manifest.permission.ACCESS_FINE_LOCATION })
            assertFalse(WifiPermissionPolicy.hasNearbyPermissions(sdk) { it == Manifest.permission.NEARBY_WIFI_DEVICES })
            assertFalse(WifiPermissionPolicy.hasNearbyPermissions(sdk) { it == Manifest.permission.ACCESS_COARSE_LOCATION })
        }
    }

    @Test fun android13NearbyWifiDoesNotDependOnLocation() {
        assertEquals(listOf(Manifest.permission.NEARBY_WIFI_DEVICES), WifiPermissionPolicy.nearbyRequestPermissions(33).toList())
        assertTrue(WifiPermissionPolicy.hasNearbyPermissions(33) { it == Manifest.permission.NEARBY_WIFI_DEVICES })
        assertFalse(WifiPermissionPolicy.hasNearbyPermissions(33) { it == Manifest.permission.ACCESS_FINE_LOCATION })
    }

    @Test fun accessPointScansStillRequirePreciseLocationOnEverySupportedVersion() {
        assertEquals(Manifest.permission.ACCESS_FINE_LOCATION, WifiPermissionPolicy.scanPermission())
    }
}
