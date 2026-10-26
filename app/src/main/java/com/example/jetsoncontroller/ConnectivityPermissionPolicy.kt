package com.example.jetsoncontroller

import android.Manifest
import android.os.Build

/** Runtime permission decisions only; legacy BLUETOOTH/ADMIN are install-time permissions. */
internal object BluetoothPermissionPolicy {
    fun locationRequestPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Keep precise location on S+ as well: BLE advertisements are not marked neverForLocation.
    fun requiredPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun requestPermissions(sdkInt: Int): Array<String> =
        (requiredPermissions(sdkInt).filterNot { it == Manifest.permission.ACCESS_FINE_LOCATION } +
            locationRequestPermissions()).toTypedArray()

    fun hasPermissions(sdkInt: Int, granted: (String) -> Boolean): Boolean =
        requiredPermissions(sdkInt).all(granted)

    // Android 11 grants background location separately; never include it in the initial request.
    fun hasBackgroundScanPermissions(sdkInt: Int, granted: (String) -> Boolean): Boolean =
        hasPermissions(sdkInt, granted) &&
            (sdkInt >= Build.VERSION_CODES.S || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
}

internal object WifiPermissionPolicy {
    fun nearbyPermission(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES
        else Manifest.permission.ACCESS_FINE_LOCATION

    fun nearbyRequestPermissions(sdkInt: Int): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else BluetoothPermissionPolicy.locationRequestPermissions()

    fun hasNearbyPermissions(sdkInt: Int, granted: (String) -> Boolean): Boolean =
        granted(nearbyPermission(sdkInt))

    // WifiManager.startScan/getScanResults require fine location even on Android 13+.
    fun scanPermission(): String = Manifest.permission.ACCESS_FINE_LOCATION
}
