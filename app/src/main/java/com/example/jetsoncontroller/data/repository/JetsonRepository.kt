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
import kotlinx.coroutines.SupervisorJob
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
import java.util.concurrent.atomic.AtomicLong
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
                    ipConnectionGeneration.incrementAndGet()
                    activeIpClient = null
                    transportCoordinator.disconnect()
                    if (
                        previousTransport == TransportType.WIFI_DIRECT &&
                        wifiDirectManager.state.value.connected
                    ) {
                        wifiDirectManager.disconnect()
                    }
                    _status.value = JetsonStatus()
                    _statusUpdatedAtEpochMillis.value = null
                    _capabilities.value = ControlCapabilities()
                } else if (state is ConnectionState.Ready) {
                    ipConnectionGeneration.incrementAndGet()
                    activeIpClient = null
                    transportCoordinator.setActiveTransport(
                        transport = BleControlTransport(gattClient),
                        deviceId = gattClient.currentDeviceId(),
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
                } else if (
                    state is ConnectionState.Disconnected ||
                    state is ConnectionState.Error
                ) {
                    if (transportCoordinator.currentTransport()?.type == TransportType.BLE) {
                        transportCoordinator.disconnect()
                        _capabilities.value = ControlCapabilities()
                    }
                }
            }
        }
        
        scope.launch {
            wifiDirectManager.state
                .map { state ->
                    state.connected to state.groupOwnerAddress
                }
                .distinctUntilChanged()
                .collectLatest { (connected, host) ->
                    if (connected && host != null) {
                        probeWifiDirectApi(host)
                    } else if (
                        transportCoordinator.currentTransport()?.type ==
                            TransportType.WIFI_DIRECT
                    ) {
                        ipConnectionGeneration.incrementAndGet()
                        activeIpClient = null
                        transportCoordinator.disconnect()
                    }
                }
        }

        scope.launch {
            combine(
                lanDiscoveryManager.discoveredEndpoints,
                registeredDevices,
                transportCoordinator.state,
                autoLanEnabled,
                wifiAccessPointScanner.state
            ) { endpoints, registered, transport, enabled, wifiState ->
                if (
                    !enabled || transport !is TransportState.Disconnected ||
                    wifiState.currentSsid.isNullOrBlank() ||
                    _connectingLanDeviceId.value != null
                ) {
                    return@combine null
                }
                endpoints.firstOrNull { endpoint ->
                    registered.any { device ->
                        device.deviceId.equals(endpoint.deviceId, ignoreCase = true)
                    }
                }
            }.collect { endpoint ->
                endpoint ?: return@collect
                val attemptKey = "${endpoint.deviceId}@${endpoint.host}:${endpoint.port}"
                if (autoLanAttempts.add(attemptKey)) {
                    connectLan(endpoint, requireSameWifi = true)
                }
            }
        }
    }

    private suspend fun probeWifiDirectApi(host: String) {
        val generation = ipConnectionGeneration.incrementAndGet()
        wifiDirectManager.markApiChecking()
        val candidateClient = LocalApiClient(credentialStore)
        candidateClient.updateEndpoint(host, LOCAL_API_PORT)

        val result = candidateClient.hello()
        result.onSuccess { hello ->
            if (ipConnectionGeneration.get() != generation) {
                return@onSuccess
            }
            if (credentialStore.getSecret(hello.deviceId) == null) {
                val message =
                    "API 장비가 등록되어 있지 않습니다. 먼저 QR로 장비를 등록해 주세요."
                reportWifiDirectApiError(message)
                return@onSuccess
            }

            val statusResult = candidateClient.getStatus()
            if (ipConnectionGeneration.get() != generation) {
                return@onSuccess
            }
            if (statusResult.isFailure) {
                val message = statusResult.exceptionOrNull()?.message
                    ?: "Jetson API 인증에 실패했습니다."
                reportWifiDirectApiError(message)
                return@onSuccess
            }

            updateStatus(statusResult.getOrThrow())
            val capabilitiesResult = candidateClient.getCapabilities()
            if (ipConnectionGeneration.get() != generation) {
                return@onSuccess
            }
            if (capabilitiesResult.isFailure) {
                val message = capabilitiesResult.exceptionOrNull()?.message
                    ?: "Jetson 기능 정보를 확인하지 못했습니다."
                reportWifiDirectApiError(message)
                return@onSuccess
            }
            _capabilities.value = capabilitiesResult.getOrThrow().toModel()

            wifiDirectManager.markApiReady(hello.deviceName)
            activeIpClient = candidateClient
            transportCoordinator.setActiveTransport(
                transport = IpControlTransport(
                    candidateClient,
                    TransportType.WIFI_DIRECT
                ),
                endpoint = "$host:$LOCAL_API_PORT",
                deviceId = hello.deviceId,
                deviceName = hello.deviceName
            )
            gattClient.disconnect()
        }.onFailure { error ->
            if (ipConnectionGeneration.get() != generation) {
                return@onFailure
            }
            val message =
                "Jetson API($host:$LOCAL_API_PORT)에 연결하지 못했습니다: " +
                    (error.message ?: "응답 없음")
            reportWifiDirectApiError(message)
        }
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

        scanner.stopScan()

        gattClient.connect(
            device = device.device,
            displayName = device.name
        )
    }

    fun reconnectRegistered(device: JetsonDevice, expectedDeviceId: String) {
        scanner.stopScan()
        gattClient.connect(
            device = device.device,
            displayName = device.name,
            expectedDeviceId = expectedDeviceId
        )
    }

    suspend fun forgetRegisteredDevice(deviceId: String) {
        credentialStore.removeCredential(deviceId)
    }


    fun disconnect() {

        ipConnectionGeneration.incrementAndGet()
        gattClient.disconnect()
        if (wifiDirectManager.state.value.connected) {
            wifiDirectManager.disconnect()
        }
        transportCoordinator.disconnect()
        activeIpClient = null
        _status.value = JetsonStatus()
        _statusUpdatedAtEpochMillis.value = null
        _capabilities.value = ControlCapabilities()
        _controlOperation.value = ControlOperationState()
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
            TransportType.LAN -> transport.getStatus()
                .onSuccess(::updateStatus)
                .isSuccess
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

    fun startPairing(info: PairingInfo): Boolean {
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
        scanner.stopScan()
        if (gattClient.connectionState.value !is ConnectionState.RegistrationRequired) {
            gattClient.disconnect()
        }
    }

    fun connectForPairing(device: JetsonDevice, info: PairingInfo) {
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
        scanner.stopScan()
        gattClient.disconnect()
    }

    fun startWifiDirectDiscovery() {
        wifiDirectManager.startDiscovery()
    }

    fun stopWifiDirectDiscovery() {
        wifiDirectManager.stopDiscovery()
    }

    fun connectWifiDirect(peer: WifiDirectPeer) {
        scanner.stopScan()
        wifiDirectManager.connect(peer)
    }

    fun retryWifiDirectApi() {
        val host = wifiDirectManager.state.value.groupOwnerAddress
            ?: return
        scope.launch {
            probeWifiDirectApi(host)
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
        autoLanEnabled.value = true
        wifiAccessPointScanner.refreshCurrentConnection()
        lanDiscoveryManager.startDiscovery()
    }

    fun stopLanDiscovery() {
        autoLanEnabled.value = false
        lanDiscoveryManager.stopDiscovery()
    }

    fun connectLan(endpoint: DeviceEndpoint) {
        connectLan(endpoint, requireSameWifi = false)
    }

    private fun connectLan(endpoint: DeviceEndpoint, requireSameWifi: Boolean) {
        val generation = ipConnectionGeneration.incrementAndGet()
        _connectingLanDeviceId.value = endpoint.deviceId
        _lanConnectionError.value = null

        scope.launch {
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
                    _capabilities.value = capabilitiesResult.getOrThrow().toModel()

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
                    gattClient.disconnect()
                    if (wifiDirectManager.state.value.connected) {
                        wifiDirectManager.disconnect()
                    }
                }
                .onFailure { error ->
                    if (ipConnectionGeneration.get() == generation) {
                        _lanConnectionError.value =
                            "${endpoint.displayName} API 연결 실패: " +
                                (error.message ?: "응답 없음")
                    }
                }

            if (ipConnectionGeneration.get() == generation) {
                _connectingLanDeviceId.value = null
            }
        }
    }

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

    suspend fun listDirectory(rootId: String, relativePath: String): Result<LocalControlApi.ListFilesResponse> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.listFiles(rootId, relativePath)
    }

    suspend fun getFile(rootId: String, relativePath: String): Result<RemoteFileContent> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getFile(rootId, relativePath)
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

    suspend fun getPipelines(): Result<List<ManagedPipeline>> {
        val client = activeIpClient ?: return missingIpConnection()
        return client.getPipelines()
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
            pipelines = pipelines
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
