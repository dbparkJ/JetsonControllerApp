package com.example.jetsoncontroller.data.rtk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.jetsoncontroller.data.network.LocalApiClient
import com.example.jetsoncontroller.data.network.WifiDirectManager
import com.example.jetsoncontroller.model.MobileRtkRelayConfig
import com.example.jetsoncontroller.model.MobileRtkRelayState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MobileRtkRelayManager(
    context: Context,
    private val wifiDirectManager: WifiDirectManager,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mutex = Mutex()
    private val _state = MutableStateFlow(MobileRtkRelayState())
    val state: StateFlow<MobileRtkRelayState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var heartbeatJob: Job? = null
    private var cellularLease: CellularLease? = null
    private var activeClient: LocalApiClient? = null
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    suspend fun prepare(pipelineId: String, client: LocalApiClient): Result<Boolean> {
        return try {
            Result.success(
                mutex.withLock {
                    prepareLocked(pipelineId, client)
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutex.withLock { releaseLocalResourcesLocked() }
            _state.value = MobileRtkRelayState(
                pipelineId = pipelineId,
                error = error.message ?: "모바일 RTK 중계를 시작하지 못했습니다."
            )
            Result.failure(error)
        }
    }

    suspend fun stop(client: LocalApiClient? = activeClient) {
        mutex.withLock {
            val pipelineId = _state.value.pipelineId
            releaseLocalResourcesLocked()
            if (pipelineId != null && client != null) {
                client.unregisterMobileRtkRelay(pipelineId)
            }
            _state.value = MobileRtkRelayState(
                message = "모바일 데이터 RTK 중계를 종료했습니다."
            )
        }
        MobileRtkRelayService.stop(appContext)
    }

    private suspend fun prepareLocked(
        pipelineId: String,
        client: LocalApiClient
    ): Boolean {
        val existingServer = serverSocket
        if (
            _state.value.active &&
            _state.value.pipelineId == pipelineId &&
            existingServer != null &&
            !existingServer.isClosed
        ) {
            client.registerMobileRtkRelay(pipelineId, existingServer.localPort).getOrThrow()
            return true
        }

        releaseLocalResourcesLocked()
        _state.value = MobileRtkRelayState(
            preparing = true,
            pipelineId = pipelineId,
            message = "셀룰러 RTK 경로를 준비하고 있습니다."
        )

        val config = client.getMobileRtkRelayConfig(pipelineId).getOrThrow()
        if (!config.available) {
            _state.value = MobileRtkRelayState(
                pipelineId = pipelineId,
                message = "이 작업에는 NTRIP이 설정되어 있지 않습니다."
            )
            return false
        }
        val upstreamHost = requireNotNull(config.upstreamHost) {
            "NTRIP caster 주소가 없습니다."
        }
        require(config.upstreamPort != null) { "NTRIP caster 포트가 없습니다." }

        val groupOwnerHost = wifiDirectManager.state.value.groupOwnerAddress
            ?: error("Wi-Fi Direct 그룹 주소를 확인할 수 없습니다.")
        val localAddress = wifiDirectManager.localAddressForGroupOwner(groupOwnerHost)
            ?: error("모바일의 Wi-Fi Direct 주소를 확인할 수 없습니다.")
        val lease = acquireCellularNetwork()
        val server = try {
            withContext(Dispatchers.IO) {
                ServerSocket().apply {
                    reuseAddress = true
                    soTimeout = ACCEPT_POLL_TIMEOUT_MILLIS
                    bind(InetSocketAddress(localAddress, 0))
                }
            }
        } catch (error: Exception) {
            runCatching { connectivityManager.unregisterNetworkCallback(lease.callback) }
            throw error
        }

        cellularLease = lease
        serverSocket = server
        activeClient = client
        acceptJob = scope.launch(Dispatchers.IO) {
            acceptConnections(server, lease.network, config, groupOwnerHost)
        }

        try {
            client.registerMobileRtkRelay(pipelineId, server.localPort).getOrThrow()
        } catch (error: Exception) {
            releaseLocalResourcesLocked()
            throw error
        }
        startHeartbeat(pipelineId, server.localPort, client)
        _state.value = MobileRtkRelayState(
            active = true,
            pipelineId = pipelineId,
            upstreamHost = upstreamHost,
            message = "모바일 셀룰러 데이터로 RTK를 중계 중입니다."
        )
        ContextCompat.startForegroundService(
            appContext,
            MobileRtkRelayService.startIntent(appContext)
        )
        return true
    }

    private fun startHeartbeat(
        pipelineId: String,
        port: Int,
        client: LocalApiClient
    ) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                client.registerMobileRtkRelay(pipelineId, port)
                    .onFailure { error ->
                        val current = _state.value
                        if (current.active && current.pipelineId == pipelineId) {
                            _state.value = current.copy(
                                error = error.message ?: "RTK 중계 등록을 갱신하지 못했습니다."
                            )
                        }
                    }
                    .onSuccess {
                        val current = _state.value
                        if (current.active && current.pipelineId == pipelineId) {
                            _state.value = current.copy(error = null)
                        }
                    }
            }
        }
    }

    private suspend fun acquireCellularNetwork(): CellularLease =
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            lateinit var callback: ConnectivityManager.NetworkCallback
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (completed.compareAndSet(false, true)) {
                        continuation.resume(CellularLease(network, callback))
                    }
                }

                override fun onUnavailable() {
                    if (completed.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            IOException("사용 가능한 모바일 데이터 연결이 없습니다.")
                        )
                    }
                }

                override fun onLost(network: Network) {
                    if (cellularLease?.network == network) {
                        _state.value = _state.value.copy(
                            error = "모바일 데이터 연결이 끊어졌습니다."
                        )
                    }
                }
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            try {
                connectivityManager.requestNetwork(
                    request,
                    callback,
                    CELLULAR_REQUEST_TIMEOUT_MILLIS
                )
            } catch (error: Exception) {
                if (completed.compareAndSet(false, true)) {
                    continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }

    private suspend fun acceptConnections(
        server: ServerSocket,
        cellularNetwork: Network,
        config: MobileRtkRelayConfig,
        allowedHost: String
    ) {
        while (scope.isActive && !server.isClosed) {
            val downstream = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (error: IOException) {
                if (!server.isClosed) {
                    Log.w(LOG_TAG, "RTK relay accept failed", error)
                }
                break
            }
            if (downstream.inetAddress.hostAddress != allowedHost) {
                downstream.close()
                continue
            }
            scope.launch(Dispatchers.IO) {
                relayConnection(downstream, cellularNetwork, config)
            }
        }
    }

    private suspend fun relayConnection(
        downstream: Socket,
        cellularNetwork: Network,
        config: MobileRtkRelayConfig
    ) {
        val upstreamHost = config.upstreamHost ?: return downstream.close()
        val upstreamPort = config.upstreamPort ?: return downstream.close()
        activeSockets.add(downstream)
        val upstream = try {
            connectOnNetwork(cellularNetwork, upstreamHost, upstreamPort)
        } catch (error: Exception) {
            downstream.close()
            activeSockets.remove(downstream)
            _state.value = _state.value.copy(
                error = "NTRIP caster 연결 실패: ${error.message ?: "응답 없음"}"
            )
            return
        }
        activeSockets.add(upstream)

        try {
            downstream.use { local ->
                upstream.use { remote ->
                    runCatching {
                        coroutineScope {
                            listOf(
                                async(Dispatchers.IO) {
                                    forwardNtripRequest(local, remote, upstreamHost, upstreamPort)
                                    runCatching { remote.shutdownOutput() }
                                },
                                async(Dispatchers.IO) {
                                    copyCasterData(remote, local)
                                    runCatching { local.shutdownOutput() }
                                }
                            ).awaitAll()
                        }
                    }.onFailure { error ->
                        if (error !is CancellationException) {
                            Log.w(LOG_TAG, "RTK relay connection ended", error)
                        }
                    }
                }
            }
        } finally {
            activeSockets.remove(downstream)
            activeSockets.remove(upstream)
        }
    }

    private fun connectOnNetwork(network: Network, host: String, port: Int): Socket {
        var lastError: IOException? = null
        for (address in network.getAllByName(host)) {
            val socket = network.socketFactory.createSocket()
            try {
                socket.connect(InetSocketAddress(address, port), UPSTREAM_CONNECT_TIMEOUT_MILLIS)
                socket.tcpNoDelay = true
                return socket
            } catch (error: IOException) {
                lastError = error
                runCatching { socket.close() }
            }
        }
        throw lastError ?: IOException("NTRIP caster 주소를 확인할 수 없습니다.")
    }

    private fun forwardNtripRequest(
        downstream: Socket,
        upstream: Socket,
        upstreamHost: String,
        upstreamPort: Int
    ) {
        val input = downstream.getInputStream()
        val output = upstream.getOutputStream()
        val buffered = ByteArrayOutputStream()
        val chunk = ByteArray(2048)
        while (buffered.size() < MAX_NTRIP_HEADER_BYTES) {
            val count = input.read(chunk)
            if (count < 0) break
            buffered.write(chunk, 0, count)
            val current = buffered.toByteArray()
            if (findHeaderEnd(current) >= 0) {
                output.write(rewriteNtripHostHeader(current, upstreamHost, upstreamPort))
                output.flush()
                input.copyTo(output, RELAY_BUFFER_BYTES)
                return
            }
        }
        if (buffered.size() > 0) {
            output.write(buffered.toByteArray())
            output.flush()
        }
    }

    private fun copyCasterData(upstream: Socket, downstream: Socket) {
        val input = upstream.getInputStream()
        val output = downstream.getOutputStream()
        val buffer = ByteArray(RELAY_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            output.flush()
            val current = _state.value
            if (current.active) {
                _state.value = current.copy(bytesFromCaster = current.bytesFromCaster + count)
            }
        }
    }

    private fun releaseLocalResourcesLocked() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeSockets.toList().forEach { socket -> runCatching { socket.close() } }
        activeSockets.clear()
        cellularLease?.let { lease ->
            runCatching { connectivityManager.unregisterNetworkCallback(lease.callback) }
        }
        cellularLease = null
        activeClient = null
    }

    private data class CellularLease(
        val network: Network,
        val callback: ConnectivityManager.NetworkCallback
    )
}

