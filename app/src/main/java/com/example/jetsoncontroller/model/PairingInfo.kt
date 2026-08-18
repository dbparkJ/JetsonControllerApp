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

private fun deviceIdSuffix(
    deviceId: String,
    length: Int
): String =
    deviceId
        .replace("-", "")
        .takeLast(length)
        .uppercase(Locale.ROOT)
