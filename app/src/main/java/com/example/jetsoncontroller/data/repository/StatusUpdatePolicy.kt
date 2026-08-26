package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.transport.TransportType

/**
 * BLE status packets contain only compact legacy fields. Once an IP transport
 * is active, accepting those packets would erase richer sensor telemetry that
 * arrived from the local API and make the sensor UI alternate between states.
 */
internal fun shouldApplyBleStatus(activeTransportType: TransportType?): Boolean =
    activeTransportType == TransportType.BLE
