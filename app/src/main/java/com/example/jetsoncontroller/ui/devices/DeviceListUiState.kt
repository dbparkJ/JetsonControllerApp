package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.RegisteredDevice

data class DeviceListUiState(

    val devices:
        List<JetsonDevice> =
        emptyList(),

    val registeredDevices:
        List<RegisteredDevice> =
        emptyList(),

    val isScanning:
        Boolean = false,

    val permissionGranted:
        Boolean = false,

    val connectionState:
        ConnectionState =
        ConnectionState.Disconnected,

    val reconnectingDeviceId:
        String? = null,

    val reconnectError:
        String? = null,

    val scanError:
        String? = null
)
