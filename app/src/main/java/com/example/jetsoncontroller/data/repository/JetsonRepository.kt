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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class JetsonRepository(
    context: Context,
    private val credentialStore: DeviceCredentialStore
) {
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

    val status:
        StateFlow<JetsonStatus> =
        gattClient.status

    val wifiDirectState = wifiDirectManager.state
    val lanEndpoints = lanDiscoveryManager.discoveredEndpoints
    val transportState = transportCoordinator.state

    init {
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
            wifiDirectManager.state.collect { state ->
                if (state.connected && state.groupOwnerAddress != null) {
                    apiClient.updateEndpoint(state.groupOwnerAddress, 8765)
                    transportCoordinator.setActiveTransport(IpControlTransport(apiClient, TransportType.WIFI_DIRECT))
                }
            }
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
        wifiDirectManager.register()
        wifiDirectManager.startDiscovery()
    }

    fun stopWifiDirectDiscovery() {
        wifiDirectManager.unregister()
    }

    fun connectWifiDirect(peer: WifiDirectPeer) {
        scanner.stopScan()
        wifiDirectManager.connect(peer)
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
