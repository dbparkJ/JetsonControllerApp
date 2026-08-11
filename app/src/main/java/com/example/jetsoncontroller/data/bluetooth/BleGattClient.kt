package com.example.jetsoncontroller.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.example.jetsoncontroller.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleGattClient(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private var bluetoothGatt:
        BluetoothGatt? = null

    private var currentDeviceName:
        String = "Jetson"

    private val _connectionState =
        MutableStateFlow<ConnectionState>(
            ConnectionState.Disconnected
        )

    val connectionState:
        StateFlow<ConnectionState> =
        _connectionState.asStateFlow()


    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothDevice,
        displayName: String
    ) {

        disconnect()

        currentDeviceName =
            displayName

        _connectionState.value =
            ConnectionState.Connecting(
                displayName
            )

        bluetoothGatt =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.M
            ) {

                device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )

            } else {

                device.connectGatt(
                    appContext,
                    false,
                    gattCallback
                )
            }
    }


    @SuppressLint("MissingPermission")
    fun disconnect() {

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        bluetoothGatt = null

        _connectionState.value =
            ConnectionState.Disconnected
    }


    @SuppressLint("MissingPermission")
    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    _connectionState.value =
                        ConnectionState.Error(
                            "GATT connection error: $status"
                        )

                    gatt.close()

                    return
                }

                when (newState) {

                    BluetoothProfile.STATE_CONNECTED -> {

                        _connectionState.value =
                            ConnectionState.Connected(
                                currentDeviceName
                            )

                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {

                        _connectionState.value =
                            ConnectionState.Disconnected

                        gatt.close()
                    }
                }
            }


            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    _connectionState.value =
                        ConnectionState.Error(
                            "Service discovery failed: $status"
                        )

                    return
                }

                val jetsonService =
                    gatt.getService(
                        JetsonGattSpec.SERVICE_UUID
                    )

                if (jetsonService == null) {

                    _connectionState.value =
                        ConnectionState.Error(
                            "Jetson Control Service not found"
                        )

                    return
                }

                _connectionState.value =
                    ConnectionState.Ready(
                        currentDeviceName
                    )
            }


            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                    BluetoothGattCharacteristic,
                value: ByteArray
            ) {

                when (characteristic.uuid) {

                    JetsonGattSpec.STATUS_UUID -> {
                        /*
                         * TODO:
                         * decode Jetson status packet
                         * and expose through repository.
                         */
                    }
                }
            }

            @Deprecated(
                "Legacy Android callback"
            )
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                    BluetoothGattCharacteristic
            ) {

                onCharacteristicChanged(
                    gatt,
                    characteristic,
                    characteristic.value ?: byteArrayOf()
                )
            }
        }


    fun isReady(): Boolean {
        return connectionState.value is ConnectionState.Ready
    }


    @SuppressLint("MissingPermission")
    fun writeCommand(
        payload: ByteArray
    ): Boolean {

        val gatt =
            bluetoothGatt
                ?: return false

        val service =
            gatt.getService(
                JetsonGattSpec.SERVICE_UUID
            ) ?: return false

        val characteristic =
            service.getCharacteristic(
                JetsonGattSpec.COMMAND_UUID
            ) ?: return false

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic
                    .WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS

        } else {

            @Suppress("DEPRECATION")
            characteristic.value =
                payload

            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(
                characteristic
            )
        }
    }


    @SuppressLint("MissingPermission")
    fun enableStatusNotifications(): Boolean {

        val gatt =
            bluetoothGatt
                ?: return false

        val service =
            gatt.getService(
                JetsonGattSpec.SERVICE_UUID
            ) ?: return false

        val characteristic =
            service.getCharacteristic(
                JetsonGattSpec.STATUS_UUID
            ) ?: return false

        val notificationEnabled =
            gatt.setCharacteristicNotification(
                characteristic,
                true
            )

        if (!notificationEnabled) {
            return false
        }

        val cccdUuid =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )

        val descriptor =
            characteristic.getDescriptor(
                cccdUuid
            ) ?: return false

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE
            ) == BluetoothGatt.GATT_SUCCESS

        } else {

            @Suppress("DEPRECATION")
            descriptor.value =
                BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE

            @Suppress("DEPRECATION")
            gatt.writeDescriptor(
                descriptor
            )
        }
    }
}
