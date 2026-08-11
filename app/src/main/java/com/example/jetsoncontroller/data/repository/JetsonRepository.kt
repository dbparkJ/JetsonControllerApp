package com.example.jetsoncontroller.data.repository

import android.content.Context
import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.data.bluetooth.BleScanner
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.data.network.LanDiscoveryManager
import com.example.jetsoncontroller.data.network.LocalApiClient
import com.example.jetsoncontroller.data.network.LocalControlApi
import com.example.jetsoncontroller.data.network.WifiDirectManager
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.transport.*
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.protocol.CommandCodec
import com.example.jetsoncontroller.protocol.JetsonCommand
import com.example.jetsoncontroller.protocol.WifiProvisionCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val lanDiscoveryManager = LanDiscoveryManager(context)
    private val apiClient = LocalApiClient(credentialStore)
    private val transportCoordinator = TransportCoordinator()

    val devices:
        StateFlow<List<JetsonDevice>> =
        scanner.devices

    val isScanning:
        StateFlow<Boolean> =
        scanner.isScanning

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

    val wifiDirectState = wifiDirectManager.state
    val lanEndpoints = lanDiscoveryManager.discoveredEndpoints
    val transportState = transportCoordinator.state

    init {
        scope.launch {
            gattClient.status.collect { currentStatus ->
                _status.value = currentStatus
            }
        }

        scope.launch {
            gattClient.connectionState.collect { state ->
                if (state is ConnectionState.Ready) {
                    transportCoordinator.setActiveTransport(BleControlTransport(gattClient))
                } else if (state is ConnectionState.Disconnected) {
                    if (transportCoordinator.currentTransport()?.type == TransportType.BLE) {
                        transportCoordinator.disconnect()
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
                        transportCoordinator.disconnect()
                    }
                }
        }
    }

    private suspend fun probeWifiDirectApi(host: String) {
        wifiDirectManager.markApiChecking()
        apiClient.updateEndpoint(host, LOCAL_API_PORT)

        val result = apiClient.hello()
        result.onSuccess { hello ->
            if (credentialStore.getSecret(hello.deviceId) == null) {
                val message =
                    "API 장비가 등록되어 있지 않습니다. 먼저 QR로 장비를 등록해 주세요."
                wifiDirectManager.markApiError(message)
                transportCoordinator.setError(TransportType.WIFI_DIRECT, message)
                return@onSuccess
            }

            wifiDirectManager.markApiReady(hello.deviceName)
            transportCoordinator.setActiveTransport(
                transport = IpControlTransport(
                    apiClient,
                    TransportType.WIFI_DIRECT
                ),
                endpoint = "$host:$LOCAL_API_PORT"
            )
        }.onFailure { error ->
            val message =
                "Jetson API($host:$LOCAL_API_PORT)에 연결하지 못했습니다: " +
                    (error.message ?: "응답 없음")
            wifiDirectManager.markApiError(message)
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


    fun disconnect() {

        gattClient.disconnect()
        if (wifiDirectManager.state.value.connected) {
            wifiDirectManager.disconnect()
        }
        transportCoordinator.disconnect()
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


    suspend fun refreshStatus(): Boolean {
        val transport = transportCoordinator.currentTransport()
            ?: return false

        return when (transport.type) {
            TransportType.BLE -> requestStatus()
            TransportType.WIFI_DIRECT,
            TransportType.LAN -> transport.getStatus()
                .onSuccess { _status.value = it }
                .isSuccess
        }
    }


    fun provisionWifi(
        request: WifiProvisionRequest
    ): Result<Unit> {
        return runCatching {
            val payload = WifiProvisionCodec.encode(request)
            check(sendCommand(JetsonCommand.SET_WIFI, payload)) {
                "Jetson에 Wi-Fi 설정을 전송하지 못했습니다. Bluetooth 연결을 확인하세요."
            }
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

    fun startPairing(info: PairingInfo) {
        scanner.stopScan()
        scanner.startScan(jetsonOnly = true)
    }

    fun connectForPairing(device: JetsonDevice, info: PairingInfo) {
        scanner.stopScan()
        gattClient.connectForPairing(device.device, device.name, info)
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

    fun startLanDiscovery() {
        lanDiscoveryManager.startDiscovery()
    }

    fun stopLanDiscovery() {
        lanDiscoveryManager.stopDiscovery()
    }

    suspend fun getRoots(): Result<List<RemoteRoot>> {
        return apiClient.getRoots()
    }

    suspend fun listDirectory(rootId: String, relativePath: String): Result<LocalControlApi.ListFilesResponse> {
        return apiClient.listFiles(rootId, relativePath)
    }

    suspend fun getUploadTargets(): Result<List<UploadTarget>> {
        return apiClient.getUploadTargets()
    }

    suspend fun startUpload(rootId: String, relativePath: String, targetId: String): Result<UploadJob> {
        return apiClient.startUpload(rootId, relativePath, targetId)
    }

    suspend fun getUploadJobs(): Result<List<UploadJob>> {
        return apiClient.getUploadJobs()
    }

    suspend fun getUploadJob(jobId: String): Result<UploadJob> {
        return apiClient.getUploadJob(jobId)
    }
}
