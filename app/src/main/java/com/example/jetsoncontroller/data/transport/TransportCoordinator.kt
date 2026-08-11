package com.example.jetsoncontroller.data.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransportCoordinator {

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    val state: StateFlow<TransportState> = _state.asStateFlow()

    private var activeTransport: ControlTransport? = null

    fun currentTransport(): ControlTransport? = activeTransport

    fun setActiveTransport(transport: ControlTransport) {
        activeTransport = transport
        _state.value = TransportState.Connected(transport.type)
    }

    fun disconnect() {
        activeTransport = null
        _state.value = TransportState.Disconnected
    }

    fun setError(type: TransportType?, message: String) {
        _state.value = TransportState.Error(type, message)
    }
}
