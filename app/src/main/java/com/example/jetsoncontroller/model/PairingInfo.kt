package com.example.jetsoncontroller.model

import java.util.Locale

data class PairingInfo(
    val version: Int,
    val deviceId: String,
    val bootstrapSecretHex: String
) {

    val shortId: String
        get() = deviceIdSuffix(deviceId, length = 4)

    val legacyShortId: String
        get() = deviceIdSuffix(deviceId, length = 5)

    val expectedBleName: String
        get() = canonicalBleNameForDeviceId(deviceId)

    val legacyExpectedBleName: String
        get() = legacyBleNameForDeviceId(deviceId)
}

fun canonicalBleNameForDeviceId(deviceId: String): String =
    "MMS-${deviceIdSuffix(deviceId, length = 4)}"

fun legacyBleNameForDeviceId(deviceId: String): String =
    "MMS-${deviceIdSuffix(deviceId, length = 5)}"

/**
 * The name shown for a registered device: the owner's own name for it (set on the
 * Jetson with `install.sh --device-name`), or the canonical MMS name when the
 * device reports none or an MMS-style one.
 */
fun registeredDeviceDisplayName(
    deviceId: String,
    reportedName: String?
): String {
    val name = reportedName?.trim().orEmpty()
    return if (name.isEmpty() || name.startsWith("MMS", ignoreCase = true)) {
        canonicalBleNameForDeviceId(deviceId)
    } else {
        name.take(MAX_DEVICE_DISPLAY_NAME_LENGTH)
    }
}

private const val MAX_DEVICE_DISPLAY_NAME_LENGTH = 64

private fun deviceIdSuffix(
    deviceId: String,
    length: Int
): String =
    deviceId
        .replace("-", "")
        .takeLast(length)
        .uppercase(Locale.ROOT)
