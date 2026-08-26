package com.example.jetsoncontroller.data.transport

import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.JetsonCommand
import com.example.jetsoncontroller.protocol.CommandCodec

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
        return if (gattClient.writeCommand(CommandCodec.encode(command, payload))) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Bluetooth command write failed"))
        }
    }

    override suspend fun disconnect() {
        gattClient.disconnect()
    }
}
