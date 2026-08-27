package com.example.jetsoncontroller.data.repository

import android.content.Context
import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.data.bluetooth.BleScanState
import com.example.jetsoncontroller.data.bluetooth.BleScanner
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.data.network.LanDiscoveryManager
import com.example.jetsoncontroller.data.network.LocalApiClient
import com.example.jetsoncontroller.data.network.LocalControlApi
import com.example.jetsoncontroller.data.network.WifiDirectManager
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiAccessPointScanner
import com.example.jetsoncontroller.data.transport.*
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.protocol.CommandCodec
import com.example.jetsoncontroller.protocol.JetsonCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

class JetsonRepository(
    context: Context,
    private val credentialStore: DeviceCredentialStore
) {
    companion object {
        const val LOCAL_API_PORT = 8765
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scanner =
        BleScanner(context)

    private val gattClient =
        BleGattClient(context, credentialStore)

    private val wifiDirectManager = WifiDirectManager(context)
    private val wifiAccessPointScanner = WifiAccessPointScanner(context)
    private val lanDiscoveryManager = LanDiscoveryManager(context)
    private val transportCoordinator = TransportCoordinator()
    private val ipConnectionGeneration = AtomicLong(0)
    private val consecutiveIpStatusFailures = AtomicInteger(0)
    private val explicitDisconnectRequested = AtomicBoolean(false)

    @Volatile
    private var activeIpClient: LocalApiClient? = null

    val devices:
        StateFlow<List<JetsonDevice>> =
        scanner.devices

    val isScanning:
        StateFlow<Boolean> =
        scanner.isScanning

    val scanState:
        StateFlow<BleScanState> =
        scanner.scanState

    val connectionState:
        StateFlow<ConnectionState> =
        gattClient.connectionState

    val pairingState:
        StateFlow<BlePairingState> =
        gattClient.pairingState

    val registeredDevices:
        StateFlow<List<RegisteredDevice>> =
        credentialStore.registeredDevices
            .map { credentials ->
                credentials
                    .map {
                        RegisteredDevice(
                            deviceId = it.deviceId,
                            deviceName = it.deviceName
                        )
                    }
                    .sortedBy { it.deviceName.lowercase() }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    private val _status = MutableStateFlow(JetsonStatus())
    val status: StateFlow<JetsonStatus> = _status.asStateFlow()

    private val _statusUpdatedAtEpochMillis = MutableStateFlow<Long?>(null)
    val statusUpdatedAtEpochMillis: StateFlow<Long?> =
        _statusUpdatedAtEpochMillis.asStateFlow()

    private val _capabilities = MutableStateFlow(ControlCapabilities())
    val capabilities: StateFlow<ControlCapabilities> = _capabilities.asStateFlow()

    private val _controlOperation = MutableStateFlow(ControlOperationState())
    val controlOperation: StateFlow<ControlOperationState> =
        _controlOperation.asStateFlow()

    val wifiDirectState = wifiDirectManager.state
    val wifiAccessPointState = wifiAccessPointScanner.state
    val lanEndpoints = lanDiscoveryManager.discoveredEndpoints
    val lanLastSeenAtEpochMillis = lanDiscoveryManager.lastSeenAtEpochMillis
    val isLanDiscovering = lanDiscoveryManager.isDiscovering
    val lanDiscoveryError = lanDiscoveryManager.error

    private val _connectingLanDeviceId = MutableStateFlow<String?>(null)
    private val _visibleConnectingLanDeviceId = MutableStateFlow<String?>(null)
    val connectingLanDeviceId: StateFlow<String?> =
        _visibleConnectingLanDeviceId.asStateFlow()

    private val _lanConnectionError = MutableStateFlow<String?>(null)
    val lanConnectionError: StateFlow<String?> =
        _lanConnectionError.asStateFlow()

    private val autoLanEnabled = MutableStateFlow(false)
    private val autoLanAttempts = ConcurrentHashMap.newKeySet<String>()
    private val autoLanFailureCounts = ConcurrentHashMap<String, Int>()
    private val automaticConnectivityEnabled = MutableStateFlow(false)
    private val localNetworkPermissionGranted = MutableStateFlow(false)
    private val nearbyWifiPermissionGranted = MutableStateFlow(false)
    private val bluetoothPermissionGranted = MutableStateFlow(false)
    private val preferredAutomaticDeviceId = MutableStateFlow<String?>(null)
    private val qrPairingActive = MutableStateFlow(false)
    private val automaticDirectFallbackReady = MutableStateFlow(false)
    private var automaticDirectFallbackJob: Job? = null
    private var automaticBleReconnectJob: Job? = null

    @Volatile
    private var pendingWifiDirectTargetDeviceId: String? = null

    @Volatile
    private var connectingLanGeneration: Long? = null

    private data class WifiDirectApiProbe(
        val client: LocalApiClient,
        val hello: LocalControlApi.HelloResponse,
        val status: JetsonStatus,
        val capabilities: LocalControlApi.CapabilitiesResponse
    )

    val transportState = transportCoordinator.state

    init {
        scope.launch {
            gattClient.status.drop(1).collect { currentStatus ->
                updateStatus(currentStatus)
            }
        }

        scope.launch {
            gattClient.connectionState.collect { state ->
                if (state is ConnectionState.Connecting) {
                    val previousTransport = transportCoordinator.currentTransport()?.type
                    if (
                        (previousTransport == null || previousTransport == TransportType.BLE) &&
                        _connectingLanDeviceId.value == null
                    ) {
                        ipConnectionGeneration.incrementAndGet()
                        activeIpClient = null
                        transportCoordinator.disconnect()
                        clearReachableDeviceState()
                    }
                } else if (state is ConnectionState.Ready) {
                    val deviceId = gattClient.currentDeviceId()
                    if (deviceId != null) {
                        preferredAutomaticDeviceId.value = deviceId
                    }
                    qrPairingActive.value = false
                    ensureAutomaticBleReconnectLoop()
                    val currentType = transportCoordinator.currentTransport()?.type
                    if (currentType == TransportType.LAN || currentType == TransportType.WIFI_DIRECT) {
                        gattClient.disconnect()
                        return@collect
                    }
                    ipConnectionGeneration.incrementAndGet()
                    activeIpClient = null
                    consecutiveIpStatusFailures.set(0)
                    transportCoordinator.setActiveTransport(
                        transport = BleControlTransport(gattClient),
                        deviceId = deviceId,
                        deviceName = state.deviceName
                    )
                    _capabilities.value = ControlCapabilities(
                        systemControlConfigured = false,
                        powerCommandsEnabled = true,
                        fileBrowsing = false,
                        uploads = false,
                        wifiProvisioning = true,
                        pipelines = false
                    )
                    scheduleAutomaticIpFallback()
                } else if (
                    state is ConnectionState.Disconnected ||
                    state is ConnectionState.Error
                ) {
                    if (transportCoordinator.currentTransport()?.type == TransportType.BLE) {
                        transportCoordinator.disconnect()
                        clearReachableDeviceState()
                    }
                }
            }
        }
        
        scope.launch {
            wifiDirectProbeSignals(
                wifiDirectManager.state,
                _connectingLanDeviceId
            ).collectLatest { (connected, host, lanConnectionPending) ->
                    if (connected && host != null) {
                        if (!allowsWifiDirectApiProbe(
                                transportCoordinator.state.value,
                                lanConnectionPending = lanConnectionPending
                            )
                        ) {
                            if (
                                (transportCoordinator.state.value as? TransportState.Connected)
                                    ?.type == TransportType.LAN
                            ) {
                                pendingWifiDirectTargetDeviceId = null
                                wifiDirectManager.cancelConnect()
                            }
                            return@collectLatest
                        }
                        val expectedDeviceId = pendingWifiDirectTargetDeviceId
                            ?: automaticTargetDeviceId(
                                preferredAutomaticDeviceId.value,
                                registeredDevices.value
                            )
                        if (
                            automaticConnectivityEnabled.value &&
                            !qrPairingActive.value &&
                            expectedDeviceId != null
                        ) {
                            pendingWifiDirectTargetDeviceId = expectedDeviceId
                            probeWifiDirectApi(host, expectedDeviceId)
                        } else {
                            pendingWifiDirectTargetDeviceId = null
                            wifiDirectManager.disconnect()
                        }
                    } else if (
                        transportCoordinator.currentTransport()?.type ==
                            TransportType.WIFI_DIRECT
                    ) {
                        markIpTransportOffline("Wi-Fi 연결이 끊어졌습니다.")
                    }
                }
        }

        scope.launch {
            val automaticDeviceId = combine(
                registeredDevices,
                preferredAutomaticDeviceId
            ) { registered, preferred ->
                automaticTargetDeviceId(preferred, registered)
            }
            val automaticLanAllowed = combine(
                autoLanEnabled,
                qrPairingActive
            ) { enabled, pairing -> enabled && !pairing }
            combine(
                lanDiscoveryManager.discoveredEndpoints,
                automaticDeviceId,
                transportCoordinator.state,
                automaticLanAllowed,
                wifiAccessPointScanner.state
            ) { endpoints, targetDeviceId, transport, enabled, wifiState ->
                if (
                    !enabled || targetDeviceId == null ||
                    !allowsAutomaticLanUpgrade(transport) ||
                    wifiState.currentSsid.isNullOrBlank() ||
                    _connectingLanDeviceId.value != null
                ) {
                    return@combine null
                }
                val connected = transport as? TransportState.Connected
                if (
                    connected?.type == TransportType.WIFI_DIRECT &&
                    !connected.deviceId.equals(targetDeviceId, ignoreCase = true)
                ) {
                    return@combine null
                }
                endpoints.firstOrNull { endpoint ->
                    endpoint.deviceId.equals(targetDeviceId, ignoreCase = true)
                }
            }.collect { endpoint ->
                endpoint ?: return@collect
                val attemptKey = automaticLanAttemptKey(endpoint)
                if (autoLanAttempts.add(attemptKey)) {
                    connectLan(
                        endpoint,
                        requireSameWifi = true,
                        automaticAttemptKey = attemptKey
                    )
                }
            }
        }

        scope.launch {
            val automaticDeviceId = combine(
                registeredDevices,
                preferredAutomaticDeviceId
            ) { registered, preferred ->
                automaticTargetDeviceId(preferred, registered)
            }
            val automaticDirectAllowed = combine(
                automaticConnectivityEnabled,
                automaticDirectFallbackReady,
                qrPairingActive
            ) { enabled, fallbackReady, pairing ->
                enabled && fallbackReady && !pairing
            }
            combine(
                wifiDirectManager.state,
                automaticDeviceId,
                transportCoordinator.state,
                automaticDirectAllowed
            ) { direct, targetDeviceId, transport, enabled ->
                if (
                    !enabled || targetDeviceId == null ||
                    !allowsAutomaticDirectFallback(transport) ||
                    direct.connected || direct.connectingPeerAddress != null
                ) {
                    return@combine null
                }
                chooseAutomaticWifiDirectPeer(direct.peers, targetDeviceId)
            }.collect { peer ->
                peer ?: return@collect
                scanner.stopScan()
                pendingWifiDirectTargetDeviceId = automaticTargetDeviceId(
                    preferredAutomaticDeviceId.value,
                    registeredDevices.value
                ) ?: return@collect
                wifiDirectManager.connect(peer)
            }
        }

        scope.launch {
            transportCoordinator.state.collectLatest { state ->
                if (
                    state is TransportState.Connected &&
                    state.type != TransportType.BLE
                ) {
                    while (true) {
                        delay(IP_HEARTBEAT_INTERVAL_MILLIS)
                        refreshStatus()
                    }
                }
            }
        }
    }

    private suspend fun probeWifiDirectApi(host: String, expectedDeviceId: String) {
        val generation = ipConnectionGeneration.incrementAndGet()
        var lastErrorMessage = "Jetson API가 응답하지 않습니다."

        repeat(WIFI_DIRECT_API_MAX_ATTEMPTS) { attempt ->
            if (!wifiDirectAttemptIsCurrent(generation, host, expectedDeviceId)) {
                return
            }
            wifiDirectManager.markApiChecking()
            val result = probeWifiDirectApiOnce(host, expectedDeviceId)
            if (!wifiDirectAttemptIsCurrent(generation, host, expectedDeviceId)) {
                return
            }
            if (result.isSuccess) {
                val probe = result.getOrThrow()
                updateStatus(probe.status)
                _capabilities.value = probe.capabilities.toModel()
                wifiDirectManager.markApiReady(probe.hello.deviceName)
                preferredAutomaticDeviceId.value = probe.hello.deviceId
                consecutiveIpStatusFailures.set(0)
                automaticDirectFallbackReady.value = false
                autoLanAttempts.clear()
                autoLanFailureCounts.clear()
                _lanConnectionError.value = null
                activeIpClient = probe.client
                transportCoordinator.setActiveTransport(
                    transport = IpControlTransport(
                        probe.client,
                        TransportType.WIFI_DIRECT
                    ),
                    endpoint = "$host:$LOCAL_API_PORT",
                    deviceId = probe.hello.deviceId,
                    deviceName = probe.hello.deviceName
                )
                gattClient.disconnect()
                return
            }

            lastErrorMessage = result.exceptionOrNull()?.message
                ?: "Jetson API가 응답하지 않습니다."
            wifiDirectManager.markApiError(lastErrorMessage)
            if (attempt + 1 < WIFI_DIRECT_API_MAX_ATTEMPTS) {
                delay(WIFI_DIRECT_API_RETRY_DELAY_MILLIS * (attempt + 1L))
            }
        }

        if (wifiDirectAttemptIsCurrent(generation, host, expectedDeviceId)) {
            reportWifiDirectApiError(lastErrorMessage)
            pendingWifiDirectTargetDeviceId = null
            wifiDirectManager.disconnect()
            scheduleAutomaticIpFallback()
        }
    }

    private suspend fun probeWifiDirectApiOnce(
        host: String,
        expectedDeviceId: String
    ): Result<WifiDirectApiProbe> = runCatching {
        val candidateClient = LocalApiClient(credentialStore)
        candidateClient.updateEndpoint(host, LOCAL_API_PORT)
        val hello = candidateClient.hello().getOrThrow()
        require(hello.deviceId.equals(expectedDeviceId, ignoreCase = true)) {
            "선택한 장비와 Wi-Fi Direct API 장비 ID가 일치하지 않습니다."
        }
        require(credentialStore.getSecret(hello.deviceId) != null) {
            "API 장비가 등록되어 있지 않습니다. 먼저 QR로 장비를 등록해 주세요."
        }
        val status = candidateClient.getStatus().getOrThrow()
        val capabilities = candidateClient.getCapabilities().getOrThrow()
        if (capabilities.mobileTimeSync) {
            candidateClient.synchronizeSystemTime(System.currentTimeMillis())
        }
        WifiDirectApiProbe(candidateClient, hello, status, capabilities)
    }

    private fun wifiDirectAttemptIsCurrent(
        generation: Long,
        host: String,
        expectedDeviceId: String
    ): Boolean {
        val direct = wifiDirectManager.state.value
        return ipConnectionGeneration.get() == generation &&
            automaticConnectivityEnabled.value &&
            !qrPairingActive.value &&
            allowsWifiDirectApiProbe(
                transportCoordinator.state.value,
                lanConnectionPending = _connectingLanDeviceId.value != null
            ) &&
            pendingWifiDirectTargetDeviceId.equals(expectedDeviceId, ignoreCase = true) &&
            direct.connected && direct.groupOwnerAddress == host
    }

    private fun reportWifiDirectApiError(message: String) {
        wifiDirectManager.markApiError(message)
        val activeType = transportCoordinator.currentTransport()?.type
        if (activeType == null || activeType == TransportType.WIFI_DIRECT) {
            transportCoordinator.setError(TransportType.WIFI_DIRECT, message)
        }
    }

    fun startScan(jetsonOnly: Boolean = false) {

        scanner.startScan(
            durationMillis = 15_000L,
            jetsonOnly = jetsonOnly
        )
    }


    fun stopScan() {

        scanner.stopScan()
    }


    fun connect(
        device: JetsonDevice
    ) {
        explicitDisconnectRequested.set(false)
        automaticConnectivityEnabled.value = true
        scanner.stopScan()

        gattClient.connect(
            device = device.device,
            displayName = device.name
        )
    }

    fun reconnectRegistered(device: JetsonDevice, expectedDeviceId: String) {
        activateAutomaticTarget(expectedDeviceId, scheduleFallback = false)
        scanner.stopScan()
        gattClient.connect(
            device = device.device,
            displayName = device.name,
            expectedDeviceId = expectedDeviceId
        )
    }

    fun connectRegisteredAutomatically(deviceId: String) {
        val registered = registeredDevices.value.firstOrNull {
            it.deviceId.equals(deviceId, ignoreCase = true)
        } ?: return
        activateAutomaticTarget(registered.deviceId, scheduleFallback = true)
    }

    suspend fun forgetRegisteredDevice(deviceId: String) {
        credentialStore.removeCredential(deviceId)
    }


    fun disconnect() {
        explicitDisconnectRequested.set(true)
        automaticConnectivityEnabled.value = false
        automaticDirectFallbackReady.value = false
        automaticDirectFallbackJob?.cancel()
        automaticDirectFallbackJob = null
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
        _visibleConnectingLanDeviceId.value = null
        pendingWifiDirectTargetDeviceId = null
        scanner.stopScan()
        stopLanDiscovery()
        gattClient.disconnect()
        wifiDirectManager.cancelConnect()
        transportCoordinator.disconnect()
        activeIpClient = null
        clearReachableDeviceState()
        _controlOperation.value = ControlOperationState()
    }

    private fun activateAutomaticTarget(deviceId: String, scheduleFallback: Boolean) {
        explicitDisconnectRequested.set(false)
        val current = transportCoordinator.state.value as? TransportState.Connected
        val changingPreferred = preferredAutomaticDeviceId.value?.let {
            !it.equals(deviceId, ignoreCase = true)
        } == true
        val changingLanAttempt = _connectingLanDeviceId.value?.let {
            !it.equals(deviceId, ignoreCase = true)
        } == true
        val changingDirectAttempt = pendingWifiDirectTargetDeviceId?.let {
            !it.equals(deviceId, ignoreCase = true)
        } == true
        if (
            (current != null && !current.deviceId.equals(deviceId, ignoreCase = true)) ||
            changingPreferred || changingLanAttempt || changingDirectAttempt
        ) {
            disconnectActiveTransportForTargetSwitch()
        }
        preferredAutomaticDeviceId.value = deviceId
        automaticConnectivityEnabled.value = true
        ensureAutomaticBleReconnectLoop()
        if (scheduleFallback) {
            scheduleAutomaticIpFallback()
        }
    }

    private fun disconnectActiveTransportForTargetSwitch() {
        automaticDirectFallbackReady.value = false
        automaticDirectFallbackJob?.cancel()
        automaticDirectFallbackJob = null
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
        _visibleConnectingLanDeviceId.value = null
        pendingWifiDirectTargetDeviceId = null
        activeIpClient = null
        gattClient.disconnect()
        wifiDirectManager.cancelConnect()
        transportCoordinator.disconnect()
        clearReachableDeviceState()
    }


    fun sendCommand(
        command: JetsonCommand,
        payload: ByteArray = byteArrayOf()
    ): Boolean {
        val transport = transportCoordinator.currentTransport()
            ?: run {
                _controlOperation.value = ControlOperationState(
                    message = "Jetson 연결을 먼저 확인해 주세요.",
                    isError = true
                )
                return false
            }

        val operationName = command.displayName()
        _controlOperation.value = ControlOperationState(
            inProgress = true,
            message = "$operationName 요청을 전송하고 있습니다."
        )

        if (transport.type == TransportType.BLE) {
            val accepted = gattClient.writeCommand(
                CommandCodec.encode(command, payload)
            )
            _controlOperation.value = ControlOperationState(
                message = if (accepted) {
                    "$operationName 요청을 Jetson에 전송했습니다."
                } else {
                    "$operationName 요청을 전송하지 못했습니다."
                },
                isError = !accepted
            )
            return accepted
        }

        scope.launch {
            transport.sendCommand(command, payload)
                .onSuccess {
                    _controlOperation.value = ControlOperationState(
                        message = "$operationName 요청이 처리되었습니다."
                    )
                }
                .onFailure { error ->
                    _controlOperation.value = ControlOperationState(
                        message = error.message ?: "$operationName 요청에 실패했습니다.",
                        isError = true
                    )
                }
        }
        return true
    }


    fun requestStatus(): Boolean {

        return sendCommand(
            JetsonCommand.GET_STATUS
        )
    }


    suspend fun refreshStatus(): Boolean {
        val transport = transportCoordinator.currentTransport()
            ?: return false

        return when (transport.type) {
            TransportType.BLE -> gattClient.writeCommand(
                CommandCodec.encode(JetsonCommand.GET_STATUS)
            )
            TransportType.WIFI_DIRECT,
            TransportType.LAN -> {
                val result = transport.getStatus()
                result.onSuccess {
                    consecutiveIpStatusFailures.set(0)
                    updateStatus(it)
                }.onFailure { error ->
                    val failures = consecutiveIpStatusFailures.incrementAndGet()
                    if (
                        ipConnectionIsOffline(failures) &&
                        transportCoordinator.currentTransport() === transport
                    ) {
                        markIpTransportOffline(error.message)
                    }
                }
                result.isSuccess
            }
        }
    }


    suspend fun provisionWifi(
        request: WifiProvisionRequest
    ): Result<Unit> {
        val transport = transportCoordinator.currentTransport()
            ?: return Result.failure(
                IllegalStateException("Jetson 연결을 먼저 확인해 주세요.")
            )

        return if (transport.type == TransportType.BLE) {
            runCatching {
                val payload = gattClient.encodeWifiProvision(request)
                check(
                    gattClient.writeCommand(
                        CommandCodec.encode(JetsonCommand.SET_WIFI, payload)
                    )
                ) {
                    "Jetson에 Wi-Fi 설정을 전송하지 못했습니다. Bluetooth 연결을 확인하세요."
                }
            }
        } else {
            val client = activeIpClient
                ?: return Result.failure(
                    IllegalStateException("IP 제어 연결을 다시 확인해 주세요.")
                )
            client.configureWifi(request).map { Unit }
        }
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

    fun startPairing(info: PairingInfo): Boolean {
        explicitDisconnectRequested.set(false)
        automaticConnectivityEnabled.value = true
        preferredAutomaticDeviceId.value = info.deviceId
        ensureAutomaticBleReconnectLoop()
        if (gattClient.authenticateConnectedDevice(info)) {
            return true
        }

        scanner.stopScan()
        // Clear a previous Ready/Error state before starting a new discovery attempt.
        // RegistrationRequired is handled above so its verified GATT connection is kept.
        gattClient.disconnect()
        scanner.startScan(jetsonOnly = true)
        return false
    }

    /**
     * Starts a clean QR pairing session without discarding a GATT connection that is
     * already waiting for the QR credential of the same physical device.
     */
    fun prepareForQrPairing() {
        explicitDisconnectRequested.set(false)
        automaticConnectivityEnabled.value = true
        qrPairingActive.value = true
        automaticDirectFallbackReady.value = false
        automaticDirectFallbackJob?.cancel()
        automaticDirectFallbackJob = null
        automaticBleReconnectJob?.cancel()
        automaticBleReconnectJob = null
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
        _visibleConnectingLanDeviceId.value = null
        pendingWifiDirectTargetDeviceId = null
        activeIpClient = null
        transportCoordinator.disconnect()
        stopLanDiscovery()
        wifiDirectManager.cancelConnect()
        clearReachableDeviceState()
        scanner.stopScan()
        if (gattClient.connectionState.value !is ConnectionState.RegistrationRequired) {
            gattClient.disconnect()
        }
    }

    fun connectForPairing(device: JetsonDevice, info: PairingInfo) {
        explicitDisconnectRequested.set(false)
        automaticConnectivityEnabled.value = true
        preferredAutomaticDeviceId.value = info.deviceId
        scanner.stopScan()
        gattClient.connectForPairing(
            device = device.device,
            displayName =
                canonicalPairingDisplayName(
                    pairingInfo = info,
                    advertisedDisplayName = device.name
                ),
            pairingInfo = info
        )
    }

    fun cancelPairing() {
        qrPairingActive.value = false
        scanner.stopScan()
        gattClient.disconnect()
        ensureAutomaticBleReconnectLoop()
        scope.launch {
            val preferred = preferredAutomaticDeviceId.value
            if (
                preferred != null &&
                credentialStore.getSecret(preferred) == null &&
                preferredAutomaticDeviceId.value.equals(preferred, ignoreCase = true)
            ) {
                preferredAutomaticDeviceId.value = null
            }
            scheduleAutomaticIpFallback()
        }
    }

    fun configureAutomaticConnectivity(
        enabled: Boolean,
        localNetworkPermissionGranted: Boolean,
        nearbyWifiPermissionGranted: Boolean,
        bluetoothPermissionGranted: Boolean
    ) {
        val effectiveEnabled = enabled && !explicitDisconnectRequested.get()
        automaticConnectivityEnabled.value = effectiveEnabled
        this.localNetworkPermissionGranted.value = localNetworkPermissionGranted
        this.nearbyWifiPermissionGranted.value = nearbyWifiPermissionGranted
        this.bluetoothPermissionGranted.value = bluetoothPermissionGranted

        if (effectiveEnabled && localNetworkPermissionGranted && !qrPairingActive.value) {
            startLanDiscovery()
        } else {
            stopLanDiscovery()
        }
        if (effectiveEnabled && !qrPairingActive.value) {
            ensureAutomaticBleReconnectLoop()
            scheduleAutomaticIpFallback()
        } else {
            automaticDirectFallbackReady.value = false
            automaticDirectFallbackJob?.cancel()
            wifiDirectManager.stopDiscovery()
            if (!qrPairingActive.value) {
                scanner.stopScan()
            }
        }
    }

    fun startWifiDirectDiscovery() {
        explicitDisconnectRequested.set(false)
        automaticConnectivityEnabled.value = true
        automaticDirectFallbackReady.value = true
        wifiDirectManager.startDiscovery()
    }

    fun stopWifiDirectDiscovery() {
        wifiDirectManager.stopDiscovery()
    }

    fun connectWifiDirect(peer: WifiDirectPeer) {
        explicitDisconnectRequested.set(false)
        automaticConnectivityEnabled.value = true
        pendingWifiDirectTargetDeviceId = registeredDevices.value
            .filter { device ->
                peer.name.equals(device.deviceName, ignoreCase = true) ||
                    peer.name.equals(canonicalBleNameForDeviceId(device.deviceId), ignoreCase = true) ||
                    peer.name.equals(legacyBleNameForDeviceId(device.deviceId), ignoreCase = true)
            }
            .singleOrNull()
            ?.deviceId
            ?: automaticTargetDeviceId(
                preferredAutomaticDeviceId.value,
                registeredDevices.value
            )
        scanner.stopScan()
        wifiDirectManager.connect(peer)
    }

    fun retryWifiDirectApi() {
        val host = wifiDirectManager.state.value.groupOwnerAddress
            ?: return
        val expectedDeviceId = pendingWifiDirectTargetDeviceId
            ?: automaticTargetDeviceId(
                preferredAutomaticDeviceId.value,
                registeredDevices.value
            )
            ?: return
        pendingWifiDirectTargetDeviceId = expectedDeviceId
        scope.launch {
            probeWifiDirectApi(host, expectedDeviceId)
        }
    }

    fun startWifiAccessPointScan() {
        wifiAccessPointScanner.startScan()
    }

    fun stopWifiAccessPointScan() {
        wifiAccessPointScanner.stop()
    }

    fun startLanDiscovery() {
        _lanConnectionError.value = null
        autoLanAttempts.clear()
        autoLanFailureCounts.clear()
        autoLanEnabled.value = true
        wifiAccessPointScanner.refreshCurrentConnection()
        lanDiscoveryManager.startDiscovery()
    }

    fun stopLanDiscovery() {
        autoLanEnabled.value = false
        lanDiscoveryManager.stopDiscovery()
    }

    fun connectLan(endpoint: DeviceEndpoint) {
        activateAutomaticTarget(endpoint.deviceId, scheduleFallback = false)
        connectLan(endpoint, requireSameWifi = false)
    }

    private fun connectLan(
        endpoint: DeviceEndpoint,
        requireSameWifi: Boolean,
        automaticAttemptKey: String? = null
    ) {
        val userVisibleAttempt = automaticAttemptKey == null
        val generation = ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = generation
        _connectingLanDeviceId.value = endpoint.deviceId
        if (userVisibleAttempt) {
            _visibleConnectingLanDeviceId.value = endpoint.deviceId
            _lanConnectionError.value = null
        }

        fun publishUserVisibleError(message: String) {
            if (userVisibleAttempt) {
                _lanConnectionError.value = message
            }
        }

        scope.launch {
            var connectedSuccessfully = false
            try {
                val candidateClient = LocalApiClient(credentialStore)
                candidateClient.updateEndpoint(endpoint.host, endpoint.port)
                candidateClient.hello()
                .onSuccess { hello ->
                    if (ipConnectionGeneration.get() != generation) {
                        return@onSuccess
                    }
                    if (!hello.deviceId.equals(endpoint.deviceId, ignoreCase = true)) {
                        publishUserVisibleError(
                            "검색된 장비 ID와 API 장비 ID가 일치하지 않습니다."
                        )
                        return@onSuccess
                    }

                    if (credentialStore.getSecret(hello.deviceId) == null) {
                        publishUserVisibleError(
                            "이 장비는 앱에 등록되어 있지 않습니다. 먼저 BLE/QR 등록을 완료해 주세요."
                        )
                        return@onSuccess
                    }

                    val statusResult = candidateClient.getStatus()
                    if (ipConnectionGeneration.get() != generation) {
                        return@onSuccess
                    }
                    if (statusResult.isFailure) {
                        publishUserVisibleError(
                            statusResult.exceptionOrNull()?.message
                                ?: "Jetson API 인증에 실패했습니다."
                        )
                        return@onSuccess
                    }

                    val status = statusResult.getOrThrow()
                    if (
                        requireSameWifi && !wifiNetworksMatch(
                            wifiAccessPointScanner.state.value.currentSsid,
                            status.wifiConnected,
                            status.wifiSsid
                        )
                    ) {
                        publishUserVisibleError(
                            "모바일과 Jetson의 Wi-Fi가 같지 않아 자동 LAN 연결을 건너뛰었습니다."
                        )
                        return@onSuccess
                    }
                    updateStatus(status)
                    val capabilitiesResult = candidateClient.getCapabilities()
                    if (ipConnectionGeneration.get() != generation) {
                        return@onSuccess
                    }
                    if (capabilitiesResult.isFailure) {
                        publishUserVisibleError(
                            capabilitiesResult.exceptionOrNull()?.message
                                ?: "Jetson 기능 정보를 확인하지 못했습니다."
                        )
                        return@onSuccess
                    }
                    val capabilities = capabilitiesResult.getOrThrow()
                    _capabilities.value = capabilities.toModel()
                    if (capabilities.mobileTimeSync) {
                        candidateClient.synchronizeSystemTime(System.currentTimeMillis())
                        if (ipConnectionGeneration.get() != generation) {
                            return@onSuccess
                        }
                    }

                    preferredAutomaticDeviceId.value = hello.deviceId
                    consecutiveIpStatusFailures.set(0)
                    automaticDirectFallbackReady.value = false
                    automaticDirectFallbackJob?.cancel()
                    automaticDirectFallbackJob = null
                    _lanConnectionError.value = null
                    activeIpClient = candidateClient
                    transportCoordinator.setActiveTransport(
                        transport = IpControlTransport(
                            candidateClient,
                            TransportType.LAN
                        ),
                        endpoint = "${endpoint.host}:${endpoint.port}",
                        deviceId = hello.deviceId,
                        deviceName = hello.deviceName
                    )
                    connectedSuccessfully = true
                    automaticAttemptKey?.let {
                        autoLanAttempts.remove(it)
                        autoLanFailureCounts.remove(it)
                    }
                    gattClient.disconnect()
                    pendingWifiDirectTargetDeviceId = null
                    wifiDirectManager.cancelConnect()
                }
                .onFailure { error ->
                    if (ipConnectionGeneration.get() == generation) {
                        publishUserVisibleError(
                            "${endpoint.displayName} API 연결 실패: " +
                                (error.message ?: "응답 없음")
                        )
                    }
                }
            } finally {
                if (connectingLanGeneration == generation) {
                    connectingLanGeneration = null
                    _connectingLanDeviceId.value = null
                    _visibleConnectingLanDeviceId.value = null
                }
                if (
                    automaticAttemptKey != null &&
                    !connectedSuccessfully &&
                    ipConnectionGeneration.get() == generation
                ) {
                    scheduleAutomaticLanRetry(endpoint, automaticAttemptKey)
                }
            }
        }
    }

    private fun scheduleAutomaticLanRetry(endpoint: DeviceEndpoint, attemptKey: String) {
        val failures = autoLanFailureCounts.merge(attemptKey, 1) { previous, increment ->
            (previous + increment).coerceAtMost(AUTOMATIC_LAN_RETRY_EXPONENT_LIMIT)
        } ?: 1
        val retryDelay = (AUTOMATIC_LAN_RETRY_BASE_MILLIS shl (failures - 1))
            .coerceAtMost(AUTOMATIC_LAN_RETRY_MAX_MILLIS)
        scope.launch {
            delay(retryDelay)
            while (
                wifiDirectManager.state.value.connected &&
                pendingWifiDirectTargetDeviceId != null &&
                (transportCoordinator.state.value as? TransportState.Connected)?.type !=
                    TransportType.WIFI_DIRECT
            ) {
                delay(AUTOMATIC_LAN_RETRY_DIRECT_PROBE_WAIT_MILLIS)
            }
            val targetDeviceId = automaticTargetDeviceId(
                preferredAutomaticDeviceId.value,
                registeredDevices.value
            )
            val endpointStillPresent = lanDiscoveryManager.discoveredEndpoints.value.any {
                it.deviceId.equals(endpoint.deviceId, ignoreCase = true) &&
                    it.host == endpoint.host && it.port == endpoint.port
            }
            if (
                automaticConnectivityEnabled.value &&
                !qrPairingActive.value &&
                targetDeviceId.equals(endpoint.deviceId, ignoreCase = true) &&
                endpointStillPresent &&
                _connectingLanDeviceId.value == null &&
                allowsAutomaticLanUpgrade(transportCoordinator.state.value)
            ) {
                autoLanAttempts.remove(attemptKey)
                if (autoLanAttempts.add(attemptKey)) {
                    connectLan(
                        endpoint,
                        requireSameWifi = true,
                        automaticAttemptKey = attemptKey
                    )
                }
            } else {
                // A disappeared endpoint or target change must not leave a permanent
                // suppression key; a later NSD/state emission may safely try again.
                if (_connectingLanDeviceId.value == null) {
                    autoLanAttempts.remove(attemptKey)
                }
            }
        }
    }

    private fun automaticLanAttemptKey(endpoint: DeviceEndpoint): String =
        "${endpoint.deviceId}@${endpoint.host}:${endpoint.port}"

    suspend fun getRoots(): Result<List<RemoteRoot>> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getRoots()
    }

    suspend fun getCameraPreviewFrame(): Result<ByteArray> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getCameraPreviewFrame()
    }

    private fun updateStatus(status: JetsonStatus) {
        _status.value = status
        _statusUpdatedAtEpochMillis.value = System.currentTimeMillis()
    }

    private fun clearReachableDeviceState() {
        _status.value = JetsonStatus()
        _statusUpdatedAtEpochMillis.value = null
        _capabilities.value = ControlCapabilities()
        consecutiveIpStatusFailures.set(0)
    }

    private fun markIpTransportOffline(message: String?) {
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
        _visibleConnectingLanDeviceId.value = null
        activeIpClient = null
        transportCoordinator.disconnect()
        clearReachableDeviceState()
        _lanConnectionError.value = message?.takeIf { it.isNotBlank() }
            ?.let { "Jetson 응답이 없어 오프라인으로 전환했습니다." }
        pendingWifiDirectTargetDeviceId = null
        wifiDirectManager.cancelConnect()
        if (
            automaticConnectivityEnabled.value &&
            !qrPairingActive.value &&
            localNetworkPermissionGranted.value
        ) {
            startLanDiscovery()
        }
        scheduleAutomaticIpFallback()
    }

    private fun scheduleAutomaticIpFallback() {
        automaticDirectFallbackJob?.cancel()
        automaticDirectFallbackReady.value = false
        if (!automaticConnectivityEnabled.value || qrPairingActive.value) {
            return
        }
        automaticDirectFallbackJob = scope.launch {
            if (localNetworkPermissionGranted.value) {
                startLanDiscovery()
                delay(AUTOMATIC_LAN_GRACE_MILLIS)
            } else {
                delay(AUTOMATIC_DIRECT_START_DELAY_MILLIS)
            }
            if (
                automaticConnectivityEnabled.value &&
                !qrPairingActive.value &&
                nearbyWifiPermissionGranted.value &&
                !wifiDirectManager.state.value.connected &&
                allowsAutomaticDirectFallback(transportCoordinator.state.value) &&
                automaticTargetDeviceId(
                    preferredAutomaticDeviceId.value,
                    registeredDevices.value
                ) != null
            ) {
                automaticDirectFallbackReady.value = true
                wifiDirectManager.startDiscovery()
            }
        }
    }

    private fun ensureAutomaticBleReconnectLoop() {
        if (automaticBleReconnectJob?.isActive == true) {
            return
        }
        automaticBleReconnectJob = scope.launch {
            while (automaticConnectivityEnabled.value) {
                val targetDeviceId = automaticTargetDeviceId(
                    preferredAutomaticDeviceId.value,
                    registeredDevices.value
                )
                val transport = transportCoordinator.state.value
                val connection = gattClient.connectionState.value
                if (
                    bluetoothPermissionGranted.value &&
                    !qrPairingActive.value && targetDeviceId != null &&
                    transport !is TransportState.Connected &&
                    _connectingLanDeviceId.value == null &&
                    (connection is ConnectionState.Disconnected || connection is ConnectionState.Error)
                ) {
                    val storedName = registeredDevices.value.firstOrNull {
                        it.deviceId.equals(targetDeviceId, ignoreCase = true)
                    }?.deviceName?.lowercase()
                    val expectedNames = setOf(
                        canonicalBleNameForDeviceId(targetDeviceId).lowercase(),
                        legacyBleNameForDeviceId(targetDeviceId).lowercase(),
                        storedName
                    ).filterNotNull().toSet()
                    val candidates = scanner.devices.value.filter { candidate ->
                        candidate.name?.lowercase() in expectedNames
                    }
                    if (candidates.size == 1) {
                        reconnectRegistered(candidates.single(), targetDeviceId)
                    } else if (!scanner.isScanning.value) {
                        scanner.startScan(durationMillis = 15_000L, jetsonOnly = true)
                    }
                }
                delay(AUTOMATIC_BLE_RECONNECT_INTERVAL_MILLIS)
            }
        }
    }

    suspend fun listDirectory(rootId: String, relativePath: String): Result<LocalControlApi.ListFilesResponse> {
        val client = activeIpClient ?: return missingIpConnection()
        return if (rootId == WORKSPACE_ROOT_ID) {
            client.listWorkspaceFiles(rootId, relativePath)
        } else {
            client.listFiles(rootId, relativePath)
        }
    }

    suspend fun getFile(rootId: String, relativePath: String): Result<RemoteFileContent> {
        val client = activeIpClient ?: return missingIpConnection()
        return if (rootId == WORKSPACE_ROOT_ID) {
            client.getWorkspaceFile(rootId, relativePath)
        } else {
            client.getFile(rootId, relativePath)
        }
    }

    suspend fun deleteStorageEntry(
        rootId: String,
        relativePath: String
    ): Result<DeviceStorageDeletion> {
        if (rootId == WORKSPACE_ROOT_ID) {
            return Result.failure(
                IllegalArgumentException("작업공간 데이터는 이 화면에서 삭제할 수 없습니다.")
            )
        }
        val client = activeIpClient ?: return missingIpConnection()
        return client.deleteStorageEntry(rootId, relativePath)
    }

    suspend fun getWorkspaceRoots(): Result<List<RemoteRoot>> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getWorkspaceRoots()
    }

    suspend fun listWorkspaceDirectory(
        rootId: String,
        relativePath: String
    ): Result<LocalControlApi.ListFilesResponse> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.listWorkspaceFiles(rootId, relativePath)
    }

    suspend fun getUploadTargets(): Result<List<UploadTarget>> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getUploadTargets()
    }

    suspend fun getUploadLibrarySessions(
        targetId: String,
        offset: Int = 0
    ): Result<UploadLibrarySessionsResponse> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getUploadLibrarySessions(targetId, offset)
    }

    suspend fun getUploadLibraryFiles(
        targetId: String,
        sessionId: String,
        path: String
    ): Result<UploadLibraryFilesResponse> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getUploadLibraryFiles(targetId, sessionId, path)
    }

    suspend fun getUploadLibraryFile(
        targetId: String,
        sessionId: String,
        path: String
    ): Result<RemoteFileContent> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getUploadLibraryFile(targetId, sessionId, path)
    }

    suspend fun deleteUploadLibrarySession(
        targetId: String,
        sessionId: String
    ): Result<UploadDeletionResponse> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.deleteUploadLibrarySession(targetId, sessionId)
    }

    suspend fun getUploadSourceSummary(
        rootId: String,
        relativePath: String
    ): Result<UploadSourceSummary> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getUploadSourceSummary(rootId, relativePath)
    }

    suspend fun saveUploadTarget(
        targetId: String,
        label: String,
        baseUrl: String,
        token: String?
    ): Result<UploadTarget> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.saveUploadTarget(targetId, label, baseUrl, token)
    }

    suspend fun deleteUploadTarget(targetId: String): Result<Unit> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.deleteUploadTarget(targetId)
    }

    suspend fun startUpload(rootId: String, relativePath: String, targetId: String): Result<UploadJob> {
        val transportType = transportCoordinator.currentTransport()?.type
        if (!canStartServerUpload(transportType)) {
            return Result.failure(
                IllegalStateException(serverUploadUnavailableMessage(transportType))
            )
        }
        val client = activeIpClient ?: return missingIpConnection()
        return client.startUpload(rootId, relativePath, targetId)
    }

    suspend fun getUploadJobs(activeOnly: Boolean = false): Result<List<UploadJob>> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getUploadJobs(activeOnly)
    }

    suspend fun getUploadJob(jobId: String): Result<UploadJob> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getUploadJob(jobId)
    }

    suspend fun deleteUploadJob(jobId: String): Result<Unit> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.deleteUploadJob(jobId)
    }

    suspend fun cancelUpload(jobId: String): Result<UploadJob> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.cancelUpload(jobId)
    }

    suspend fun retryUpload(jobId: String): Result<UploadJob> {
        val transportType = transportCoordinator.currentTransport()?.type
        if (!canStartServerUpload(transportType)) {
            return Result.failure(
                IllegalStateException(serverUploadUnavailableMessage(transportType))
            )
        }
        val client = activeIpClient ?: return missingIpConnection()
        return client.retryUpload(jobId)
    }

    suspend fun verifyUploadSource(jobId: String): Result<UploadVerification> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.verifyUploadSource(jobId)
    }

    suspend fun deleteUploadSource(jobId: String): Result<UploadJob> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.deleteUploadSource(jobId)
    }

    suspend fun getPipelines(): Result<List<ManagedPipeline>> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getPipelines()
    }

    suspend fun discoverPipelineFolder(
        rootId: String,
        path: String
    ): Result<PipelineFolderDiscovery> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.discoverPipelineFolder(rootId, path)
    }

    suspend fun registerPipelineFolder(
        rootId: String,
        path: String,
        name: String,
        autostart: Boolean
    ): Result<ManagedPipeline> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.registerPipelineFolder(rootId, path, name, autostart)
    }

    suspend fun registerPipeline(request: RegisterPipelineRequest): Result<ManagedPipeline> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.registerPipeline(request)
    }

    suspend fun controlPipeline(
        pipelineId: String,
        action: String
    ): Result<ManagedPipeline> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.controlPipeline(pipelineId, action)
    }

    suspend fun removePipeline(pipelineId: String): Result<Unit> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.removePipeline(pipelineId)
    }

    suspend fun getSystemTime(): Result<SystemTimeStatus> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getSystemTime()
    }

    suspend fun synchronizeSystemTime(
        mobileTimeEpochMillis: Long = System.currentTimeMillis()
    ): Result<SystemTimeStatus> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.synchronizeSystemTime(mobileTimeEpochMillis)
    }

    suspend fun getFanStatus(): Result<FanStatus> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getFanStatus()
    }

    suspend fun setFan(mode: String, percent: Int? = null): Result<FanStatus> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.setFan(mode, percent)
    }

    suspend fun getPipelineLogs(pipelineId: String): Result<PipelineLog> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getPipelineLogs(pipelineId)
    }

    suspend fun getPipelineLogFiles(pipelineId: String): Result<PipelineLogFilesResponse> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getPipelineLogFiles(pipelineId)
    }

    suspend fun getPipelineLogChunk(
        pipelineId: String,
        logId: String,
        offset: Long,
        limit: Int
    ): Result<PipelineLogChunk> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getPipelineLogChunk(pipelineId, logId, offset, limit)
    }

    suspend fun getPipelineConfig(pipelineId: String): Result<PipelineConfigDocument> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getPipelineConfig(pipelineId)
    }

    suspend fun updatePipelineConfig(
        pipelineId: String,
        content: String
    ): Result<PipelineConfigDocument> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.updatePipelineConfig(pipelineId, content)
    }

    suspend fun getPipelineConfigFields(
        pipelineId: String
    ): Result<PipelineConfigFieldsDocument> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getPipelineConfigFields(pipelineId)
    }

    suspend fun updatePipelineConfigFields(
        pipelineId: String,
        revision: String,
        values: Map<String, String>
    ): Result<PipelineConfigFieldsDocument> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.updatePipelineConfigFields(pipelineId, revision, values)
    }

    fun clearControlMessage() {
        _controlOperation.value = ControlOperationState()
    }

    private fun <T> missingIpConnection(): Result<T> = Result.failure(
        IllegalStateException("이 기능에는 LAN 또는 Wi-Fi Direct 연결이 필요합니다.")
    )

    private fun LocalControlApi.CapabilitiesResponse.toModel() =
        ControlCapabilities(
            systemControlConfigured = systemControlConfigured,
            powerCommandsEnabled = powerCommandsEnabled,
            fileBrowsing = fileBrowsing,
            uploads = uploads,
            wifiProvisioning = wifiProvisioning,
            pipelines = pipelines,
            pipelineFolderRegistration = pipelineFolderRegistration,
            mobileTimeSync = mobileTimeSync,
            fanControl = fanControl
        )

    private fun JetsonCommand.displayName(): String = when (this) {
        JetsonCommand.START_SYSTEM -> "시스템 시작"
        JetsonCommand.STOP_SYSTEM -> "시스템 중지"
        JetsonCommand.RESTART_SERVICES -> "서비스 재시작"
        JetsonCommand.REBOOT -> "재부팅"
        JetsonCommand.SHUTDOWN -> "종료"
        JetsonCommand.GET_STATUS -> "상태 갱신"
        JetsonCommand.SET_WIFI -> "Wi-Fi 설정"
    }
}

