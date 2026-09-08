package com.example.jetsoncontroller.data.transport

import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransportCoordinator {

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    val state: StateFlow<TransportState> = _state.asStateFlow()

    private var activeTransport: ControlTransport? = null
    private var sessionId = 0L
    private var requestId = 0L
    private var connectionAttemptId = 0L
    private val appliedRequests = mutableMapOf<String, Long>()

    // A temporary BLE session and an IP verification attempt have independent lifetimes.
    @Synchronized
    fun nextConnectionAttempt(): Long = ++connectionAttemptId

    @Synchronized
    fun connectionAttemptIsCurrent(attemptId: Long): Boolean =
        connectionAttemptId == attemptId

    class Request internal constructor(
        val transport: ControlTransport,
        val deviceId: String?,
        internal val sessionId: Long,
        internal val requestId: Long,
        internal val operation: String
    )

    @Synchronized
    fun beginRequest(operation: String): Request? {
        val transport = activeTransport ?: return null
        return Request(transport, (_state.value as? TransportState.Connected)?.deviceId,
            sessionId, ++requestId, operation)
    }

    @Synchronized
    fun isCurrent(request: Request): Boolean =
        request.sessionId == sessionId && request.transport === activeTransport &&
            request.deviceId == (_state.value as? TransportState.Connected)?.deviceId

    /** Commit a response only once and never over a newer response in the same stream. */
    @Synchronized
    fun applyResponse(request: Request, apply: () -> Unit): Boolean {
        if (!isCurrent(request) ||
            request.requestId <= (appliedRequests[request.operation] ?: 0L)) return false
        appliedRequests[request.operation] = request.requestId
        apply()
        return true
    }

    @Synchronized
    fun currentTransport(): ControlTransport? = activeTransport

    @Synchronized
    fun setActiveTransport(
        transport: ControlTransport,
        endpoint: String? = null,
        deviceId: String? = null,
        deviceName: String? = null
    ) {
        val previousState = diagnosticState()
        sessionId += 1
        appliedRequests.clear()
        activeTransport = transport
        _state.value = TransportState.Connected(
            type = transport.type,
            endpoint = endpoint,
            deviceId = deviceId,
            deviceName = deviceName
        )
        ConnectionDiagnostics.record("transport_state", mapOf(
            "oldState" to previousState, "newState" to "CONNECTED", "sessionId" to sessionId,
            "actualTransport" to transport.type, "attemptId" to connectionAttemptId,
            "deviceRef" to ConnectionDiagnostics.privateRef(deviceId),
            "endpointRef" to ConnectionDiagnostics.privateRef(endpoint)
        ))
    }

    @Synchronized
    fun disconnect() {
        val previousState = diagnosticState()
        sessionId += 1
        appliedRequests.clear()
        activeTransport = null
        _state.value = TransportState.Disconnected
        ConnectionDiagnostics.record("transport_state", mapOf(
            "oldState" to previousState, "newState" to "DISCONNECTED", "sessionId" to sessionId,
            "attemptId" to connectionAttemptId
        ))
    }

    @Synchronized
    fun setError(type: TransportType?, message: String) {
        val previousState = diagnosticState()
        sessionId += 1
        appliedRequests.clear()
        activeTransport = null
        _state.value = TransportState.Error(type, message)
        ConnectionDiagnostics.record("transport_state", mapOf(
            "oldState" to previousState, "newState" to "ERROR", "sessionId" to sessionId,
            "actualTransport" to type, "attemptId" to connectionAttemptId
        ), incident = true)
    }

    private fun diagnosticState(): String = when (_state.value) {
        is TransportState.Connecting -> "CONNECTING"
        is TransportState.Connected -> "CONNECTED"
        is TransportState.Error -> "ERROR"
        TransportState.Disconnected -> "DISCONNECTED"
    }
}
