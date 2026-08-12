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
import android.util.Log
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.BlePairingState
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.PairingInfo
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.PairingAuth
import com.example.jetsoncontroller.protocol.StatusCodec
import com.example.jetsoncontroller.protocol.UuidCodec
import com.example.jetsoncontroller.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BleGattClient(
    context: Context,
    private val credentialStore: DeviceCredentialStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private val _pairingState =
        MutableStateFlow<BlePairingState>(
            BlePairingState.Idle
        )

    val pairingState:
        StateFlow<BlePairingState> =
        _pairingState.asStateFlow()

    private val _status =
        MutableStateFlow(JetsonStatus())

    val status:
        StateFlow<JetsonStatus> =
        _status.asStateFlow()

    private data class PairingSession(
        val info: PairingInfo,
        val displayName: String
    )

    private var pairingSession: PairingSession? = null

    private var verifiedDeviceId: String? = null


    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothDevice,
        displayName: String
    ) {
        disconnect()
        pairingSession = null
        verifiedDeviceId = null
        _pairingState.value = BlePairingState.Idle

        currentDeviceName =
            displayName

        _connectionState.value =
            ConnectionState.Connecting(
                displayName
            )

        bluetoothGatt = connectGattInternal(device)
    }

    @SuppressLint("MissingPermission")
    fun connectForPairing(
        device: BluetoothDevice,
        displayName: String,
        pairingInfo: PairingInfo
    ) {
        disconnect()
        pairingSession = PairingSession(pairingInfo, displayName)
        verifiedDeviceId = null
        
        currentDeviceName = displayName
        _connectionState.value = ConnectionState.Connecting(displayName)
        _pairingState.value = BlePairingState.Connecting

        bluetoothGatt = connectGattInternal(device)
    }

    @SuppressLint("MissingPermission")
    private fun connectGattInternal(device: BluetoothDevice): BluetoothGatt? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        verifiedDeviceId = null
        _connectionState.value = ConnectionState.Disconnected
        _pairingState.value = BlePairingState.Idle
    }


    @SuppressLint("MissingPermission")
    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                Log.d("JetsonBLE", "onConnectionStateChange: status=$status newState=$newState")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    handleError("GATT connection error: $status")
                    gatt.close()
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionState.value = ConnectionState.Connected(currentDeviceName)
                        if (pairingSession != null) {
                            _pairingState.value = BlePairingState.DiscoveringServices
                        }
                        if (!gatt.requestMtu(247)) {
                            gatt.discoverServices()
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        verifiedDeviceId = null
                        _connectionState.value = ConnectionState.Disconnected
                        _pairingState.value = BlePairingState.Idle
                        gatt.close()
                    }
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {
                Log.d("JetsonBLE", "MTU changed: mtu=$mtu status=$status")
                gatt.discoverServices()
            }


            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    handleError("Service discovery failed: $status")
                    return
                }

                val jetsonService = gatt.getService(JetsonGattSpec.SERVICE_UUID)
                if (jetsonService == null) {
                    handleError("Jetson Control Service not found")
                    return
                }

                // Always try to read Device ID to verify identity or find stored credentials
                _pairingState.value = BlePairingState.VerifyingIdentity
                val deviceIdChar = jetsonService.getCharacteristic(JetsonGattSpec.DEVICE_ID_UUID)
                if (deviceIdChar != null) {
                    Log.d("JetsonBLE", "Reading DEVICE_ID characteristic")
                    gatt.readCharacteristic(deviceIdChar)
                } else {
                    handleError("Device ID characteristic not found")
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                Log.d("JetsonBLE", "onCharacteristicRead: uuid=${characteristic.uuid} status=$status valueSize=${value.size}")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    handleError("Read failed: $status (uuid=${characteristic.uuid})")
                    return
                }

                when (characteristic.uuid) {
                    JetsonGattSpec.DEVICE_ID_UUID -> {
                        handleDeviceIdRead(gatt, value)
                    }
                    JetsonGattSpec.AUTH_CHALLENGE_UUID -> {
                        handleChallengeRead(gatt, value)
                    }
                    JetsonGattSpec.AUTH_STATE_UUID -> {
                        handleAuthStateRead(value)
                    }
                }
            }

            @Deprecated("Legacy")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                onCharacteristicRead(gatt, characteristic, characteristic.value ?: byteArrayOf(), status)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    handleError("Write failed: $status")
                    return
                }

                if (characteristic.uuid == JetsonGattSpec.AUTH_RESPONSE_UUID) {
                    Log.d("JetsonBLE", "AUTH_RESPONSE write callback status=$status")
                    // Response written, now check state
                    val authStateChar = characteristic.service.getCharacteristic(JetsonGattSpec.AUTH_STATE_UUID)
                    if (authStateChar != null) {
                        Log.d("JetsonBLE", "Reading AUTH_STATE")
                        gatt.readCharacteristic(authStateChar)
                    } else {
                        handleError("Auth State characteristic not found")
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == JetsonGattSpec.STATUS_UUID) {
                    Log.d("JetsonBLE", "STATUS notification received: ${value.size} bytes")
                    try {
                        _status.value = StatusCodec.decode(value)
                    } catch (e: Exception) {
                        // Log decoding error
                    }
                }
            }

            @Deprecated("Legacy")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                onCharacteristicChanged(gatt, characteristic, characteristic.value ?: byteArrayOf())
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (pairingSession != null) {
                        val session = pairingSession!!
                        scope.launch {
                            credentialStore.saveCredential(session.info)
                        }
                        _pairingState.value = BlePairingState.Ready(currentDeviceName)
                        _connectionState.value = ConnectionState.Ready(currentDeviceName)
                    }
                } else {
                    handleError("Descriptor write failed: $status")
                }
            }
        }

    @SuppressLint("MissingPermission")
    private fun handleDeviceIdRead(gatt: BluetoothGatt, value: ByteArray) {
        val actualDeviceId = try {
            UuidCodec.fromBytes(value).toString().lowercase()
        } catch (e: Exception) {
            handleError("Invalid Device ID format")
            return
        }
        
        Log.d("JetsonBLE", "DEVICE_ID read: $actualDeviceId")
        verifiedDeviceId = actualDeviceId

        val session = pairingSession
        if (session != null) {
            if (actualDeviceId == session.info.deviceId) {
                proceedToAuthentication(gatt, session.info)
            } else {
                handleError("QR 코드와 연결된 Jetson이 일치하지 않습니다.")
                disconnect()
            }
        } else {
            // Manual connect: check store for this deviceId
            scope.launch {
                val secretHex = credentialStore.getSecret(actualDeviceId)
                if (secretHex != null) {
                    Log.d("JetsonBLE", "Stored credential found for $actualDeviceId. Authenticating...")
                    val info = PairingInfo(1, actualDeviceId, secretHex)
                    proceedToAuthentication(gatt, info)
                } else {
                    _pairingState.value = BlePairingState.Idle
                    _connectionState.value =
                        ConnectionState.RegistrationRequired(
                            deviceName = currentDeviceName,
                            deviceId = actualDeviceId
                        )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun authenticateConnectedDevice(info: PairingInfo): Boolean {
        if (_connectionState.value !is ConnectionState.RegistrationRequired) {
            return false
        }

        val gatt = bluetoothGatt ?: return false
        val actualDeviceId = verifiedDeviceId ?: return false

        if (!actualDeviceId.equals(info.deviceId, ignoreCase = true)) {
            handleError("QR 코드와 현재 연결된 Jetson이 일치하지 않습니다.")
            return true
        }

        pairingSession = PairingSession(info, currentDeviceName)
        proceedToAuthentication(gatt, info)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun proceedToAuthentication(gatt: BluetoothGatt, info: PairingInfo) {
        // We need to keep track of the info for challenge computation
        // If it was a manual connect, we might want to set a temporary session
        if (pairingSession == null) {
            pairingSession = PairingSession(info, currentDeviceName)
        }

        _pairingState.value = BlePairingState.Authenticating
        val challengeChar = gatt.getService(JetsonGattSpec.SERVICE_UUID)
            ?.getCharacteristic(JetsonGattSpec.AUTH_CHALLENGE_UUID)
        if (challengeChar != null) {
            Log.d("JetsonBLE", "Reading AUTH_CHALLENGE")
            gatt.readCharacteristic(challengeChar)
        } else {
            handleError("Auth Challenge characteristic not found")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleChallengeRead(gatt: BluetoothGatt, challenge: ByteArray) {
        val session = pairingSession ?: return
        try {
            Log.d("JetsonBLE", "Challenge received: ${challenge.size} bytes")
            Log.d("JetsonBLE", "Computing HMAC")
            val response = PairingAuth.computeResponse(
                deviceId = session.info.deviceId,
                bootstrapSecretHex = session.info.bootstrapSecretHex,
                challenge = challenge
            )
            val responseChar = gatt.getService(JetsonGattSpec.SERVICE_UUID)
                ?.getCharacteristic(JetsonGattSpec.AUTH_RESPONSE_UUID)
            if (responseChar != null) {
                Log.d("JetsonBLE", "Writing AUTH_RESPONSE: ${response.size} bytes")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = gatt.writeCharacteristic(responseChar, response, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    Log.d("JetsonBLE", "writeCharacteristic status: $status")
                } else {
                    @Suppress("DEPRECATION")
                    responseChar.value = response
                    @Suppress("DEPRECATION")
                    val success = gatt.writeCharacteristic(responseChar)
                    Log.d("JetsonBLE", "writeCharacteristic success: $success")
                }
            } else {
                handleError("Auth Response characteristic not found")
            }
        } catch (e: Exception) {
            handleError("Authentication calculation failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleAuthStateRead(value: ByteArray) {
        val authState = if (value.isNotEmpty()) value[0].toInt() else -1
        Log.d("JetsonBLE", "AUTH_STATE=$authState")
        if (authState == 1) {
            _pairingState.value = BlePairingState.EnablingNotifications
            Log.d("JetsonBLE", "Enabling STATUS notifications")
            enableStatusNotifications()
        } else {
            handleError("장비 인증에 실패했습니다.")
            disconnect()
        }
    }

    private fun handleError(message: String) {
        _connectionState.value = ConnectionState.Error(message)
        _pairingState.value = BlePairingState.Error(message)
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