private const val AUTOMATIC_LAN_GRACE_MILLIS = 5_000L
private const val AUTOMATIC_LAN_RETRY_BASE_MILLIS = 1_500L
private const val AUTOMATIC_LAN_RETRY_MAX_MILLIS = 15_000L
private const val AUTOMATIC_LAN_RETRY_EXPONENT_LIMIT = 5
private const val AUTOMATIC_LAN_RETRY_DIRECT_PROBE_WAIT_MILLIS = 500L
private const val AUTOMATIC_DIRECT_START_DELAY_MILLIS = 750L
private const val AUTOMATIC_BLE_RECONNECT_INTERVAL_MILLIS = 5_000L
private const val IP_HEARTBEAT_INTERVAL_MILLIS = 1_000L
private const val WIFI_DIRECT_API_MAX_ATTEMPTS = 3
private const val WIFI_DIRECT_API_RETRY_DELAY_MILLIS = 750L
private const val WORKSPACE_ROOT_ID = "workspace-home"

@Suppress("UNUSED_PARAMETER")
internal fun canonicalPairingDisplayName(
    pairingInfo: PairingInfo,
    advertisedDisplayName: String
): String =
    pairingInfo.expectedBleName

internal fun wifiNetworksMatch(
    mobileSsid: String?,
    jetsonConnected: Boolean,
    jetsonSsid: String?
): Boolean = jetsonConnected && !mobileSsid.isNullOrBlank() &&
    !jetsonSsid.isNullOrBlank() && mobileSsid == jetsonSsid
