package com.example.jetsoncontroller.data.alerts

import java.util.Locale

/** Shown when the device's own name is not known. An alert never shows a UUID instead. */
internal const val UNNAMED_DEVICE_LABEL = "장치"

private val UUID_PATTERN = Regex(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
)

/** A name a person can read. A blank name or a UUID falls back to [UNNAMED_DEVICE_LABEL]. */
internal fun readableDeviceName(raw: String?): String {
    val name = raw?.trim().orEmpty()
    return if (name.isBlank() || UUID_PATTERN.matches(name)) UNNAMED_DEVICE_LABEL else name
}

/**
 * Alert title that names the device first: "MMS-4DE0 온도 높음". Every alert uses this shape
 * so that a notification read on its own still says which device it is about.
 */
internal fun alertTitle(deviceName: String?, condition: String): String =
    "${readableDeviceName(deviceName)} $condition"

/** Temperature with one decimal and the degree sign, for example "81.5°C". */
internal fun formatCelsius(celsius: Float): String =
    String.format(Locale.US, "%.1f°C", celsius)
