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
        val apiCommand = when (command) {
            JetsonCommand.START_SYSTEM -> "start-system"
            JetsonCommand.STOP_SYSTEM -> "stop-system"
            JetsonCommand.RESTART_SERVICES -> "restart-services"
            JetsonCommand.REBOOT -> "reboot"
            JetsonCommand.SHUTDOWN -> "shutdown"
            JetsonCommand.GET_STATUS -> return apiClient.getStatus().map { Unit }
            JetsonCommand.SET_WIFI -> return Result.failure(
                Exception("Wi-Fi 설정은 전용 요청으로 전송해야 합니다.")
            )
        }
        return apiClient.sendCommand(apiCommand)
    }

    override suspend fun disconnect() {
        // HTTP is stateless, but we might clear session if needed
    }
}
