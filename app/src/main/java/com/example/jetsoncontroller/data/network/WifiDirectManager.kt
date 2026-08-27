package com.example.jetsoncontroller.data.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo
import android.os.Build
import android.os.Handler
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress

data class WifiDirectPeer(
    val name: String,
    val deviceAddress: String,
    val status: Int
)

enum class WifiDirectApiStatus {
    IDLE,
    CHECKING,
    READY,
    ERROR
}

data class WifiDirectState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val discovering: Boolean = false,
    val peers: List<WifiDirectPeer> = emptyList(),
    val connectingPeerAddress: String? = null,
    val connected: Boolean = false,
    val groupOwnerAddress: String? = null,
    val discoveryAttempted: Boolean = false,
    val apiStatus: WifiDirectApiStatus = WifiDirectApiStatus.IDLE,
    val apiDeviceName: String? = null,
    val apiError: String? = null,
    val error: String? = null
)

class WifiDirectManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(appContext.mainLooper)

    private val supported =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) &&
            manager != null

    private val _state = MutableStateFlow(
        WifiDirectState(
            supported = supported,
            enabled = supported && wifiManager?.isWifiEnabled == true,
            error = if (supported) null else "이 기기는 Wi-Fi Direct를 지원하지 않습니다."
        )
    )
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    private var connectionAttemptGeneration = 0L
    private var channel: WifiP2pManager.Channel? = createChannel()
    private var registered = false

    private fun createChannel(): WifiP2pManager.Channel? {
        return manager?.initialize(
            appContext,
            appContext.mainLooper
        ) {
            resetDisconnectedState(
                "Wi-Fi Direct 연결 채널이 끊어졌습니다. 다시 시도해 주세요."
            )
            channel = null
        }
    }

    private fun ensureChannel(): WifiP2pManager.Channel? {
        if (channel == null) {
            channel = createChannel()
        }
        return channel
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val wifiP2pState = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        -1
                    )
                    val enabled =
                        wifiP2pState == WifiP2pManager.WIFI_P2P_STATE_ENABLED

                    _state.value = _state.value.copy(
                        enabled = enabled,
                        discovering = if (enabled) _state.value.discovering else false,
                        error = if (enabled) {
                            null
                        } else {
                            "Wi-Fi가 꺼져 있습니다. Wi-Fi를 켠 뒤 다시 검색해 주세요."
                        }
                    )
                }

                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val discoveryState = intent.getIntExtra(
                        WifiP2pManager.EXTRA_DISCOVERY_STATE,
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
                    )
                    _state.value = _state.value.copy(
                        discovering =
                            discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                    )
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    requestConnectionInfo()
                }
            }
        }
    }

    fun register(): Boolean {
        if (registered) {
            return ensureChannel() != null
        }

        if (!supported) {
            fail("이 기기는 Wi-Fi Direct를 지원하지 않습니다.")
            return false
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }

        return try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
            _state.value = _state.value.copy(
                enabled = wifiManager?.isWifiEnabled == true
            )
            requestConnectionInfo()
            true
        } catch (_: Exception) {
            fail("Wi-Fi Direct 상태 수신기를 등록할 수 없습니다.")
            false
        }
    }

    fun unregister() {
        if (!registered) {
            return
        }

        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // The receiver was already removed by the system.
        } finally {
            registered = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (_state.value.discovering) {
            return
        }
        if (!hasNearbyWifiPermission()) {
            fail("주변 기기 권한을 허용한 뒤 다시 검색해 주세요.")
            return
        }

        if (locationManager?.isLocationEnabled != true) {
            fail("Wi-Fi Direct 검색을 위해 기기의 위치 서비스를 켜 주세요.")
            return
        }

        if (wifiManager?.isWifiEnabled != true) {
            fail("Wi-Fi를 켠 뒤 다시 검색해 주세요.")
            return
        }

        if (!register()) {
            return
        }

        val readyManager = manager
        val readyChannel = ensureChannel()
        if (readyManager == null || readyChannel == null) {
            fail("Wi-Fi Direct를 초기화할 수 없습니다.")
            return
        }

        _state.value = _state.value.copy(
            enabled = true,
            discovering = true,
            discoveryAttempted = true,
            peers = emptyList(),
            error = null
        )

        try {
            readyManager.discoverPeers(
                readyChannel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        // Peer results arrive through WIFI_P2P_PEERS_CHANGED_ACTION.
                    }

                    override fun onFailure(reason: Int) {
                        fail(actionFailure("장비 검색", reason))
                    }
                }
            )
        } catch (_: SecurityException) {
            fail("주변 기기 권한을 허용한 뒤 다시 검색해 주세요.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        if (!hasNearbyWifiPermission()) {
            fail("주변 기기 권한을 허용한 뒤 다시 검색해 주세요.")
            return
        }

        val readyManager = manager ?: return
        val readyChannel = ensureChannel() ?: return

        try {
            readyManager.requestPeers(readyChannel) { peerList ->
                val peers = peerList.deviceList
                    .map { device ->
                        WifiDirectPeer(
                            name = device.deviceName
                                .orEmpty()
                                .ifBlank { "이름 없는 장비" },
                            deviceAddress = device.deviceAddress,
                            status = device.status
                        )
                    }
                    .sortedBy { it.name.lowercase() }

                _state.value = _state.value.copy(
                    peers = peers,
                    error = null
                )
            }
        } catch (_: SecurityException) {
            fail("주변 기기 권한을 허용한 뒤 다시 검색해 주세요.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(peer: WifiDirectPeer) {
        if (_state.value.connected || _state.value.connectingPeerAddress != null) {
            return
        }
        if (!hasNearbyWifiPermission()) {
            fail("주변 기기 권한을 허용한 뒤 다시 연결해 주세요.")
            return
        }

        val readyManager = manager
        val readyChannel = ensureChannel()
        if (readyManager == null || readyChannel == null) {
            fail("Wi-Fi Direct를 초기화할 수 없습니다.")
            return
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            groupOwnerIntent = 0
            wps.setup = WpsInfo.PBC
        }

        val attemptGeneration = ++connectionAttemptGeneration
        _state.value = _state.value.copy(
            discovering = false,
            connectingPeerAddress = peer.deviceAddress,
            error = null
        )
        scheduleConnectionTimeout(peer.deviceAddress, attemptGeneration)

        try {
            readyManager.connect(
                readyChannel,
                config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        // Connection details arrive through the connection broadcast.
                    }

                    override fun onFailure(reason: Int) {
                        if (!wifiDirectAttemptIsCurrent(
                                currentGeneration = connectionAttemptGeneration,
                                callbackGeneration = attemptGeneration,
                                connectingPeerAddress = _state.value.connectingPeerAddress,
                                callbackPeerAddress = peer.deviceAddress,
                                connected = _state.value.connected
                            )
                        ) {
                            return
                        }
                        _state.value = _state.value.copy(
                            error = actionFailure("장비 연결", reason)
                        )
                        releaseFailedConnectionAfterCooldown(
                            peer.deviceAddress,
                            attemptGeneration
                        )
                    }
                }
            )
        } catch (_: SecurityException) {
            fail("주변 기기 권한을 허용한 뒤 다시 연결해 주세요.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        if (!hasNearbyWifiPermission()) {
            return
        }

        val readyManager = manager ?: return
        val readyChannel = ensureChannel() ?: return
        val queryGeneration = connectionAttemptGeneration

        try {
            readyManager.requestConnectionInfo(readyChannel) { info ->
                if (queryGeneration != connectionAttemptGeneration) {
                    return@requestConnectionInfo
                }
                if (info.groupFormed && info.groupOwnerAddress != null) {
                    connectionAttemptGeneration += 1
                    _state.value = _state.value.copy(
                        discovering = false,
                        connectingPeerAddress = null,
                        connected = true,
                        groupOwnerAddress = info.groupOwnerAddress.hostAddress,
                        apiStatus = WifiDirectApiStatus.IDLE,
                        apiDeviceName = null,
                        apiError = null,
                        error = null
                    )
                } else if (!shouldPreservePendingWifiDirectConnection(
                        connectingPeerAddress = _state.value.connectingPeerAddress,
                        groupFormed = info.groupFormed
                    )
                ) {
                    _state.value = _state.value.copy(
                        connectingPeerAddress = null,
                        connected = false,
                        groupOwnerAddress = null,
                        apiStatus = WifiDirectApiStatus.IDLE,
                        apiDeviceName = null,
                        apiError = null
                    )
                }
            }
        } catch (_: SecurityException) {
            fail("Wi-Fi Direct 연결 정보를 읽을 권한이 없습니다.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val readyManager = manager
        val readyChannel = channel

        if (readyManager != null && readyChannel != null && _state.value.discovering) {
            try {
                readyManager.stopPeerDiscovery(
                    readyChannel,
                    object : WifiP2pManager.ActionListener {
                        override fun onSuccess() = Unit
                        override fun onFailure(reason: Int) = Unit
                    }
                )
            } catch (_: SecurityException) {
                // Permission can be revoked while the screen is closing.
            }
        }

        _state.value = _state.value.copy(discovering = false)
        if (!_state.value.connected && _state.value.connectingPeerAddress == null) {
            unregister()
        }
    }

    @SuppressLint("MissingPermission")
    fun cancelConnect() {
        if (_state.value.connected) {
            disconnect()
            return
        }

        connectionAttemptGeneration += 1
        val readyManager = manager
        val readyChannel = ensureChannel()
        if (readyManager == null || readyChannel == null) {
            resetDisconnectedState()
            unregister()
            return
        }

        if (_state.value.connectingPeerAddress == null) {
            stopDiscovery()
            resetDisconnectedState()
            return
        }

        try {
            readyManager.cancelConnect(
                readyChannel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        resetDisconnectedState()
                        stopDiscovery()
                    }

                    override fun onFailure(reason: Int) {
                        _state.value = _state.value.copy(
                            connectingPeerAddress = null,
                            error = actionFailure("연결 취소", reason)
                        )
                        stopDiscovery()
                    }
                }
            )
        } catch (_: SecurityException) {
            resetDisconnectedState("Wi-Fi Direct 연결을 취소할 권한이 없습니다.")
            stopDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!_state.value.connected) {
            cancelConnect()
            return
        }

        connectionAttemptGeneration += 1
        val readyManager = manager
        val readyChannel = ensureChannel()
        if (readyManager == null || readyChannel == null) {
            resetDisconnectedState()
            unregister()
            return
        }

        try {
            readyManager.removeGroup(
                readyChannel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        resetDisconnectedState()
                        unregister()
                    }

                    override fun onFailure(reason: Int) {
                        _state.value = _state.value.copy(
                            error = actionFailure("연결 해제", reason)
                        )
                    }
                }
            )
        } catch (_: SecurityException) {
            fail("Wi-Fi Direct 연결을 해제할 권한이 없습니다.")
        }
    }

    private fun resetDisconnectedState(error: String? = null) {
        connectionAttemptGeneration += 1
        _state.value = _state.value.copy(
            discovering = false,
            connectingPeerAddress = null,
            connected = false,
            groupOwnerAddress = null,
            apiStatus = WifiDirectApiStatus.IDLE,
            apiDeviceName = null,
            apiError = null,
            error = error
        )
    }

    private fun hasNearbyWifiPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun markApiChecking() {
        _state.value = _state.value.copy(
            apiStatus = WifiDirectApiStatus.CHECKING,
            apiDeviceName = null,
            apiError = null
        )
    }

    fun markApiReady(deviceName: String) {
        _state.value = _state.value.copy(
            apiStatus = WifiDirectApiStatus.READY,
            apiDeviceName = deviceName,
            apiError = null
        )
    }

    fun markApiError(message: String) {
        _state.value = _state.value.copy(
            apiStatus = WifiDirectApiStatus.ERROR,
            apiDeviceName = null,
            apiError = message
        )
    }

    /** Local Android P2P address used to bind the mobile RTK relay server. */
    fun localAddressForGroupOwner(host: String): InetAddress? {
        val targetAddress = runCatching {
            InetAddresses.parseNumericAddress(host)
        }.getOrNull() ?: return null
        val readyConnectivityManager = connectivityManager ?: return null

        return runCatching {
            readyConnectivityManager.allNetworks
                .mapNotNull { network ->
                    val linkProperties = readyConnectivityManager.getLinkProperties(network)
                        ?: return@mapNotNull null
                    val interfaceName = linkProperties.interfaceName.orEmpty()
                    val hasP2pInterface = interfaceName.contains("p2p", ignoreCase = true)
                    val reachesGroupOwner = linkProperties.routes.any { route ->
                        route.matches(targetAddress)
                    }
                    val localAddress = linkProperties.linkAddresses
                        .map { it.address }
                        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                    if (hasP2pInterface && reachesGroupOwner) localAddress else null
                }
                .firstOrNull()
        }.getOrNull()
    }

    private fun fail(message: String) {
        connectionAttemptGeneration += 1
        _state.value = _state.value.copy(
            discovering = false,
            connectingPeerAddress = null,
            error = message
        )
    }

    private fun actionFailure(
        action: String,
        reason: Int
    ): String {
        val detail = when (reason) {
            WifiP2pManager.P2P_UNSUPPORTED -> "이 기기에서 Wi-Fi Direct를 지원하지 않습니다."
            WifiP2pManager.BUSY -> "Wi-Fi Direct가 사용 중입니다. 잠시 후 다시 시도해 주세요."
            WifiP2pManager.ERROR -> "Android Wi-Fi 서비스에서 오류가 발생했습니다."
            else -> "알 수 없는 오류 코드: $reason"
        }

        return "$action 실패: $detail"
    }

    private fun scheduleConnectionTimeout(
        peerAddress: String,
        attemptGeneration: Long
    ) {
        mainHandler.postDelayed(
            {
                if (wifiDirectAttemptIsCurrent(
                        currentGeneration = connectionAttemptGeneration,
                        callbackGeneration = attemptGeneration,
                        connectingPeerAddress = _state.value.connectingPeerAddress,
                        callbackPeerAddress = peerAddress,
                        connected = _state.value.connected
                    )
                ) {
                    resetDisconnectedState(
                        "Wi-Fi Direct 연결 시간이 초과되었습니다. 다시 시도해 주세요."
                    )
                }
            },
            WIFI_DIRECT_CONNECT_TIMEOUT_MILLIS
        )
    }

    private fun releaseFailedConnectionAfterCooldown(
        peerAddress: String,
        attemptGeneration: Long
    ) {
        mainHandler.postDelayed(
            {
                if (wifiDirectAttemptIsCurrent(
                        currentGeneration = connectionAttemptGeneration,
                        callbackGeneration = attemptGeneration,
                        connectingPeerAddress = _state.value.connectingPeerAddress,
                        callbackPeerAddress = peerAddress,
                        connected = _state.value.connected
                    )
                ) {
                    connectionAttemptGeneration += 1
                    _state.value = _state.value.copy(connectingPeerAddress = null)
                }
            },
            WIFI_DIRECT_CONNECT_FAILURE_COOLDOWN_MILLIS
        )
    }
}

internal fun shouldPreservePendingWifiDirectConnection(
    connectingPeerAddress: String?,
    groupFormed: Boolean
): Boolean = connectingPeerAddress != null && !groupFormed

internal fun wifiDirectAttemptIsCurrent(
    currentGeneration: Long,
    callbackGeneration: Long,
    connectingPeerAddress: String?,
    callbackPeerAddress: String,
    connected: Boolean
): Boolean = currentGeneration == callbackGeneration &&
    connectingPeerAddress == callbackPeerAddress &&
    !connected

private const val WIFI_DIRECT_CONNECT_TIMEOUT_MILLIS = 70_000L
private const val WIFI_DIRECT_CONNECT_FAILURE_COOLDOWN_MILLIS = 2_000L
