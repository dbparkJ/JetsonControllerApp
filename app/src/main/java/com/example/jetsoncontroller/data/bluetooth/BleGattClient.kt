package com.example.jetsoncontroller.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.BlePairingState
import com.example.jetsoncontroller.model.BLE_CONNECTION_FAILED_TEXT
import com.example.jetsoncontroller.model.bleConnectionFailureText
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.PairingInfo
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.WifiProvisionRequest
import com.example.jetsoncontroller.model.registeredDeviceDisplayName
import com.example.jetsoncontroller.protocol.PairingAuth
import com.example.jetsoncontroller.protocol.BleTimeSync
import com.example.jetsoncontroller.protocol.StatusCodec
import com.example.jetsoncontroller.protocol.UuidCodec
import com.example.jetsoncontroller.protocol.WifiProvisionCodec
import com.example.jetsoncontroller.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BleGattClient(
    context: Context,
    private val credentialStore: DeviceCredentialStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
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
        val displayName: String,
        // A QR registration names the device; a reconnect keeps the name already stored.
        val registersDevice: Boolean = false
    )

    private var pairingSession: PairingSession? = null

    private var verifiedDeviceId: String? = null
    private var expectedReconnectDeviceId: String? = null

    private var pendingSessionEncryptionKey: ByteArray? = null
    private var sessionEncryptionKey: ByteArray? = null
    private var waitingForTimeSync = false
    private var timeSyncTimeoutJob: Job? = null

    private var connectionGeneration = 0L
    private var connectionAttempt = 0
    private var connectingDevice: BluetoothDevice? = null
    private var pendingConnectJob: Job? = null
    private var connectionTimeoutJob: Job? = null


    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(
        device: BluetoothDevice,
        displayName: String,
        expectedDeviceId: String? = null
    ) {
        disconnect()
        pairingSession = null
        verifiedDeviceId = null
        expectedReconnectDeviceId = expectedDeviceId?.lowercase()
        _pairingState.value = BlePairingState.Idle

        currentDeviceName =
            displayName

        beginConnection(device)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun connectForPairing(
        device: BluetoothDevice,
        displayName: String,
        pairingInfo: PairingInfo
    ) {
        disconnect()
        pairingSession = PairingSession(pairingInfo, displayName, registersDevice = true)
        verifiedDeviceId = null
        expectedReconnectDeviceId = pairingInfo.deviceId.lowercase()
        
        currentDeviceName = displayName
        _pairingState.value = BlePairingState.Connecting

        beginConnection(device)
    }

    // Both registration and reconnect callers stop the scanner before entering here.
    private fun beginConnection(device: BluetoothDevice) {
        connectingDevice = device
        val generation = connectionGeneration
        connectionTimeoutJob = scope.launch(start = CoroutineStart.LAZY) {
            delay(BleConnectionPolicy.CONNECTION_BUDGET_MILLIS)
            synchronized(this@BleGattClient) {
                if (generation == connectionGeneration && _connectionState.value is ConnectionState.Connecting) {
                    handleError(BLE_CONNECTION_FAILED_TEXT, "GATT_CONNECT_TIMEOUT")
                }
            }
        }.also { it.start() }
        scheduleConnection(device, 1, BleConnectionPolicy.initialDelayMillis(sdkInt))
    }

    @SuppressLint("MissingPermission")
    private fun scheduleConnection(device: BluetoothDevice, attempt: Int, waitMillis: Long) {
        connectionAttempt = attempt
        _connectionState.value = ConnectionState.Connecting(currentDeviceName, attempt, expectedReconnectDeviceId)
        if (pairingSession != null) {
            _pairingState.value = if (attempt == 1) BlePairingState.Connecting
                else BlePairingState.Retrying(attempt, BleConnectionPolicy.MAX_ATTEMPTS)
        }
        val generation = connectionGeneration
        if (waitMillis == 0L) {
            openGatt(device)
            return
        }
        pendingConnectJob = scope.launch(start = CoroutineStart.LAZY) {
            delay(waitMillis)
            synchronized(this@BleGattClient) {
                if (generation != connectionGeneration || _connectionState.value !is ConnectionState.Connecting) return@launch
                pendingConnectJob = null
                openGatt(device)
            }
        }.also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun openGatt(device: BluetoothDevice) {
        try {
            bluetoothGatt = connectGattInternal(device)
            if (bluetoothGatt == null) handleError(BLE_CONNECTION_FAILED_TEXT, "GATT_CONNECT_REJECTED")
        } catch (_: SecurityException) {
            handleError("블루투스 연결 권한이 필요합니다.", "GATT_PERMISSION_DENIED")
        } catch (_: RuntimeException) {
            handleError(BLE_CONNECTION_FAILED_TEXT, "GATT_CONNECT_REJECTED")
        }
    }

    private fun cancelPendingConnection() {
        connectionGeneration += 1
        pendingConnectJob?.cancel()
        pendingConnectJob = null
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
        connectingDevice = null
        connectionAttempt = 0
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun connectGattInternal(device: BluetoothDevice): BluetoothGatt? {
        return device.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }


    @SuppressLint("MissingPermission")
    @Synchronized
    fun disconnect() {
        cancelPendingConnection()
        clearTimeSyncWait()
        val gatt = bluetoothGatt
        bluetoothGatt = null
        pairingSession = null
        verifiedDeviceId = null
        expectedReconnectDeviceId = null
        pendingSessionEncryptionKey = null
        sessionEncryptionKey = null
        _connectionState.value = ConnectionState.Disconnected
        _pairingState.value = BlePairingState.Idle
        gatt?.disconnect()
        gatt?.close()
    }


    @SuppressLint("MissingPermission")
    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                synchronized(this@BleGattClient) {
                    if (gatt !== bluetoothGatt) {
                        gatt.close()
                        return
                    }
                    Log.d("JetsonBLE", "onConnectionStateChange: status=$status newState=$newState")
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        val retryDelay = BleConnectionPolicy.retryDelayMillis(
                            status, connectionAttempt, _connectionState.value is ConnectionState.Connecting
                        )
                        val device = connectingDevice
                        if (retryDelay != null && device != null) {
                            // Invalidate before close: callbacks from this GATT cannot mutate the next attempt.
                            bluetoothGatt = null
                            gatt.close()
                            Log.d("JetsonBLE", "Retrying GATT connection: status=$status attempt=${connectionAttempt + 1}/${BleConnectionPolicy.MAX_ATTEMPTS} delayMs=$retryDelay")
                            scheduleConnection(device, connectionAttempt + 1, retryDelay)
                        } else {
                            val message = if (status == 5 || status == 137) {
                                "장치 인증 정보를 확인할 수 없습니다. QR 코드로 다시 등록하세요."
                            } else BLE_CONNECTION_FAILED_TEXT
                            handleError(message, "GATT_$status")
                        }
                        return
                    }

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            cancelPendingConnection()
                            _connectionState.value = ConnectionState.Connected(currentDeviceName)
                            if (pairingSession != null) {
                                _pairingState.value = BlePairingState.DiscoveringServices
                            }
                            if (!gatt.requestMtu(247)) {
                                gatt.discoverServices()
                            }
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (_connectionState.value is ConnectionState.Connecting) {
                                handleError(BLE_CONNECTION_FAILED_TEXT, "GATT_DISCONNECTED")
                                return
                            }
                            cancelPendingConnection()
                            clearTimeSyncWait()
                            bluetoothGatt = null
                            pairingSession = null
                            verifiedDeviceId = null
                            expectedReconnectDeviceId = null
                            pendingSessionEncryptionKey = null
                            sessionEncryptionKey = null
                            if (_connectionState.value !is ConnectionState.Error) {
                                _connectionState.value = ConnectionState.Disconnected
                            }
                            if (_pairingState.value !is BlePairingState.Error) {
                                _pairingState.value = BlePairingState.Idle
                            }
                            gatt.close()
                        }
                    }
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {
                if (gatt !== bluetoothGatt) return
                Log.d("JetsonBLE", "MTU changed: mtu=$mtu status=$status")
                gatt.discoverServices()
            }


            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (gatt !== bluetoothGatt) return
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
                if (gatt !== bluetoothGatt) return
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
                        handleAuthStateRead(gatt, value)
                    }
                }
            }

            @Deprecated("Legacy")
            @Suppress("DEPRECATION")
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
                handleCharacteristicWrite(gatt, characteristic, status)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (gatt !== bluetoothGatt) return
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
            @Suppress("DEPRECATION")
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
                if (gatt !== bluetoothGatt) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w("JetsonBLE", "Status notification descriptor failed: $status")
                }
            }
        }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun handleCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (gatt !== bluetoothGatt) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            handleError("Write failed: $status")
            return
        }
        if (characteristic.uuid == JetsonGattSpec.COMMAND_UUID && waitingForTimeSync) {
            clearTimeSyncWait()
            completeAuthentication()
        } else if (characteristic.uuid == JetsonGattSpec.AUTH_RESPONSE_UUID && pendingSessionEncryptionKey != null) {
            val authStateChar = characteristic.service.getCharacteristic(JetsonGattSpec.AUTH_STATE_UUID)
            if (authStateChar == null || !gatt.readCharacteristic(authStateChar)) {
                handleError("Auth State characteristic read failed")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun handleDeviceIdRead(gatt: BluetoothGatt, value: ByteArray) {
        if (gatt !== bluetoothGatt) return
        val actualDeviceId = try {
            UuidCodec.fromBytes(value).toString().lowercase()
        } catch (e: Exception) {
            handleError("Invalid Device ID format")
            return
        }
        
        Log.d("JetsonBLE", "DEVICE_ID read: $actualDeviceId")
        verifiedDeviceId = actualDeviceId

        val expectedReconnectId = expectedReconnectDeviceId
        if (
            expectedReconnectId != null &&
            !actualDeviceId.equals(expectedReconnectId, ignoreCase = true)
        ) {
            handleError("선택한 등록 장치와 연결된 장치가 일치하지 않습니다.")
            return
        }

        val session = pairingSession
        if (session != null) {
            if (actualDeviceId == session.info.deviceId) {
                proceedToAuthentication(gatt, session.info)
            } else {
                handleError("QR 코드와 연결된 장치가 일치하지 않습니다.")
            }
        } else {
            // Manual connect: check store for this deviceId
            scope.launch {
                val secretHex = credentialStore.getSecret(actualDeviceId)
                synchronized(this@BleGattClient) {
                    if (gatt !== bluetoothGatt) return@launch
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
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun authenticateConnectedDevice(info: PairingInfo): Boolean {
        if (_connectionState.value !is ConnectionState.RegistrationRequired) {
            return false
        }

        val gatt = bluetoothGatt ?: return false
        val actualDeviceId = verifiedDeviceId ?: return false

        if (!actualDeviceId.equals(info.deviceId, ignoreCase = true)) {
            handleError("QR 코드와 현재 연결된 장치가 일치하지 않습니다.")
            return true
        }

        pairingSession = PairingSession(info, currentDeviceName, registersDevice = true)
        proceedToAuthentication(gatt, info)
        return true
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun proceedToAuthentication(gatt: BluetoothGatt, info: PairingInfo) {
        if (gatt !== bluetoothGatt) return
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
    @Synchronized
    private fun handleChallengeRead(gatt: BluetoothGatt, challenge: ByteArray) {
        if (gatt !== bluetoothGatt) return
        val session = pairingSession ?: return
        try {
            Log.d("JetsonBLE", "Challenge received: ${challenge.size} bytes")
            Log.d("JetsonBLE", "Computing HMAC")
            val response = PairingAuth.computeResponse(
                deviceId = session.info.deviceId,
                bootstrapSecretHex = session.info.bootstrapSecretHex,
                challenge = challenge
            )
            pendingSessionEncryptionKey = PairingAuth.deriveSessionKey(
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
                pendingSessionEncryptionKey = null
                handleError("Auth Response characteristic not found")
            }
        } catch (e: Exception) {
            pendingSessionEncryptionKey = null
            handleError("Authentication calculation failed")
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun handleAuthStateRead(gatt: BluetoothGatt, value: ByteArray) {
        if (gatt !== bluetoothGatt || waitingForTimeSync || isReady()) return
        val authState = if (value.isNotEmpty()) value[0].toInt() else -1
        Log.d("JetsonBLE", "AUTH_STATE=$authState")
        if (authState == 1) {
            val encryptionKey = pendingSessionEncryptionKey
            if (encryptionKey == null) {
                handleError("BLE 보안 세션을 만들지 못했습니다.")
                return
            }
            sessionEncryptionKey = encryptionKey
            pendingSessionEncryptionKey = null
            if (BleTimeSync.supported(value)) {
                // Do not publish Ready until the authenticated command has been
                // acknowledged. P2P must not issue a DHCP lease on the boot clock.
                waitingForTimeSync = true
                val sent = try {
                    writeCommandInternal(BleTimeSync.command(currentTimeMillis()))
                } catch (_: IllegalArgumentException) {
                    handleError("휴대폰의 현재 시각을 확인한 뒤 다시 연결해 주세요.")
                    return
                } catch (_: SecurityException) {
                    handleError("Bluetooth 권한을 허용한 뒤 다시 연결해 주세요.")
                    return
                }
                if (!sent) {
                    handleError("장치의 연결 전 시각 동기화를 시작하지 못했습니다.")
                } else if (waitingForTimeSync) {
                    val timeoutJob = scope.launch(start = CoroutineStart.LAZY) {
                        delay(10_000L)
                        synchronized(this@BleGattClient) {
                            if (gatt === bluetoothGatt && waitingForTimeSync) {
                                handleError("장치의 연결 전 시각 동기화 응답 시간이 초과되었습니다.")
                            }
                        }
                    }
                    timeSyncTimeoutJob = timeoutJob
                    timeoutJob.start()
                }
            } else {
                // Old backends advertise no capability; retain HTTP time sync.
                completeAuthentication()
            }
        } else {
            pendingSessionEncryptionKey = null
            sessionEncryptionKey = null
            handleError("장치 인증에 실패했습니다.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun completeAuthentication() {
        val completedSession = pairingSession
        if (completedSession != null) {
            scope.launch {
                credentialStore.saveCredential(
                    completedSession.info,
                    deviceName = completedSession.displayName
                        .takeIf { completedSession.registersDevice }
                        ?.let { registeredDeviceDisplayName(completedSession.info.deviceId, it) }
                )
            }
        }
        pairingSession = null
        _pairingState.value = BlePairingState.Ready(currentDeviceName)
        _connectionState.value = ConnectionState.Ready(currentDeviceName)

        Log.d("JetsonBLE", "Authentication complete; enabling STATUS notifications")
        if (!enableStatusNotifications()) {
            Log.w("JetsonBLE", "Status notifications are unavailable; connection remains ready")
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun handleError(message: String, technicalCode: String? = null) {
        cancelPendingConnection()
        clearTimeSyncWait()
        val gatt = bluetoothGatt
        bluetoothGatt = null
        pairingSession = null
        pendingSessionEncryptionKey = null
        sessionEncryptionKey = null
        verifiedDeviceId = null
        _connectionState.value = ConnectionState.Error(bleConnectionFailureText(message), expectedReconnectDeviceId)
        expectedReconnectDeviceId = null
        _pairingState.value = BlePairingState.Error(message, technicalCode)
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
            // Permission may have been revoked while the connection was active.
        }
        gatt?.close()
    }

    private fun clearTimeSyncWait() {
        waitingForTimeSync = false
        timeSyncTimeoutJob?.cancel()
        timeSyncTimeoutJob = null
    }

    fun isReady(): Boolean {
        return connectionState.value is ConnectionState.Ready
    }

    fun currentDeviceId(): String? = verifiedDeviceId

    fun encodeWifiProvision(request: WifiProvisionRequest): ByteArray {
        val key = sessionEncryptionKey
            ?: error("Bluetooth 보안 세션이 준비되지 않았습니다.")
        val deviceId = verifiedDeviceId
            ?: error("Bluetooth 장치 ID를 확인할 수 없습니다.")
        return WifiProvisionCodec.encodeEncrypted(request, key, deviceId)
    }


    @SuppressLint("MissingPermission")
    @Synchronized
    fun writeCommand(
        payload: ByteArray
    ): Boolean = isReady() && writeCommandInternal(payload)

    @SuppressLint("MissingPermission")
    private fun writeCommandInternal(
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
            ) == BluetoothStatusCodes.SUCCESS

        } else {

            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
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
            ) == BluetoothStatusCodes.SUCCESS

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
