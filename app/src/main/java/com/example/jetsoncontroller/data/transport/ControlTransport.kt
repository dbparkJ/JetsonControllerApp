package com.example.jetsoncontroller.data.transport

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.JetsonCommand

data class TransportCapabilities(
    val control: Boolean,
    val status: Boolean,
    val networkProvisioning: Boolean,
    val fileBrowsing: Boolean,
    val uploadControl: Boolean
)

interface ControlTransport {

    val type: TransportType

    val capabilities:
        TransportCapabilities

    suspend fun ping(): Boolean

    suspend fun getStatus():
        Result<JetsonStatus>

    suspend fun sendCommand(
        command: JetsonCommand,
        payload: ByteArray = byteArrayOf()
    ): Result<Unit>

    suspend fun disconnect()
}
