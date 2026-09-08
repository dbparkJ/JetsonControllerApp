package com.example.jetsoncontroller.ui.connection

/** Retains a separate workspace for each registered device for this app session. */
internal class DeviceWorkspace<T>(private val empty: () -> T) {
    private val states = mutableMapOf<String, T>()
    private var deviceId: String? = null

    fun select(selectedDeviceId: String?, current: T): T {
        val next = selectedDeviceId?.lowercase() ?: deviceId
        if (next == deviceId) return current
        deviceId?.let { states[it] = current }
        deviceId = next
        return states[next] ?: empty()
    }
}
