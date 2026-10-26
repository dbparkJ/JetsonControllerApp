package com.example.jetsoncontroller.data.monitoring

internal fun deviceMonitoringCanStart(
    foreground: Boolean,
    automaticConnectionRequested: Boolean,
    selectedDeviceId: String?,
    connectedDeviceId: String?
): Boolean = foreground && automaticConnectionRequested && !selectedDeviceId.isNullOrBlank() &&
    selectedDeviceId.equals(connectedDeviceId, ignoreCase = true)
