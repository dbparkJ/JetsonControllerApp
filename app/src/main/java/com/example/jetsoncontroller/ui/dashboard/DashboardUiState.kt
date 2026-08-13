package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ControlCapabilities

data class DashboardUiState(

    val connectionState:
        ConnectionState =
        ConnectionState.Disconnected,

    val transportType: TransportType? = null,

    val deviceName: String = "Jetson",

    val endpoint: String? = null,

    val capabilities: ControlCapabilities = ControlCapabilities(),

    val operationInProgress: Boolean = false,

    val operationMessage: String? = null,

    val operationIsError: Boolean = false,

    val statusFreshness: StatusFreshness = StatusFreshness.UNKNOWN,

    val statusAgeSeconds: Long? = null,

    val status:
        JetsonStatus =
        JetsonStatus()
)
