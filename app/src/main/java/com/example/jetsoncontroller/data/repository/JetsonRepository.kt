package com.example.jetsoncontroller.data.repository

import android.content.Context
import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.data.bluetooth.BleScanner
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.CommandCodec
import com.example.jetsoncontroller.protocol.JetsonCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JetsonRepository(
    context: Context
) {

    private val scanner =
        BleScanner(context)

    private val gattClient =
        BleGattClient(context)

    val devices:
        StateFlow<List<JetsonDevice>> =
        scanner.devices

    val isScanning:
        StateFlow<Boolean> =
        scanner.isScanning

    val connectionState:
        StateFlow<ConnectionState> =
        gattClient.connectionState

    private val _status =
        MutableStateFlow(
            JetsonStatus()
        )

    val status:
        StateFlow<JetsonStatus> =
        _status.asStateFlow()


    fun startScan() {

        scanner.startScan(
            durationMillis = 15_000L
        )
    }


    fun stopScan() {

        scanner.stopScan()
    }


    fun connect(
        device: JetsonDevice
    ) {

        scanner.stopScan()

        gattClient.connect(
            device = device.device,
            displayName = device.name
        )
    }


    fun disconnect() {

        gattClient.disconnect()
    }


    fun sendCommand(
        command: JetsonCommand,
        payload: ByteArray = byteArrayOf()
    ): Boolean {

        val frame =
            CommandCodec.encode(
                command,
                payload
            )

        return gattClient.writeCommand(
            frame
        )
    }


    fun requestStatus(): Boolean {

        return sendCommand(
            JetsonCommand.GET_STATUS
        )
    }


    fun startSystem(): Boolean {

        return sendCommand(
            JetsonCommand.START_SYSTEM
        )
    }


    fun stopSystem(): Boolean {

        return sendCommand(
            JetsonCommand.STOP_SYSTEM
        )
    }


    fun restartServices(): Boolean {

        return sendCommand(
            JetsonCommand.RESTART_SERVICES
        )
    }


    fun reboot(): Boolean {

        return sendCommand(
            JetsonCommand.REBOOT
        )
    }


    fun shutdown(): Boolean {

        return sendCommand(
            JetsonCommand.SHUTDOWN
        )
    }
}
