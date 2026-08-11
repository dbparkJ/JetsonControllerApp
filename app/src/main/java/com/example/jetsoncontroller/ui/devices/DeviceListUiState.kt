package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonDevice

data class DeviceListUiState(

    val devices:
        List<JetsonDevice> =
        emptyList(),

    val isScanning:
        Boolean = false,

    val permissionGranted:
        Boolean = false,

    val connectionState:
        ConnectionState =
        ConnectionState.Disconnected
)
