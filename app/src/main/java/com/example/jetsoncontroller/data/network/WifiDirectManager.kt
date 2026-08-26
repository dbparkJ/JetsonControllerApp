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
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.net.SocketFactory

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

internal enum class WifiDirectLinkPhase {
    CONNECTED,
    CONNECTING,
    DISCONNECTED
}

data class WifiDirectState(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val preparing: Boolean = false,
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
    private var peerPollingGeneration = 0L
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

                    if (enabled) {
                        _state.value = _state.value.copy(enabled = true, error = null)
                    } else {
                        resetDisconnectedState(
                            error = "Wi-Fi가 꺼져 있습니다. Wi-Fi를 켠 뒤 다시 검색해 주세요.",
                            enabled = false,
                            clearPeers = true
                        )
                    }
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
                    requestNetworkInfo()
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
        val currentState = _state.value
        if (!shouldStartWifiDirectDiscovery(
                discovering = currentState.discovering,
                connected = currentState.connected,
                connectingPeerAddress = currentState.connectingPeerAddress
            )
        ) {
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
            preparing = false,
            discovering = true,
            discoveryAttempted = true,
            peers = emptyList(),
            error = null
        )
        val pollGeneration = beginPeerPolling()
        Log.i(WIFI_DIRECT_LOG_TAG, "Starting Wi-Fi Direct peer discovery")

        try {
            readyManager.discoverPeers(
                readyChannel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        // Samsung devices can miss or delay PEERS_CHANGED while
                        // bringing p2p0 up. Query immediately and keep polling as
                        // a fallback instead of depending on that broadcast alone.
                        requestPeers()
                        schedulePeerPolling(pollGeneration)
                    }

                    override fun onFailure(reason: Int) {
                        cancelPeerPolling()
                        Log.w(
                            WIFI_DIRECT_LOG_TAG,
                            "Wi-Fi Direct discovery failed: reason=$reason"
                        )
                        fail(actionFailure("장비 검색", reason))
                    }
                }
            )
        } catch (_: SecurityException) {
            fail("주변 기기 권한을 허용한 뒤 다시 검색해 주세요.")
        }
    }

    private fun beginPeerPolling(): Long {
        peerPollingGeneration += 1
        return peerPollingGeneration
    }

    private fun cancelPeerPolling() {
        peerPollingGeneration += 1
    }

    private fun schedulePeerPolling(generation: Long, attempt: Int = 1) {
        mainHandler.postDelayed(
            {
                val current = _state.value
                if (!shouldContinueWifiDirectPeerPolling(
                        currentGeneration = peerPollingGeneration,
                        callbackGeneration = generation,
                        discovering = current.discovering,
                        connected = current.connected,
                        connectingPeerAddress = current.connectingPeerAddress,
                        attempt = attempt,
                        maxAttempts = WIFI_DIRECT_PEER_POLL_MAX_ATTEMPTS
                    )
                ) {
                    return@postDelayed
                }
                requestPeers()
                schedulePeerPolling(generation, attempt + 1)
            },
            WIFI_DIRECT_PEER_POLL_INTERVAL_MILLIS
        )
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
                if (!_state.value.enabled) {
                    return@requestPeers
                }
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
                if (peers.isNotEmpty()) {
                    Log.i(
                        WIFI_DIRECT_LOG_TAG,
                        "Wi-Fi Direct peers available: count=${peers.size}"
                    )
                }
            }
        } catch (_: SecurityException) {
            fail("주변 기기 권한을 허용한 뒤 다시 검색해 주세요.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(peer: WifiDirectPeer) {
        if (
            !_state.value.enabled ||
            _state.value.connected ||
            _state.value.connectingPeerAddress != null
        ) {
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

        cancelPeerPolling()
        val attemptGeneration = ++connectionAttemptGeneration
        _state.value = _state.value.copy(
            discovering = false,
            connectingPeerAddress = peer.deviceAddress,
            error = null
        )
        Log.i(WIFI_DIRECT_LOG_TAG, "Connecting to Wi-Fi Direct peer ${peer.name}")
        scheduleConnectionTimeout(peer.deviceAddress, attemptGeneration)

        try {
            readyManager.connect(
                readyChannel,
                config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        // Connection details arrive through the connection broadcast.
                        Log.i(WIFI_DIRECT_LOG_TAG, "Wi-Fi Direct connect request accepted")
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
                        Log.w(
                            WIFI_DIRECT_LOG_TAG,
                            "Wi-Fi Direct connect request failed: reason=$reason"
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

    private fun scheduleConnectionTimeout(peerAddress: String, attemptGeneration: Long) {
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

    @SuppressLint("MissingPermission")
    private fun requestNetworkInfo() {
        if (!hasNearbyWifiPermission()) {
            return
        }

        val readyManager = manager ?: return
        val readyChannel = ensureChannel() ?: return
        val queryGeneration = connectionAttemptGeneration

        try {
            readyManager.requestNetworkInfo(readyChannel) { networkInfo ->
                if (queryGeneration != connectionAttemptGeneration) {
                    return@requestNetworkInfo
                }
                val phase = networkInfo.toWifiDirectLinkPhase()
                if (
                    shouldPreservePendingWifiDirectConnection(
                        connectingPeerAddress = _state.value.connectingPeerAddress,
                        phase = phase
                    )
                ) {
                    requestConnectionInfo(preservePendingConnection = true)
                    return@requestNetworkInfo
                }
                when (phase) {
                    WifiDirectLinkPhase.CONNECTED,
                    WifiDirectLinkPhase.CONNECTING ->
                        requestConnectionInfo(preservePendingConnection = true)
                    WifiDirectLinkPhase.DISCONNECTED -> {
                        val current = _state.value
                        if (shouldPreserveActiveWifiDirectDiscovery(
                                discovering = current.discovering,
                                preparing = current.preparing,
                                phase = phase
                            )
                        ) {
                            // Android emits a disconnected P2P broadcast while it
                            // is creating the management interface. Treating that
                            // as terminal used to clear a discovery that had just
                            // started, so retain discovery and refresh peers.
                            requestPeers()
                            return@requestNetworkInfo
                        }
                        resetDisconnectedState(clearPeers = true)
                    }
                }
            }
        } catch (_: SecurityException) {
            fail("Wi-Fi Direct 연결 정보를 읽을 권한이 없습니다.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo(preservePendingConnection: Boolean = false) {
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
                    cancelPeerPolling()
                    connectionAttemptGeneration += 1
                    _state.value = _state.value.copy(
                        preparing = false,
                        discovering = false,
                        connectingPeerAddress = null,
                        connected = true,
                        groupOwnerAddress = info.groupOwnerAddress.hostAddress,
                        apiStatus = WifiDirectApiStatus.IDLE,
                        apiDeviceName = null,
                        apiError = null,
                        error = null
                    )
                    Log.i(
                        WIFI_DIRECT_LOG_TAG,
                        "Wi-Fi Direct group formed; owner=${info.groupOwnerAddress.hostAddress}"
                    )
                } else {
                    if (
                        preservePendingConnection &&
                        _state.value.connectingPeerAddress != null
                    ) {
                        return@requestConnectionInfo
                    }
                    _state.value = _state.value.copy(
                        peers = emptyList(),
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
        cancelPeerPolling()
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

        _state.value = _state.value.copy(
            preparing = false,
            discovering = false
        )
        if (!shouldKeepWifiDirectReceiver(
                connected = _state.value.connected,
                connectingPeerAddress = _state.value.connectingPeerAddress
            )
        ) {
            unregister()
        }
    }

    fun markEntryPreparing() {
        if (_state.value.connected) {
            return
        }
        connectionAttemptGeneration += 1
        _state.value = wifiDirectDisconnectedState(
            current = _state.value,
            clearPeers = true
        ).copy(
            preparing = true,
            discoveryAttempted = false
        )
    }

    fun markEntryError(message: String) {
        resetDisconnectedState(error = message, clearPeers = true)
    }

    /**
     * LAN becoming authoritative is a terminal P2P handoff. The Jetson may
     * already have removed the group, so Android can reject removeGroup even
     * though no P2P link remains. Clear the app's link state immediately while
     * still making a best-effort framework cleanup request.
     */
    @SuppressLint("MissingPermission")
    fun releaseForTransportHandoff() {
        val current = _state.value
        val readyManager = manager
        val readyChannel = channel

        if (readyManager != null && readyChannel != null) {
            val listener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() = Unit
                override fun onFailure(reason: Int) = Unit
            }
            try {
                when {
                    current.connected -> readyManager.removeGroup(readyChannel, listener)
                    current.connectingPeerAddress != null ->
                        readyManager.cancelConnect(readyChannel, listener)
                    current.discovering ->
                        readyManager.stopPeerDiscovery(readyChannel, listener)
                }
            } catch (_: SecurityException) {
                // Local state must still be released for the completed LAN handoff.
            }
        }

        resetDisconnectedState(clearPeers = true)
        unregister()
    }

    @SuppressLint("MissingPermission")
    fun cancelConnect() {
        if (_state.value.connected) {
            disconnect()
            return
        }

        val cancelGeneration = ++connectionAttemptGeneration

        val readyManager = manager
        val readyChannel = ensureChannel()
        if (readyManager == null || readyChannel == null) {
            resetDisconnectedState(clearPeers = true)
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
                        if (cancelGeneration != connectionAttemptGeneration) {
                            return
                        }
                        resetDisconnectedState()
                        stopDiscovery()
                    }

                    override fun onFailure(reason: Int) {
                        if (cancelGeneration != connectionAttemptGeneration) {
                            return
                        }
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
        val disconnectGeneration = ++connectionAttemptGeneration

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
                        if (disconnectGeneration != connectionAttemptGeneration) {
                            return
                        }
                        resetDisconnectedState(clearPeers = true)
                        unregister()
                    }

                    override fun onFailure(reason: Int) {
                        if (disconnectGeneration != connectionAttemptGeneration) {
                            return
                        }
                        reconcileRemoveGroupFailure(
                            message = actionFailure("연결 해제", reason),
                            expectedGeneration = disconnectGeneration
                        )
                    }
                }
            )
        } catch (_: SecurityException) {
            fail("Wi-Fi Direct 연결을 해제할 권한이 없습니다.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconcileRemoveGroupFailure(
        message: String,
        expectedGeneration: Long
    ) {
        val readyManager = manager
        val readyChannel = ensureChannel()
        if (readyManager == null || readyChannel == null || !hasNearbyWifiPermission()) {
            _state.value = _state.value.copy(error = message)
            return
        }

        try {
            readyManager.requestConnectionInfo(readyChannel) { info ->
                if (expectedGeneration != connectionAttemptGeneration) {
                    return@requestConnectionInfo
                }
                if (info.groupFormed) {
                    _state.value = _state.value.copy(error = message)
                } else {
                    resetDisconnectedState(clearPeers = true)
                    unregister()
                }
            }
        } catch (_: SecurityException) {
            _state.value = _state.value.copy(error = message)
        }
    }

    private fun resetDisconnectedState(
        error: String? = null,
        enabled: Boolean = _state.value.enabled,
        clearPeers: Boolean = false
    ) {
        cancelPeerPolling()
        connectionAttemptGeneration += 1
        _state.value = wifiDirectDisconnectedState(
            current = _state.value,
            error = error,
            enabled = enabled,
            clearPeers = clearPeers
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

    /**
     * Return the Android network-owned socket factory for the current P2P route.
     * This is deliberately scoped to the API client; binding the whole process
     * would break Internet-backed screens while Wi-Fi Direct is active.
     */
    fun socketFactoryForGroupOwner(host: String): SocketFactory? {
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
                    if (hasP2pInterface && reachesGroupOwner) network else null
                }
                .firstOrNull()
                ?.socketFactory
        }.getOrNull()
    }

    private fun fail(message: String) {
        cancelPeerPolling()
        connectionAttemptGeneration += 1
        _state.value = _state.value.copy(
            preparing = false,
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
}

internal fun NetworkInfo?.toWifiDirectLinkPhase(): WifiDirectLinkPhase =
    wifiDirectLinkPhase(
        isConnected = this?.isConnected == true,
        detailedState = this?.detailedState
    )

internal fun wifiDirectLinkPhase(
    isConnected: Boolean,
    detailedState: NetworkInfo.DetailedState?
): WifiDirectLinkPhase = when {
    isConnected -> WifiDirectLinkPhase.CONNECTED
    detailedState in setOf(
        NetworkInfo.DetailedState.CONNECTING,
        NetworkInfo.DetailedState.AUTHENTICATING,
        NetworkInfo.DetailedState.OBTAINING_IPADDR,
        NetworkInfo.DetailedState.SCANNING,
        NetworkInfo.DetailedState.VERIFYING_POOR_LINK,
        NetworkInfo.DetailedState.CAPTIVE_PORTAL_CHECK,
        NetworkInfo.DetailedState.DISCONNECTING
    ) -> WifiDirectLinkPhase.CONNECTING
    else -> WifiDirectLinkPhase.DISCONNECTED
}

internal fun shouldPreservePendingWifiDirectConnection(
    connectingPeerAddress: String?,
    phase: WifiDirectLinkPhase
): Boolean = connectingPeerAddress != null && phase != WifiDirectLinkPhase.CONNECTED

internal fun shouldPreserveActiveWifiDirectDiscovery(
    discovering: Boolean,
    preparing: Boolean,
    phase: WifiDirectLinkPhase
): Boolean = (discovering || preparing) && phase == WifiDirectLinkPhase.DISCONNECTED

internal fun shouldContinueWifiDirectPeerPolling(
    currentGeneration: Long,
    callbackGeneration: Long,
    discovering: Boolean,
    connected: Boolean,
    connectingPeerAddress: String?,
    attempt: Int,
    maxAttempts: Int
): Boolean = currentGeneration == callbackGeneration &&
    discovering &&
    !connected &&
    connectingPeerAddress == null &&
    attempt <= maxAttempts

internal fun shouldStartWifiDirectDiscovery(
    discovering: Boolean,
    connected: Boolean,
    connectingPeerAddress: String?
): Boolean = !discovering && !connected && connectingPeerAddress == null

internal fun shouldKeepWifiDirectReceiver(
    connected: Boolean,
    connectingPeerAddress: String?
): Boolean = connected || connectingPeerAddress != null

internal fun wifiDirectAttemptIsCurrent(
    currentGeneration: Long,
    callbackGeneration: Long,
    connectingPeerAddress: String?,
    callbackPeerAddress: String,
    connected: Boolean
): Boolean = currentGeneration == callbackGeneration &&
    connectingPeerAddress == callbackPeerAddress &&
    !connected

internal fun wifiDirectDisconnectedState(
    current: WifiDirectState,
    error: String? = null,
    enabled: Boolean = current.enabled,
    clearPeers: Boolean = false
): WifiDirectState = current.copy(
    enabled = enabled,
    preparing = false,
    discovering = false,
    peers = if (clearPeers) emptyList() else current.peers,
    connectingPeerAddress = null,
    connected = false,
    groupOwnerAddress = null,
    apiStatus = WifiDirectApiStatus.IDLE,
    apiDeviceName = null,
    apiError = null,
    error = error
)

private const val WIFI_DIRECT_CONNECT_TIMEOUT_MILLIS = 70_000L
private const val WIFI_DIRECT_CONNECT_FAILURE_COOLDOWN_MILLIS = 2_000L
private const val WIFI_DIRECT_PEER_POLL_INTERVAL_MILLIS = 1_000L
private const val WIFI_DIRECT_PEER_POLL_MAX_ATTEMPTS = 120
private const val WIFI_DIRECT_LOG_TAG = "JetsonWifiDirect"