internal fun rewriteNtripHostHeader(
    request: ByteArray,
    upstreamHost: String,
    upstreamPort: Int
): ByteArray {
    val headerEnd = findHeaderEnd(request)
    if (headerEnd < 0) return request
    val separatorSize = if (
        headerEnd + 3 < request.size &&
        request[headerEnd] == '\r'.code.toByte()
    ) 4 else 2
    val header = request.copyOfRange(0, headerEnd)
        .toString(Charsets.ISO_8859_1)
    val lineSeparator = if (header.contains("\r\n")) "\r\n" else "\n"
    val hostValue = if (upstreamPort == 80) upstreamHost else "$upstreamHost:$upstreamPort"
    val lines = header.split(lineSeparator).toMutableList()
    val hostIndex = lines.indexOfFirst { it.startsWith("Host:", ignoreCase = true) }
    if (hostIndex >= 0) {
        lines[hostIndex] = "Host: $hostValue"
    } else if (lines.isNotEmpty()) {
        lines.add(1, "Host: $hostValue")
    }
    val rewrittenHeader = (lines.joinToString(lineSeparator) + lineSeparator + lineSeparator)
        .toByteArray(Charsets.ISO_8859_1)
    val bodyStart = headerEnd + separatorSize
    return rewrittenHeader + request.copyOfRange(bodyStart, request.size)
}

private fun findHeaderEnd(data: ByteArray): Int {
    for (index in 0..data.size - 4) {
        if (
            data[index] == '\r'.code.toByte() &&
            data[index + 1] == '\n'.code.toByte() &&
            data[index + 2] == '\r'.code.toByte() &&
            data[index + 3] == '\n'.code.toByte()
        ) return index
    }
    for (index in 0..data.size - 2) {
        if (data[index] == '\n'.code.toByte() && data[index + 1] == '\n'.code.toByte()) {
            return index
        }
    }
    return -1
}

private const val LOG_TAG = "MobileRtkRelay"
private const val CELLULAR_REQUEST_TIMEOUT_MILLIS = 15_000
private const val UPSTREAM_CONNECT_TIMEOUT_MILLIS = 10_000
private const val ACCEPT_POLL_TIMEOUT_MILLIS = 1_000
private const val HEARTBEAT_INTERVAL_MILLIS = 10_000L
private const val MAX_NTRIP_HEADER_BYTES = 64 * 1024
private const val RELAY_BUFFER_BYTES = 16 * 1024
