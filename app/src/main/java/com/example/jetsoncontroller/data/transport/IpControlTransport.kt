package com.example.jetsoncontroller.data.transport

import com.example.jetsoncontroller.data.network.LocalApiClient
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.JetsonCommand

class IpControlTransport(
    private val apiClient: LocalApiClient,
    override val type: TransportType
) : ControlTransport {

    override val capabilities = TransportCapabilities(
        control = true,
        status = true,
        networkProvisioning = true,
        fileBrowsing = true,
        uploadControl = true
    )

    override suspend fun ping(): Boolean {
        return apiClient.hello().isSuccess
    }

    override suspend fun getStatus(): Result<JetsonStatus> {
        return apiClient.getStatus()
    }

    override suspend fun sendCommand(command: JetsonCommand, payload: ByteArray): Result<Unit> {
        // TODO: Implement command sending in LocalApiClient
        return Result.failure(Exception("Not implemented"))
    }

    override suspend fun disconnect() {
        // HTTP is stateless, but we might clear session if needed
    }
}
