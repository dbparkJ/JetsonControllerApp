package com.example.jetsoncontroller.data.repository

import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val connectingLanDeviceId: StateFlow<String?> =
        _connectingLanDeviceId.asStateFlow()

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
    private var wifiDirectEntryJob: Job? = null
    private var wifiDirectEntryTimeoutJob: Job? = null
    private var wifiProvisionLanHandoffTimeoutJob: Job? = null

    private val wifiDirectEntryGeneration = AtomicLong(0)

    private data class PendingWifiDirectEntry(
        val targetDeviceId: String,
        val peer: WifiDirectPeer?,
        val automatic: Boolean,
        val generation: Long,
        val commandWriteFailures: Int = 0
    )

    @Volatile
    private var pendingWifiDirectEntry: PendingWifiDirectEntry? = null

    @Volatile
    private var wifiProvisionLanHandoffActive = false

    @Volatile
    private var wifiProvisionLanHandoffDeviceId: String? = null

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
                if (
                    shouldApplyBleStatus(
                        transportCoordinator.currentTransport()?.type
                    )
                ) {
                    updateStatus(currentStatus)
                }
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
                    if (currentType == TransportType.LAN) {
                        gattClient.disconnect()
                        return@collect
                    }
                    if (currentType == TransportType.WIFI_DIRECT) {
                        // Keep the authenticated BLE session as an out-of-band
                        // recovery channel while the P2P control API is active.
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
                    if (!startPendingWifiDirectEntryIfReady()) {
                        scheduleAutomaticIpFallback()
                    }
                } else if (
                    state is ConnectionState.Disconnected ||
                    state is ConnectionState.Error
                ) {
                    if (transportCoordinator.currentTransport()?.type == TransportType.BLE) {
                        transportCoordinator.disconnect()
                        clearReachableDeviceState()
                    }
                    refreshBleReconnectCandidatesAfterFailure()
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
                                wifiDirectManager.releaseForTransportHandoff()
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
                automaticDirectAllowed,
                _connectingLanDeviceId
            ) { direct, targetDeviceId, transport, enabled, connectingLanDeviceId ->
                if (
                    !enabled || targetDeviceId == null ||
                    !allowsAutomaticDirectAttempt(
                        transport,
                        lanConnectionPending = connectingLanDeviceId != null
                    ) ||
                    !direct.enabled ||
                    direct.connected || direct.connectingPeerAddress != null
                ) {
                    return@combine null
                }
                chooseAutomaticWifiDirectPeer(direct.peers, targetDeviceId)
            }.collect { peer ->
                peer ?: return@collect
                if (
                    !allowsAutomaticDirectAttempt(
                        transportCoordinator.state.value,
                        lanConnectionPending = _connectingLanDeviceId.value != null
                    )
                ) {
                    return@collect
                }
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
        require(localNetworkPermissionGranted.value) {
            "로컬 네트워크 권한이 필요합니다. 권한을 허용한 뒤 다시 시도해 주세요."
        }
        val candidateClient = LocalApiClient(credentialStore)
        candidateClient.updateEndpoint(
            host = host,
            port = LOCAL_API_PORT,
            socketFactory = wifiDirectManager.socketFactoryForGroupOwner(host)
        )
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
        cancelPendingWifiDirectEntry()
        cancelWifiProvisionLanHandoff(scheduleRecovery = false)
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
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
        cancelPendingWifiDirectEntry()
        cancelWifiProvisionLanHandoff(scheduleRecovery = false)
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
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
            TransportType.BLE -> gattClient.writeCommandAwait(
                CommandCodec.encode(JetsonCommand.GET_STATUS)
            ).isSuccess
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
    ): Result<WifiProvisionReceipt> {
        val transport = transportCoordinator.currentTransport()
            ?: return Result.failure(
                IllegalStateException("Jetson 연결을 먼저 확인해 주세요.")
            )

        return if (transport.type == TransportType.BLE) {
            val deviceId = gattClient.currentDeviceId()
                ?: return Result.failure(
                    IllegalStateException("Bluetooth 장비 ID를 확인할 수 없습니다.")
                )
            val payload = runCatching {
                gattClient.encodeWifiProvision(request)
            }.getOrElse { error ->
                return Result.failure(error)
            }
            beginWifiProvisionLanHandoff(deviceId)
            val writeResult = try {
                gattClient.writeCommandAwait(
                    CommandCodec.encode(JetsonCommand.SET_WIFI, payload)
                )
            } catch (error: CancellationException) {
                cancelWifiProvisionLanHandoff(scheduleRecovery = true)
                throw error
            }
            if (writeResult.isFailure) {
                cancelWifiProvisionLanHandoff(scheduleRecovery = true)
                return Result.failure(
                    writeResult.exceptionOrNull()
                        ?: IllegalStateException(
                            "Jetson에 Wi-Fi 설정을 전송하지 못했습니다."
                        )
                )
            }
            activateWifiProvisionLanHandoffTimeout()
            Result.success(
                WifiProvisionReceipt(
                    ssid = request.ssid,
                    statusPollingAvailable = false,
                    lanHandoffRequired = true,
                    deviceId = deviceId
                )
            )
        } else {
            val client = activeIpClient
                ?: return Result.failure(
                    IllegalStateException("IP 제어 연결을 다시 확인해 주세요.")
                )
            val followUp = wifiProvisionFollowUpForTransport(transport.type)
            val deviceId = (transportCoordinator.state.value as? TransportState.Connected)
                ?.deviceId
                ?: pendingWifiDirectTargetDeviceId
                ?: preferredAutomaticDeviceId.value
            if (followUp.waitForLanHandoff) {
                beginWifiProvisionLanHandoff(deviceId)
            }
            val response = client.configureWifi(request).getOrElse { error ->
                if (followUp.waitForLanHandoff) {
                    cancelWifiProvisionLanHandoff(scheduleRecovery = false)
                }
                return Result.failure(error)
            }
            if (!response.accepted) {
                if (followUp.waitForLanHandoff) {
                    cancelWifiProvisionLanHandoff(scheduleRecovery = false)
                }
                return Result.failure(
                    IllegalStateException("Jetson이 Wi-Fi 연결 요청을 접수하지 않았습니다.")
                )
            }
            if (followUp.waitForLanHandoff) {
                activateWifiProvisionLanHandoffTimeout()
            }
            Result.success(
                WifiProvisionReceipt(
                    ssid = response.ssid ?: request.ssid,
                    statusPollingAvailable = followUp.pollCurrentEndpoint,
                    lanHandoffRequired = followUp.waitForLanHandoff,
                    deviceId = deviceId
                )
            )
        }
    }

    suspend fun getWifiProvisionStatus(): Result<WifiProvisionStatus> {
        val transport = transportCoordinator.currentTransport()
            ?: return Result.failure(
                IllegalStateException("Wi-Fi 연결 결과를 확인할 Jetson 연결이 없습니다.")
            )
        if (transport.type == TransportType.BLE || transport.type == TransportType.WIFI_DIRECT) {
            return Result.failure(
                IllegalStateException(
                    if (transport.type == TransportType.WIFI_DIRECT) {
                        "Wi-Fi Direct 설정 후에는 새 LAN 연결에서 결과를 확인해야 합니다."
                    } else {
                        "BLE 연결에서는 Wi-Fi 최종 상태 조회를 지원하지 않습니다."
                    }
                )
            )
        }
        val client = activeIpClient
            ?: return Result.failure(
                IllegalStateException("IP 제어 연결을 다시 확인해 주세요.")
            )
        return client.getWifiProvisionStatus()
    }

    suspend fun awaitWifiProvisionLanHandoff(expectedDeviceId: String?): Result<Unit> {
        val targetDeviceId = expectedDeviceId
            ?: wifiProvisionLanHandoffDeviceId
            ?: return Result.failure(
                IllegalStateException("LAN으로 다시 찾을 Jetson 장비 ID가 없습니다.")
            )
        if (localNetworkPermissionGranted.value) {
            startLanDiscovery()
        }
        val connected = withTimeoutOrNull(WIFI_PROVISION_LAN_HANDOFF_TIMEOUT_MILLIS) {
            transportCoordinator.state
                .filter { state -> isMatchingLanHandoffTransport(state, targetDeviceId) }
                .first()
        }
        return if (connected != null) {
            completeWifiProvisionLanHandoff(targetDeviceId)
            Result.success(Unit)
        } else {
            expireWifiProvisionLanHandoff(targetDeviceId)
            Result.failure(
                IllegalStateException(
                    "Jetson의 Wi-Fi 요청은 접수됐지만 새 LAN 연결을 확인하지 못했습니다."
                )
            )
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
        cancelPendingWifiDirectEntry()
        cancelWifiProvisionLanHandoff(scheduleRecovery = false)
        automaticBleReconnectJob?.cancel()
        automaticBleReconnectJob = null
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
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

        wifiAccessPointScanner.refreshCurrentConnection()
        val mobileHasInfrastructureWifi =
            !wifiAccessPointScanner.state.value.currentSsid.isNullOrBlank()

        if (
            effectiveEnabled && localNetworkPermissionGranted &&
            !qrPairingActive.value && mobileHasInfrastructureWifi
        ) {
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
        if (
            transportCoordinator.currentTransport()?.type == TransportType.WIFI_DIRECT &&
            wifiDirectManager.state.value.connected
        ) {
            return
        }
        val targetDeviceId = resolveWifiDirectTargetDeviceId(peer = null)
        if (targetDeviceId == null) {
            wifiDirectManager.markEntryError(
                "Wi-Fi Direct로 연결할 등록 장비를 찾지 못했습니다. 장비를 먼저 등록해 주세요."
            )
            return
        }
        prepareForManualWifiDirectEntry()
        requestWifiDirectEntry(
            targetDeviceId = targetDeviceId,
            peer = null,
            automatic = false
        )
    }

    fun stopWifiDirectDiscovery() {
        cancelPendingWifiDirectEntry()
        wifiDirectManager.stopDiscovery()
        if (
            automaticConnectivityEnabled.value &&
            !qrPairingActive.value &&
            allowsAutomaticDirectFallback(transportCoordinator.state.value)
        ) {
            automaticDirectFallbackReady.value = false
            scheduleAutomaticIpFallback()
        }
    }

    fun connectWifiDirect(peer: WifiDirectPeer) {
        val targetDeviceId = resolveWifiDirectTargetDeviceId(peer)
        if (targetDeviceId == null) {
            wifiDirectManager.markEntryError(
                "선택한 Wi-Fi Direct 장비의 등록 정보를 찾지 못했습니다."
            )
            return
        }
        val direct = wifiDirectManager.state.value
        if (shouldConnectPreparedWifiDirectPeer(
                preparedTargetDeviceId = pendingWifiDirectTargetDeviceId,
                selectedTargetDeviceId = targetDeviceId,
                discoveryAttempted = direct.discoveryAttempted,
                connected = direct.connected,
                connectingPeerAddress = direct.connectingPeerAddress
            )
        ) {
            wifiDirectManager.connect(peer)
            return
        }
        prepareForManualWifiDirectEntry()
        requestWifiDirectEntry(
            targetDeviceId = targetDeviceId,
            peer = peer,
            automatic = false
        )
    }

    private fun prepareForManualWifiDirectEntry() {
        explicitDisconnectRequested.set(false)
        automaticConnectivityEnabled.value = true
        automaticDirectFallbackReady.value = false
        automaticDirectFallbackJob?.cancel()
        automaticDirectFallbackJob = null
        cancelWifiProvisionLanHandoff(scheduleRecovery = false)
        cancelPendingWifiDirectEntry()
        stopLanDiscovery()
        autoLanAttempts.clear()
        autoLanFailureCounts.clear()

        // A manual Direct choice supersedes any pending LAN probe. Its coroutine
        // checks this generation before every state-changing callback, so it can
        // no longer win later and cancel the P2P negotiation.
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
        _lanConnectionError.value = null

        // If LAN is already the active control transport, release only the app
        // session. The phone and Jetson network interfaces remain connected.
        val currentTransportType = transportCoordinator.currentTransport()?.type
        if (
            currentTransportType == TransportType.LAN ||
            (currentTransportType != TransportType.WIFI_DIRECT &&
                (wifiDirectManager.state.value.connected ||
                    wifiDirectManager.state.value.connectingPeerAddress != null))
        ) {
            wifiDirectManager.releaseForTransportHandoff()
        }
        if (currentTransportType == TransportType.LAN) {
            activeIpClient = null
            transportCoordinator.disconnect()
            clearReachableDeviceState()
        }
        ensureAutomaticBleReconnectLoop()
    }

    private fun requestWifiDirectEntry(
        targetDeviceId: String,
        peer: WifiDirectPeer?,
        automatic: Boolean
    ) {
        preferredAutomaticDeviceId.value = targetDeviceId
        wifiDirectManager.markEntryPreparing()
        val request = PendingWifiDirectEntry(
            targetDeviceId = targetDeviceId,
            peer = peer,
            automatic = automatic,
            generation = wifiDirectEntryGeneration.incrementAndGet()
        )
        pendingWifiDirectEntry = request
        scheduleWifiDirectEntryTimeout(request)
        ensureAutomaticBleReconnectLoop()
        startPendingWifiDirectEntryIfReady()
    }

    private fun scheduleWifiDirectEntryTimeout(request: PendingWifiDirectEntry) {
        wifiDirectEntryTimeoutJob?.cancel()
        wifiDirectEntryTimeoutJob = scope.launch {
            delay(WIFI_DIRECT_ENTRY_TIMEOUT_MILLIS)
            if (pendingWifiDirectEntry?.generation != request.generation) {
                return@launch
            }

            wifiDirectEntryGeneration.incrementAndGet()
            pendingWifiDirectEntry = null
            wifiDirectEntryJob?.cancel()
            wifiDirectEntryJob = null
            wifiDirectEntryTimeoutJob = null
            automaticDirectFallbackReady.value = false
            wifiDirectManager.markEntryError(
                "BLE로 Jetson을 준비하지 못했습니다. Bluetooth 상태를 확인한 뒤 다시 시도해 주세요."
            )
            if (request.automatic) {
                scheduleAutomaticIpFallback()
            }
        }
    }

    private fun resolveWifiDirectTargetDeviceId(peer: WifiDirectPeer?): String? =
        peer?.let(::wifiDirectTargetDeviceIdForPeer)
            ?: automaticTargetDeviceId(
                preferredAutomaticDeviceId.value,
                registeredDevices.value
            )

    private fun wifiDirectTargetDeviceIdForPeer(peer: WifiDirectPeer): String? =
        registeredDevices.value
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

    /**
     * Wi-Fi Direct discovery is gated by an authenticated BLE session. The new
     * backend command switches the Jetson radio into P2P mode; Android must not
     * start discovery until that write has been accepted and the radio had a
     * short preparation window.
     */
    private fun startPendingWifiDirectEntryIfReady(): Boolean {
        val request = pendingWifiDirectEntry ?: return false
        if (wifiDirectEntryJob?.isActive == true) {
            return true
        }
        wifiDirectEntryJob = scope.launch {
            executePendingWifiDirectEntry(request)
        }
        return true
    }

    private suspend fun executePendingWifiDirectEntry(
        initialRequest: PendingWifiDirectEntry
    ) {
        var request = initialRequest
        while (true) {
            if (
                !wifiDirectEntryIsCurrent(
                    currentGeneration = wifiDirectEntryGeneration.get(),
                    requestGeneration = request.generation,
                    connectivityEnabled = automaticConnectivityEnabled.value,
                    pairingActive = qrPairingActive.value
                ) || pendingWifiDirectEntry?.generation != request.generation
            ) {
                return
            }

            when (
                wifiDirectEntryReadiness(
                    automatic = request.automatic,
                    bleReady = gattClient.isReady(),
                    bleDeviceId = gattClient.currentDeviceId(),
                    targetDeviceId = request.targetDeviceId,
                    transportState = transportCoordinator.state.value,
                    lanConnectionPending = _connectingLanDeviceId.value != null
                )
            ) {
                WifiDirectEntryReadiness.BLOCKED_BY_LAN,
                WifiDirectEntryReadiness.WAITING_FOR_BLE -> return
                WifiDirectEntryReadiness.WRONG_BLE_DEVICE -> {
                    gattClient.disconnect()
                    return
                }
                WifiDirectEntryReadiness.READY -> Unit
            }

            val writeResult = gattClient.writeCommandAwait(
                CommandCodec.encode(JetsonCommand.REQUEST_WIFI_DIRECT)
            )
            if (
                !wifiDirectEntryIsCurrent(
                    currentGeneration = wifiDirectEntryGeneration.get(),
                    requestGeneration = request.generation,
                    connectivityEnabled = automaticConnectivityEnabled.value,
                    pairingActive = qrPairingActive.value
                ) || pendingWifiDirectEntry?.generation != request.generation
            ) {
                return
            }
            if (writeResult.isFailure) {
                automaticDirectFallbackReady.value = false
                request = request.copy(
                    commandWriteFailures = request.commandWriteFailures + 1
                )
                pendingWifiDirectEntry = request
                delay(wifiDirectCommandRetryDelayMillis(request.commandWriteFailures))
                continue
            }

            // The remote callback succeeded. If LAN started while the write was
            // in flight, let it finish before P2P discovery without resending 0x08.
            while (
                request.automatic &&
                _connectingLanDeviceId.value != null &&
                wifiDirectEntryIsCurrent(
                    currentGeneration = wifiDirectEntryGeneration.get(),
                    requestGeneration = request.generation,
                    connectivityEnabled = automaticConnectivityEnabled.value,
                    pairingActive = qrPairingActive.value
                )
            ) {
                delay(WIFI_DIRECT_DISCOVERY_SETTLE_MILLIS)
            }
            if (
                !wifiDirectEntryIsCurrent(
                    currentGeneration = wifiDirectEntryGeneration.get(),
                    requestGeneration = request.generation,
                    connectivityEnabled = automaticConnectivityEnabled.value,
                    pairingActive = qrPairingActive.value
                ) || pendingWifiDirectEntry?.generation != request.generation ||
                (request.automatic && !allowsAutomaticDirectFallback(
                    transportCoordinator.state.value
                ))
            ) {
                return
            }

            pendingWifiDirectEntry = null
            wifiDirectEntryTimeoutJob?.cancel()
            wifiDirectEntryTimeoutJob = null
            pendingWifiDirectTargetDeviceId = request.targetDeviceId
            scanner.stopScan()
            delay(WIFI_DIRECT_MODE_READY_DELAY_MILLIS)
            if (
                !wifiDirectEntryIsCurrent(
                    currentGeneration = wifiDirectEntryGeneration.get(),
                    requestGeneration = request.generation,
                    connectivityEnabled = automaticConnectivityEnabled.value,
                    pairingActive = qrPairingActive.value
                ) ||
                (request.automatic && !allowsAutomaticDirectAttempt(
                    transportCoordinator.state.value,
                    lanConnectionPending = _connectingLanDeviceId.value != null
                ))
            ) {
                return
            }
            // Both automatic fallback and an explicit Direct-screen entry must
            // connect the authenticated registered peer as soon as it appears.
            // Previously the manual screen only listed the peer and waited for
            // another tap, which left Samsung P2P idle until it shut down.
            automaticDirectFallbackReady.value = true
            wifiDirectManager.startDiscovery()
            request.peer?.let { peer ->
                delay(WIFI_DIRECT_DISCOVERY_SETTLE_MILLIS)
                if (
                    wifiDirectEntryIsCurrent(
                        currentGeneration = wifiDirectEntryGeneration.get(),
                        requestGeneration = request.generation,
                        connectivityEnabled = automaticConnectivityEnabled.value,
                        pairingActive = qrPairingActive.value
                    )
                ) {
                    wifiDirectManager.connect(peer)
                }
            }
            return
        }
    }

    private fun cancelPendingWifiDirectEntry(cancelActiveWrite: Boolean = true) {
        wifiDirectEntryGeneration.incrementAndGet()
        pendingWifiDirectEntry = null
        wifiDirectEntryTimeoutJob?.cancel()
        wifiDirectEntryTimeoutJob = null
        if (cancelActiveWrite) {
            wifiDirectEntryJob?.cancel()
            wifiDirectEntryJob = null
        }
    }

    private fun beginWifiProvisionLanHandoff(deviceId: String?) {
        wifiProvisionLanHandoffActive = true
        wifiProvisionLanHandoffDeviceId = deviceId
        automaticDirectFallbackReady.value = false
        automaticDirectFallbackJob?.cancel()
        automaticDirectFallbackJob = null
        cancelPendingWifiDirectEntry(cancelActiveWrite = false)
    }

    private fun activateWifiProvisionLanHandoffTimeout() {
        if (!wifiProvisionLanHandoffActive) {
            return
        }
        if (localNetworkPermissionGranted.value) {
            startLanDiscovery()
        }
        wifiProvisionLanHandoffTimeoutJob?.cancel()
        wifiProvisionLanHandoffTimeoutJob = scope.launch {
            delay(WIFI_PROVISION_LAN_HANDOFF_TIMEOUT_MILLIS)
            expireWifiProvisionLanHandoff(wifiProvisionLanHandoffDeviceId)
        }
    }

    private fun completeWifiProvisionLanHandoff(deviceId: String?) {
        val target = wifiProvisionLanHandoffDeviceId
        if (
            !wifiProvisionLanHandoffActive ||
            (target != null && deviceId != null &&
                !target.equals(deviceId, ignoreCase = true))
        ) {
            return
        }
        wifiProvisionLanHandoffActive = false
        wifiProvisionLanHandoffDeviceId = null
        wifiProvisionLanHandoffTimeoutJob?.cancel()
        wifiProvisionLanHandoffTimeoutJob = null
    }

    private fun expireWifiProvisionLanHandoff(deviceId: String?) {
        val target = wifiProvisionLanHandoffDeviceId
        if (
            !wifiProvisionLanHandoffActive ||
            (target != null && deviceId != null &&
                !target.equals(deviceId, ignoreCase = true))
        ) {
            return
        }
        wifiProvisionLanHandoffActive = false
        wifiProvisionLanHandoffDeviceId = null
        wifiProvisionLanHandoffTimeoutJob = null
        scheduleAutomaticIpFallback()
    }

    private fun cancelWifiProvisionLanHandoff(scheduleRecovery: Boolean) {
        val wasActive = wifiProvisionLanHandoffActive
        wifiProvisionLanHandoffActive = false
        wifiProvisionLanHandoffDeviceId = null
        wifiProvisionLanHandoffTimeoutJob?.cancel()
        wifiProvisionLanHandoffTimeoutJob = null
        if (!scheduleRecovery && transportCoordinator.currentTransport()?.type == TransportType.WIFI_DIRECT) {
            stopLanDiscovery()
        }
        if (wasActive && scheduleRecovery) {
            scheduleAutomaticIpFallback()
        }
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
        val generation = ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = generation
        _connectingLanDeviceId.value = endpoint.deviceId
        _lanConnectionError.value = null

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
                        _lanConnectionError.value =
                            "검색된 장비 ID와 API 장비 ID가 일치하지 않습니다."
                        return@onSuccess
                    }

                    if (credentialStore.getSecret(hello.deviceId) == null) {
                        _lanConnectionError.value =
                            "이 장비는 앱에 등록되어 있지 않습니다. 먼저 BLE/QR 등록을 완료해 주세요."
                        return@onSuccess
                    }

                    val statusResult = candidateClient.getStatus()
                    if (ipConnectionGeneration.get() != generation) {
                        return@onSuccess
                    }
                    if (statusResult.isFailure) {
                        _lanConnectionError.value =
                            statusResult.exceptionOrNull()?.message
                                ?: "Jetson API 인증에 실패했습니다."
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
                        _lanConnectionError.value =
                            "모바일과 Jetson의 Wi-Fi가 같지 않아 자동 LAN 연결을 건너뛰었습니다."
                        return@onSuccess
                    }
                    updateStatus(status)
                    val capabilitiesResult = candidateClient.getCapabilities()
                    if (ipConnectionGeneration.get() != generation) {
                        return@onSuccess
                    }
                    if (capabilitiesResult.isFailure) {
                        _lanConnectionError.value =
                            capabilitiesResult.exceptionOrNull()?.message
                                ?: "Jetson 기능 정보를 확인하지 못했습니다."
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
                    cancelPendingWifiDirectEntry()
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
                    completeWifiProvisionLanHandoff(hello.deviceId)
                    connectedSuccessfully = true
                    automaticAttemptKey?.let {
                        autoLanAttempts.remove(it)
                        autoLanFailureCounts.remove(it)
                    }
                    gattClient.disconnect()
                    pendingWifiDirectTargetDeviceId = null
                    wifiDirectManager.releaseForTransportHandoff()
                }
                .onFailure { error ->
                    if (ipConnectionGeneration.get() == generation) {
                        _lanConnectionError.value =
                            "${endpoint.displayName} API 연결 실패: " +
                                (error.message ?: "응답 없음")
                    }
                }
            } finally {
                if (connectingLanGeneration == generation) {
                    connectingLanGeneration = null
                    _connectingLanDeviceId.value = null
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
                allowsAutomaticLanRetry(
                    lanDiscoveryEnabled = autoLanEnabled.value,
                    transportState = transportCoordinator.state.value,
                    lanConnectionPending = _connectingLanDeviceId.value != null
                )
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
        val directProvisioningHandoff =
            transportCoordinator.currentTransport()?.type == TransportType.WIFI_DIRECT &&
                wifiProvisionLanHandoffActive
        ipConnectionGeneration.incrementAndGet()
        connectingLanGeneration = null
        _connectingLanDeviceId.value = null
        activeIpClient = null
        transportCoordinator.disconnect()
        clearReachableDeviceState()
        _lanConnectionError.value = if (directProvisioningHandoff) {
            null
        } else {
            message?.takeIf { it.isNotBlank() }
                ?.let { "Jetson 응답이 없어 오프라인으로 전환했습니다." }
        }
        pendingWifiDirectTargetDeviceId = null
        wifiDirectManager.cancelConnect()
        promoteReadyBleTransport()
        if (
            automaticConnectivityEnabled.value &&
            !qrPairingActive.value &&
            localNetworkPermissionGranted.value
        ) {
            startLanDiscovery()
        }
        if (!directProvisioningHandoff) {
            scheduleAutomaticIpFallback()
        }
    }

    private fun promoteReadyBleTransport(): Boolean {
        val ready = gattClient.connectionState.value as? ConnectionState.Ready
            ?: return false
        val deviceId = gattClient.currentDeviceId() ?: return false
        transportCoordinator.setActiveTransport(
            transport = BleControlTransport(gattClient),
            deviceId = deviceId,
            deviceName = ready.deviceName
        )
        updateStatus(gattClient.status.value)
        _capabilities.value = ControlCapabilities(
            systemControlConfigured = false,
            powerCommandsEnabled = true,
            fileBrowsing = false,
            uploads = false,
            wifiProvisioning = true,
            pipelines = false
        )
        preferredAutomaticDeviceId.value = deviceId
        return true
    }

    private fun scheduleAutomaticIpFallback() {
        automaticDirectFallbackJob?.cancel()
        automaticDirectFallbackReady.value = false
        if (
            !allowsAutomaticDirectRecovery(
                connectivityEnabled = automaticConnectivityEnabled.value,
                pairingActive = qrPairingActive.value,
                wifiProvisionLanHandoffActive = wifiProvisionLanHandoffActive
            )
        ) {
            if (wifiProvisionLanHandoffActive && localNetworkPermissionGranted.value) {
                startLanDiscovery()
            }
            return
        }
        automaticDirectFallbackJob = scope.launch {
            wifiAccessPointScanner.refreshCurrentConnection()
            val preferDirect = shouldPreferWifiDirectBeforeLan(
                mobileSsid = wifiAccessPointScanner.state.value.currentSsid,
                jetsonWifiConnected = _status.value.wifiConnected
            )
            if (localNetworkPermissionGranted.value && !preferDirect) {
                startLanDiscovery()
                delay(AUTOMATIC_LAN_GRACE_MILLIS)
            } else {
                stopLanDiscovery()
                // An infrastructure link that disappeared while an NSD/API
                // probe was running must not keep the offline Direct path
                // blocked by a stale LAN attempt.
                ipConnectionGeneration.incrementAndGet()
                connectingLanGeneration = null
                _connectingLanDeviceId.value = null
                _lanConnectionError.value = null
                autoLanAttempts.clear()
                autoLanFailureCounts.clear()
                delay(AUTOMATIC_DIRECT_START_DELAY_MILLIS)
            }
            val targetDeviceId = automaticTargetDeviceId(
                preferredAutomaticDeviceId.value,
                registeredDevices.value
            )
            if (
                automaticConnectivityEnabled.value &&
                !qrPairingActive.value &&
                localNetworkPermissionGranted.value &&
                nearbyWifiPermissionGranted.value &&
                !wifiDirectManager.state.value.connected &&
                allowsAutomaticDirectFallback(transportCoordinator.state.value) &&
                targetDeviceId != null
            ) {
                requestWifiDirectEntry(
                    targetDeviceId = targetDeviceId,
                    peer = null,
                    automatic = true
                )
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
                val direct = wifiDirectManager.state.value
                if (
                    bluetoothPermissionGranted.value &&
                    !qrPairingActive.value && targetDeviceId != null &&
                    allowsAutomaticBleReconnect(
                        transportState = transport,
                        lanConnectionPending = _connectingLanDeviceId.value != null,
                        wifiDirectConnectionInProgress =
                            direct.connectingPeerAddress != null && !direct.connected
                    ) &&
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
                    val nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()
                    val currentScanGeneration = scanner.scanGeneration.value
                    val candidates = scanner.devices.value.filter { candidate ->
                        candidate.name?.lowercase() in expectedNames &&
                            isFreshBleReconnectCandidate(
                                candidateScanGeneration = candidate.scanGeneration,
                                currentScanGeneration = currentScanGeneration,
                                observedAtElapsedRealtimeMillis =
                                    candidate.observedAtElapsedRealtimeMillis,
                                nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                                maxAgeMillis = BLE_RECONNECT_CANDIDATE_MAX_AGE_MILLIS
                            )
                    }
                    if (candidates.size == 1) {
                        reconnectRegistered(candidates.single(), targetDeviceId)
                    } else if (!scanner.isScanning.value) {
                        scanner.clear()
                        scanner.startScan(durationMillis = 15_000L, jetsonOnly = true)
                    }
                }
                delay(AUTOMATIC_BLE_RECONNECT_INTERVAL_MILLIS)
            }
        }
    }

    private fun refreshBleReconnectCandidatesAfterFailure() {
        val direct = wifiDirectManager.state.value
        if (
            !isCurrentBleFailureState(gattClient.connectionState.value) ||
            !automaticConnectivityEnabled.value ||
            explicitDisconnectRequested.get() ||
            !bluetoothPermissionGranted.value ||
            qrPairingActive.value ||
            automaticTargetDeviceId(
                preferredAutomaticDeviceId.value,
                registeredDevices.value
            ) == null ||
            !allowsAutomaticBleReconnect(
                transportState = transportCoordinator.state.value,
                lanConnectionPending = _connectingLanDeviceId.value != null,
                wifiDirectConnectionInProgress =
                    direct.connectingPeerAddress != null && !direct.connected
            )
        ) {
            return
        }
        scanner.clear()
        if (!scanner.isScanning.value) {
            scanner.startScan(durationMillis = 15_000L, jetsonOnly = true)
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
        JetsonCommand.REQUEST_WIFI_DIRECT -> "Wi-Fi Direct 준비"
    }
}

private const val AUTOMATIC_LAN_GRACE_MILLIS = 5_000L
private const val AUTOMATIC_LAN_RETRY_BASE_MILLIS = 1_500L
private const val AUTOMATIC_LAN_RETRY_MAX_MILLIS = 15_000L
private const val AUTOMATIC_LAN_RETRY_EXPONENT_LIMIT = 5
private const val AUTOMATIC_LAN_RETRY_DIRECT_PROBE_WAIT_MILLIS = 500L
private const val AUTOMATIC_DIRECT_START_DELAY_MILLIS = 750L
private const val WIFI_DIRECT_MODE_READY_DELAY_MILLIS = 1_000L
private const val WIFI_DIRECT_DISCOVERY_SETTLE_MILLIS = 250L
private const val WIFI_DIRECT_ENTRY_TIMEOUT_MILLIS = 60_000L
internal const val WIFI_PROVISION_LAN_HANDOFF_TIMEOUT_MILLIS = 330_000L
private const val AUTOMATIC_BLE_RECONNECT_INTERVAL_MILLIS = 5_000L
private const val BLE_RECONNECT_CANDIDATE_MAX_AGE_MILLIS = 20_000L
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

/**
 * A LAN probe cannot succeed unless both endpoints already have infrastructure
 * Wi-Fi. In that offline/bootstrap case BLE should lead directly to P2P instead
 * of spending the LAN grace period on an impossible discovery path.
 */
internal fun shouldPreferWifiDirectBeforeLan(
    mobileSsid: String?,
    jetsonWifiConnected: Boolean
): Boolean = mobileSsid.isNullOrBlank() || !jetsonWifiConnected
