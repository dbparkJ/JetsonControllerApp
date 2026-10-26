package com.example.jetsoncontroller.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.EndpointTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LanDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    internal companion object {
        const val RETRY_MILLIS = 2_000L
        const val RESOLVE_TIMEOUT_MILLIS = 12_000L
        const val REFRESH_MILLIS = 30_000L
        const val STOP_TIMEOUT_MILLIS = 2_000L
        const val START_TIMEOUT_MILLIS = 8_000L
    }

    private val SERVICE_TYPE = "_jetsonctl._tcp."
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _discoveredEndpoints = MutableStateFlow<List<DeviceEndpoint>>(emptyList())
    val discoveredEndpoints: StateFlow<List<DeviceEndpoint>> = _discoveredEndpoints.asStateFlow()

    private val _lastSeenAtEpochMillis = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastSeenAtEpochMillis: StateFlow<Map<String, Long>> =
        _lastSeenAtEpochMillis.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var requested = false
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var stoppingListener: NsdManager.DiscoveryListener? = null
    private var restartJob: Job? = null
    private var discoveryWatchdog: Job? = null
    private var resolveWatchdog: Job? = null
    private val resolveQueue = linkedMapOf<String, NsdServiceInfo>()
    private var activeResolve: NsdManager.ResolveListener? = null
    private val authenticatedEndpoints = ConcurrentHashMap<String, DeviceEndpoint>()
    private val endpointPreferences = context.applicationContext.getSharedPreferences(
        "authenticated_lan_endpoints", Context.MODE_PRIVATE
    )

    fun rememberAuthenticatedEndpoint(endpoint: DeviceEndpoint) {
        val id = endpoint.deviceId.lowercase()
        authenticatedEndpoints[id] = endpoint
        endpointPreferences.edit().putString("$id.host", endpoint.host)
            .putInt("$id.port", endpoint.port).apply()
    }

    /** Only authenticated success writes this cache; callers still authenticate every attempt. */
    fun authenticatedEndpoint(deviceId: String, displayName: String): DeviceEndpoint? {
        val id = runCatching { UUID.fromString(deviceId).toString().lowercase() }.getOrNull()
            ?: return null
        val endpoint = authenticatedEndpoints[id] ?: endpointPreferences.getString("$id.host", null)?.let {
            DeviceEndpoint(id, displayName, it, endpointPreferences.getInt("$id.port", -1), EndpointTransport.LAN)
        } ?: return null
        if (endpoint.port !in 1..65535) return null
        return runCatching {
            val address = InetAddress.getByName(endpoint.host)
            selectLanServiceAddress(listOf(address), emptyList(), currentDirectPrefixes())
                ?.let { endpoint.copy(host = it.hostAddress.orEmpty(), displayName = displayName) }
        }.getOrNull()
    }

    /** Re-evaluate on every retry, after Android has restored foreground network access. */
    fun reconnectEndpoint(endpoint: DeviceEndpoint): DeviceEndpoint {
        val id = endpoint.deviceId.lowercase()
        val previous = (authenticatedEndpoints[id] ?: endpointPreferences.getString("$id.host", null)?.let {
            endpoint.copy(host = it, port = endpointPreferences.getInt("$id.port", -1))
        })?.takeIf { it.port == endpoint.port }
        val directPrefixes = currentDirectPrefixes()
        val prefixes = runCatching {
            connectivityManager.allNetworks.filter { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                !isDirectNetwork(network) &&
                    (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true)
            }.flatMap { network ->
                connectivityManager.getLinkProperties(network)?.linkAddresses.orEmpty()
                    .map { LanAddressPrefix(it.address, it.prefixLength) }
            }
        }.getOrDefault(emptyList())
        return runCatching {
            val host = selectLanReconnectAddress(
                InetAddress.getByName(endpoint.host),
                previous?.let { runCatching { InetAddress.getByName(it.host) }.getOrNull() },
                prefixes,
                directPrefixes
            )?.hostAddress.orEmpty()
            // An empty host is rejected by the existing LAN endpoint validation.
            endpoint.copy(host = host)
        }.getOrDefault(endpoint)
    }

    /** Explicit retry always retires the previous listener, even before onDiscoveryStarted. */
    fun startDiscovery() {
        requested = true
        restartDiscovery()
    }

    /** Repeated lifecycle/policy notifications must not interrupt a healthy resolve. */
    fun ensureDiscovery() {
        if (!requested) startDiscovery()
        else if (discoveryListener == null && stoppingListener == null && restartJob == null) beginDiscovery()
    }

    private fun restartDiscovery() {
        restartJob?.cancel()
        restartJob = null
        discoveryWatchdog?.cancel()
        resolveQueue.clear()
        val old = discoveryListener
        discoveryListener = null
        _isDiscovering.value = false
        if (old != null) {
            stoppingListener = old
            Log.d("JetsonLAN", "Stopping previous NSD discovery before restart")
            runCatching { nsdManager.stopServiceDiscovery(old) }
                .onFailure { Log.w("JetsonLAN", "NSD stop failed; waiting before restart", it) }
        }
        if (stoppingListener == null) beginDiscovery()
        else restartJob = scope.launch {
            delay(STOP_TIMEOUT_MILLIS)
            stoppingListener = null
            restartJob = null
            if (requested) beginDiscovery()
        }
    }

    private fun beginDiscovery() {
        if (!requested) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) { val owner = this; scope.launch {
                if (discoveryListener !== owner) return@launch
                _isDiscovering.value = true
                _error.value = null
                watchDiscovery(REFRESH_MILLIS)
            } }
            override fun onServiceFound(service: NsdServiceInfo) { val owner = this; scope.launch {
                if (discoveryListener !== owner || !requested) return@launch
                if (service.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.')) return@launch
                if (resolveQueue.size < 64) resolveQueue[service.serviceName] = service
                resolveNext()
            } }
            override fun onServiceLost(service: NsdServiceInfo) { val owner = this; scope.launch {
                if (discoveryListener !== owner) return@launch
                resolveQueue.remove(service.serviceName)
                _discoveredEndpoints.value = _discoveredEndpoints.value.filterNot { it.displayName == service.serviceName }
            } }
            override fun onDiscoveryStopped(regType: String) { discoveryStopped(this) }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                val owner = this
                scope.launch {
                    if (discoveryListener !== owner) return@launch
                    Log.w("JetsonLAN", "NSD start failed: $errorCode; retrying")
                    _error.value = "같은 네트워크 장치 검색을 다시 시도합니다. ($errorCode)"
                    // FAILURE_ALREADY_ACTIVE also needs a stop with the old listener.
                    retryDiscovery()
                }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w("JetsonLAN", "NSD stop failed: $errorCode")
                discoveryStopped(this)
            }
        }
        discoveryListener = listener
        _isDiscovering.value = true
        _error.value = null
        Log.d("JetsonLAN", "Starting NSD discovery for $SERVICE_TYPE")
        watchDiscovery(START_TIMEOUT_MILLIS)
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: SecurityException) {
            discoveryListener = null
            _isDiscovering.value = false
            discoveryWatchdog?.cancel()
            _error.value = "로컬 네트워크 권한을 허용해 주세요."
            // A later permission/lifecycle notification calls ensureDiscovery again.
            Log.w("JetsonLAN", "NSD permission denied", error)
        } catch (error: Exception) {
            Log.e("JetsonLAN", "NSD discovery failed", error)
            _error.value = "같은 네트워크 장치 검색을 다시 시도합니다."
            retryDiscovery()
        }
    }

    private fun watchDiscovery(timeoutMillis: Long) {
        discoveryWatchdog?.cancel()
        discoveryWatchdog = scope.launch {
            delay(timeoutMillis)
            Log.d("JetsonLAN", "Refreshing NSD discovery after ${timeoutMillis}ms")
            restartDiscovery()
        }
    }

    private fun discoveryStopped(listener: NsdManager.DiscoveryListener) { scope.launch {
        if (stoppingListener === listener) {
            stoppingListener = null
            restartJob?.cancel()
            restartJob = null
            if (requested) beginDiscovery()
        } else if (discoveryListener === listener) {
            discoveryListener = null
            _isDiscovering.value = false
            if (requested) retryDiscovery()
        }
    } }

    private fun retryDiscovery() {
        if (!requested || restartJob != null) return
        discoveryWatchdog?.cancel()
        restartJob = scope.launch {
            delay(RETRY_MILLIS)
            restartJob = null
            if (requested) restartDiscovery()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (!requested || activeResolve != null || resolveQueue.isEmpty() || restartJob != null) return
        val (name, service) = resolveQueue.entries.first().let { it.key to it.value }
        resolveQueue.remove(name)
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                val owner = this
                scope.launch {
                    if (activeResolve !== owner) return@launch
                    finishResolve()
                    Log.w("JetsonLAN", "NSD resolve failed for ${serviceInfo.serviceName}: $errorCode; retrying")
                    _error.value = "LAN 장치 주소 확인을 다시 시도합니다. ($errorCode)"
                    retryDiscovery()
                }
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val owner = this
                scope.launch {
                    if (activeResolve !== owner) return@launch
                    finishResolve()
                    if (!requested) return@launch
                    publishResolvedService(serviceInfo)
                    resolveNext()
                }
            }
        }
        activeResolve = listener
        resolveWatchdog = scope.launch {
            delay(RESOLVE_TIMEOUT_MILLIS)
            if (activeResolve !== listener) return@launch
            finishResolve()
            Log.w("JetsonLAN", "NSD resolve timed out for $name; retrying")
            _error.value = "LAN 장치 주소 확인 시간이 초과되어 다시 검색합니다."
            // API 30 has no resolve cancellation. Late callbacks are ignored and
            // a daemon FAILURE_ALREADY_ACTIVE response receives the same retry delay.
            retryDiscovery()
        }
        try {
            nsdManager.resolveService(service, listener)
        } catch (error: Exception) {
            finishResolve()
            Log.w("JetsonLAN", "NSD resolve could not start for $name", error)
            retryDiscovery()
        }
    }

    private fun finishResolve() {
        activeResolve = null
        resolveWatchdog?.cancel()
        resolveWatchdog = null
    }

    private fun publishResolvedService(serviceInfo: NsdServiceInfo) {
        val attributes = serviceInfo.attributes.mapValues { (_, value) ->
            String(value, StandardCharsets.UTF_8)
        }
        if (attributes["api"] != "1" || attributes["tls"] != "1") {
            Log.w("JetsonLAN", "Ignoring NSD service ${serviceInfo.serviceName}: unsupported API/TLS metadata")
            return
        }
        val deviceId = runCatching {
            UUID.fromString(attributes["id"]).toString().lowercase()
        }.getOrNull() ?: run {
            Log.w("JetsonLAN", "Ignoring NSD service ${serviceInfo.serviceName}: invalid device ID")
            return
        }
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfo.hostAddresses
        } else {
            listOfNotNull(serviceInfo.host)
        }
        val serviceNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            serviceInfo.network
        } else null
        if (serviceNetwork != null && isDirectNetwork(serviceNetwork)) {
            Log.d("JetsonLAN", "Ignoring NSD service ${serviceInfo.serviceName} on Wi-Fi Direct")
            return
        }
        val localPrefixes = runCatching {
            val networks = serviceNetwork?.let(::listOf)
                ?: connectivityManager.allNetworks.filter { network ->
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    !isDirectNetwork(network) &&
                        (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true)
                }
            networks.flatMap { network ->
                connectivityManager.getLinkProperties(network)?.linkAddresses.orEmpty()
                    .map { LanAddressPrefix(it.address, it.prefixLength) }
            }
        }.getOrDefault(emptyList())
        val host = selectLanServiceAddress(
            addresses, localPrefixes, currentDirectPrefixes()
        )?.hostAddress.orEmpty()
        if (host.isBlank() || serviceInfo.port !in 1..65535) {
            Log.w("JetsonLAN", "NSD service ${serviceInfo.serviceName} has no usable LAN endpoint; " +
                "advertised=${addresses.map { it.hostAddress }}, port=${serviceInfo.port}; retrying")
            _error.value = "같은 네트워크에서 사용할 수 있는 장치 주소를 다시 확인합니다."
            watchDiscovery(RETRY_MILLIS)
            return
        }
        Log.d(
            "JetsonLAN",
            "Resolved ${serviceInfo.serviceName} at $host:${serviceInfo.port} " +
                "for $deviceId; advertised=${addresses.map { it.hostAddress }}"
        )
        val endpoint = DeviceEndpoint(
            deviceId = deviceId,
            displayName = serviceInfo.serviceName,
            host = host,
            port = serviceInfo.port,
            transport = EndpointTransport.LAN
        )
        updateEndpoints(endpoint)
        _error.value = null
        if (discoveryListener != null && restartJob == null) watchDiscovery(REFRESH_MILLIS)
    }

    fun stopDiscovery() {
        requested = false
        restartJob?.cancel()
        restartJob = null
        discoveryWatchdog?.cancel()
        resolveQueue.clear()
        finishResolve()
        val old = discoveryListener
        discoveryListener = null
        _isDiscovering.value = false
        if (old != null) {
            stoppingListener = old
            runCatching { nsdManager.stopServiceDiscovery(old) }
        }
    }

    private fun isDirectNetwork(network: Network): Boolean {
        val name = connectivityManager.getLinkProperties(network)?.interfaceName.orEmpty()
        return isWifiDirectInterface(name)
    }

    private fun currentDirectPrefixes(): List<LanAddressPrefix> {
        val kernelPrefixes = runCatching {
            val candidates = NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { it.isUp }
                ?.flatMap { networkInterface ->
                    networkInterface.interfaceAddresses.asSequence().map {
                        WifiDirectInterfaceAddressCandidate(
                            networkInterface.name.orEmpty(), it.address, it.networkPrefixLength.toInt()
                        )
                    }
                }?.toList().orEmpty()
            kernelWifiDirectPrefixes(candidates)
        }.getOrDefault(emptyList())
        val networkPrefixes = runCatching {
            connectivityManager.allNetworks.flatMap { network ->
                val link = connectivityManager.getLinkProperties(network)
                if (link == null || !isWifiDirectInterface(link.interfaceName.orEmpty())) emptyList()
                else link.linkAddresses.map { LanAddressPrefix(it.address, it.prefixLength) } +
                    link.routes.filterNot { it.isDefaultRoute }.map {
                        LanAddressPrefix(it.destination.address, it.destination.prefixLength)
                    }
            }
        }.getOrDefault(emptyList())
        return (kernelPrefixes + networkPrefixes).distinct()
    }

    private fun updateEndpoints(endpoint: DeviceEndpoint) {
        val current = _discoveredEndpoints.value.toMutableList()
        val index = current.indexOfFirst { it.deviceId == endpoint.deviceId }
        if (index >= 0) {
            current[index] = endpoint
        } else {
            current.add(endpoint)
        }
        _discoveredEndpoints.value = current
        _lastSeenAtEpochMillis.value = _lastSeenAtEpochMillis.value +
            (endpoint.deviceId.lowercase() to System.currentTimeMillis())
    }
}
