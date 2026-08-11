package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonStatus

data class DashboardUiState(

    val connectionState:
        ConnectionState =
        ConnectionState.Disconnected,

    val status:
        JetsonStatus =
        JetsonStatus()
)
