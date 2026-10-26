package com.example.jetsoncontroller.data.bluetooth

/** Policy for establishing a link only; ATT, identity and authentication failures never enter it. */
internal object BleConnectionPolicy {
    const val MAX_ATTEMPTS = 3
    const val CONNECTION_BUDGET_MILLIS = 20_000L

    // stopScan has no completion callback. The reported Android 11 Samsung failure
    // warrants a short radio handoff window; 500 ms is a mitigation to validate on
    // that tablet, not an Android timing guarantee. Android 12+ keeps its fast path.
    fun initialDelayMillis(sdkInt: Int): Long = if (sdkInt <= 30) 500L else 0L

    fun retryDelayMillis(status: Int, attempt: Int, establishingLink: Boolean): Long? =
        if (establishingLink && attempt in 1 until MAX_ATTEMPTS && status in transientStatuses) {
            500L
        } else {
            null
        }

    // AOSP GATT_ERROR=0x85; HCI connection timeout=0x08 and failed establishment=0x3e:
    // https://android.googlesource.com/platform/system/bt/+/3c196348ee9922fde76502b107656b07f04b31f3/stack/include/gatt_api.h
    // https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/system/stack/include/hci_error_code.h
    // 147 is GATT_CONNECTION_TIMEOUT (API 35); use its value for older compile SDKs:
    // https://developer.android.com/reference/android/bluetooth/BluetoothGatt#GATT_CONNECTION_TIMEOUT
    // 8 is retryable ONLY in onConnectionStateChange before STATE_CONNECTED; an
    // ATT callback with status 8 means insufficient authorization and must fail.
    // Bounded retries are also used by Nordic's BLE library. Our 500 ms close-to-
    // reconnect interval and 20 s overall budget trade stack recovery against latency:
    // https://github.com/nordicsemi/Android-BLE-Library/blob/main/USAGE.md#ble-api
    private val transientStatuses = setOf(133, 8, 62, 147)
}
