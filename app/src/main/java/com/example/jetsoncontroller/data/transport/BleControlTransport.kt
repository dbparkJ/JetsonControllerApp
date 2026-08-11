package com.example.jetsoncontroller.data.transport

import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.JetsonCommand
import kotlinx.coroutines.flow.first

class BleControlTransport(
    private val gattClient: BleGattClient
) : ControlTransport {

    override val type = TransportType.BLE

    override val capabilities = TransportCapabilities(
        control = true,
        status = true,
        networkProvisioning = true, // Possible via GATT
        fileBrowsing = false,
        uploadControl = false
    )

    override suspend fun ping(): Boolean {
        return gattClient.isReady()
    }

    override suspend fun getStatus(): Result<JetsonStatus> {
        return Result.success(gattClient.status.value)
    }

    override suspend fun sendCommand(command: JetsonCommand, payload: ByteArray): Result<Unit> {
        // We'll need to encode command here if not already done in Repository
        // For now let's assume we use the GATT client's writeCommand directly
        // but we'll need to adapt it to the protocol codec
        return Result.failure(Exception("Not implemented"))
    }

    override suspend fun disconnect() {
        gattClient.disconnect()
    }
}
