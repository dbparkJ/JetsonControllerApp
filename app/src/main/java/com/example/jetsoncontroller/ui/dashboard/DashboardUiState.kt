package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonStatus

data class DashboardUiState(

    val connectionState:
        ConnectionState =
        ConnectionState.Disconnected,

    val transportType: TransportType? = null,

    val status:
        JetsonStatus =
        JetsonStatus()
)
