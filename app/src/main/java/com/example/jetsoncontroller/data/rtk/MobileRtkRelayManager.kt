package com.example.jetsoncontroller.data.rtk

import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import com.example.jetsoncontroller.data.network.diagnosticFailure
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.system.ErrnoException
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
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
import java.util.concurrent.atomic.AtomicLong
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
    private var monitoring = false
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeRoutes = ConcurrentHashMap.newKeySet<RtkRelayRoute>()
    private val connectionSequence = AtomicLong()
    // Never close routes while holding this lock: route callbacks acquire it too.
    private val resourceLock = Any()
    private var currentSession: RelaySession? = null

    private class RelaySession(val job: Job) {
        var config: MobileRtkRelayConfig? = null
        var registrationError: String? = null
        var dataError: String? = null
    }

    private fun createSession(): RelaySession = synchronized(resourceLock) {
        RelaySession(SupervisorJob(scope.coroutineContext[Job])).also { currentSession = it }
    }

    private fun isCurrent(session: RelaySession): Boolean =
        currentSession === session && session.job.isActive

    private fun registerSocket(session: RelaySession, socket: Socket): Boolean {
        val accepted = synchronized(resourceLock) {
            if (isCurrent(session)) { activeSockets.add(socket); true } else false
        }
        if (!accepted) runCatching { socket.close() }
        return accepted
    }

    private fun registerRoute(session: RelaySession, route: RtkRelayRoute): Boolean {
        val accepted = synchronized(resourceLock) {
            if (isCurrent(session)) { activeRoutes.add(route); true } else false
        }
        if (!accepted) route.close()
        return accepted
    }

    private fun registrationResult(session: RelaySession, error: String?) = synchronized(resourceLock) {
        if (isCurrent(session)) {
            session.registrationError = error
            _state.value = _state.value.copy(error = session.dataError ?: error)
        }
    }

    private fun dataError(session: RelaySession, error: String) = synchronized(resourceLock) {
        if (isCurrent(session)) {
            session.dataError = error
            _state.value = _state.value.copy(error = error)
        }
    }

    sealed interface Preparation {
        data object NotRequired : Preparation

        class Ready internal constructor(
            val newlyCreated: Boolean,
            internal val server: ServerSocket
        ) : Preparation
    }

    suspend fun prepare(pipelineId: String, client: LocalApiClient): Result<Preparation> =
        prepare(pipelineId, client, forMonitoring = false)

    suspend fun prepareForMonitoring(pipelineId: String, client: LocalApiClient): Result<Preparation> =
        prepare(pipelineId, client, forMonitoring = true)

    private suspend fun prepare(
        pipelineId: String, client: LocalApiClient, forMonitoring: Boolean
    ): Result<Preparation> {
        return mutex.withLock {
            val existingServer = serverSocket
            if (monitoring && activeClient === client && _state.value.active &&
                existingServer != null && !existingServer.isClosed && _state.value.pipelineId != pipelineId
            ) {
                // An unrelated acquisition must never retire the monitor's socket,
                // including when its config is unavailable or the command is rejected.
                val session = currentSession
                return@withLock try {
                    val config = client.getMobileRtkRelayConfig(pipelineId).getOrThrow()
                    if (!config.available) return@withLock Result.success(Preparation.NotRequired)
                    val activeSession = requireNotNull(session)
                    val currentConfig = activeSession.config
                    require(currentConfig != null && currentConfig.upstreamHost == config.upstreamHost &&
                        currentConfig.upstreamPort == config.upstreamPort) {
                        "센서 모니터와 다른 NTRIP 설정의 중계를 동시에 사용할 수 없습니다."
                    }
                    client.registerMobileRtkRelay(pipelineId, existingServer.localPort).getOrThrow()
                    val previousPipeline = _state.value.pipelineId
                    _state.value = _state.value.copy(pipelineId = pipelineId)
                    startHeartbeat(pipelineId, existingServer.localPort, client, activeSession)
                    registrationResult(activeSession, null)
                    if (previousPipeline != null) client.unregisterMobileRtkRelay(previousPipeline)
                    Result.success(Preparation.Ready(newlyCreated = false, existingServer))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
            if (
                _state.value.active && _state.value.pipelineId == pipelineId &&
                activeClient === client && existingServer != null && !existingServer.isClosed
            ) {
                // Refreshing a shared relay does not acquire cleanup ownership.
                // A failed refresh must leave the running acquisition intact.
                val session = currentSession
                return@withLock try {
                    client.registerMobileRtkRelay(pipelineId, existingServer.localPort).getOrThrow()
                    monitoring = monitoring || forMonitoring
                    session?.let { registrationResult(it, null) }
                    Result.success(Preparation.Ready(newlyCreated = false, existingServer))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    session?.let { registrationResult(it, error.message ?: "RTK 중계 등록을 갱신하지 못했습니다.") }
                    Result.failure(error)
                }
            }
            try {
                val keepMonitoring = forMonitoring || (activeClient === client && monitoring)
                val prepared = prepareLocked(pipelineId, client)
                monitoring = prepared is Preparation.Ready && keepMonitoring
                Result.success(prepared)
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    stopWithServiceLocked(client)
                }
                throw error
            } catch (error: Exception) {
                // Cleanup remains under the same ownership lock. A failed old
                // prepare cannot release a newer client's relay between locks.
                withContext(NonCancellable) { stopWithServiceLocked(client) }
                _state.value = MobileRtkRelayState(
                    pipelineId = pipelineId,
                    error = if (error is SecurityException) "모바일 RTK 중계에 필요한 네트워크·알림 권한을 확인하세요."
                        else error.message ?: "모바일 RTK 중계를 시작하지 못했습니다."
                )
                Result.failure(error)
            }
        }
    }

    /** Deliberately stops the current relay, regardless of which client owns it. */
    suspend fun stop(client: LocalApiClient? = null) {
        mutex.withLock {
            stopWithServiceLocked(client ?: activeClient)
        }
    }

    /**
     * Stops only the relay still owned by [client]. A delayed cleanup from an
     * old transport session must not tear down a relay prepared for a new one.
     */
    suspend fun stopIfOwnedBy(client: LocalApiClient?): Boolean {
        return mutex.withLock {
            if (activeClient !== client) {
                false
            } else {
                stopWithServiceLocked(client)
                true
            }
        }
    }

    /** Acquisition completion cannot release the sensor monitor's shared relay. */
    suspend fun stopAfterAcquisition(client: LocalApiClient): Boolean = mutex.withLock {
        if (activeClient !== client || monitoring) false else {
            stopWithServiceLocked(client)
            true
        }
    }

    /** Cleanup is scoped to the exact relay observed or created by this prepare. */
    suspend fun stopIfPrepared(client: LocalApiClient, preparation: Preparation.Ready): Boolean {
        return mutex.withLock {
            if (activeClient !== client || serverSocket !== preparation.server || monitoring) {
                false
            } else {
                stopWithServiceLocked(client)
                true
            }
        }
    }

    private suspend fun stopWithServiceLocked(client: LocalApiClient?) {
        try {
            stopLocked(client)
        } finally {
            MobileRtkRelayService.stop(appContext)
        }
    }

    private suspend fun prepareLocked(
        pipelineId: String,
        client: LocalApiClient
    ): Preparation {
        releaseLocalResourcesLocked()
        val session = createSession()
        // Claim ownership before any cancellable network preparation so a
        // target-switch cleanup can identify this in-flight session.
        activeClient = client
        _state.value = MobileRtkRelayState(
            preparing = true,
            pipelineId = pipelineId,
            message = "셀룰러 RTK 경로를 준비하고 있습니다."
        )

        val config = client.getMobileRtkRelayConfig(pipelineId).getOrThrow()
        if (!config.available) {
            releaseLocalResourcesLocked()
            activeClient = null
            _state.value = MobileRtkRelayState(
                pipelineId = pipelineId,
                message = "이 작업에는 NTRIP이 설정되어 있지 않습니다."
            )
            MobileRtkRelayService.stop(appContext)
            return Preparation.NotRequired
        }
        val upstreamHost = requireNotNull(config.upstreamHost) {
            "NTRIP caster 주소가 없습니다."
        }
        require(config.upstreamPort != null) { "NTRIP caster 포트가 없습니다." }
        session.config = config

        val groupOwnerHost = wifiDirectManager.state.value.groupOwnerAddress
            ?: error("Wi-Fi Direct 그룹 주소를 확인할 수 없습니다.")
        val localAddress = wifiDirectManager.localAddressForGroupOwner(groupOwnerHost)
            ?: error("모바일의 Wi-Fi Direct 주소를 확인할 수 없습니다.")
        val lease = acquireCellularNetwork(session)
        try {
            // Registration alone must not hide a missing/unsafe internet route.
            awaitMobileInternet(lease.network)
        } catch (error: Exception) {
            runCatching { connectivityManager.unregisterNetworkCallback(lease.callback) }
            throw error
        }
        val server = try {
            val candidate = ServerSocket()
            try {
                withContext(Dispatchers.IO) {
                    candidate.apply {
                        reuseAddress = true
                        soTimeout = ACCEPT_POLL_TIMEOUT_MILLIS
                        bind(InetSocketAddress(localAddress, 0))
                    }
                }
            } catch (error: Exception) {
                // Dispatch cancellation can discard an already-bound return value.
                runCatching { candidate.close() }
                throw error
            }
        } catch (error: Exception) {
            runCatching { connectivityManager.unregisterNetworkCallback(lease.callback) }
            throw error
        }

        synchronized(resourceLock) { cellularLease = lease }
        serverSocket = server
        try {
            client.registerMobileRtkRelay(pipelineId, server.localPort).getOrThrow()
        } catch (error: Exception) {
            releaseLocalResourcesLocked()
            throw error
        }
        synchronized(resourceLock) {
            _state.value = MobileRtkRelayState(
                active = true,
                pipelineId = pipelineId,
                upstreamHost = upstreamHost,
                message = "RTK 중계를 등록했습니다. 보정 데이터와 RTK FIX는 별도 확인이 필요합니다.",
                error = session.dataError ?: session.registrationError
            )
        }
        acceptJob = scope.launch(session.job + Dispatchers.IO) {
            acceptConnections(server, lease.network, config, groupOwnerHost, session)
        }
        startHeartbeat(pipelineId, server.localPort, client, session)
        ContextCompat.startForegroundService(
            appContext,
            MobileRtkRelayService.startIntent(appContext)
        )
        return Preparation.Ready(newlyCreated = true, server)
    }

    private suspend fun stopLocked(client: LocalApiClient?) {
        val pipelineId = _state.value.pipelineId
        releaseLocalResourcesLocked()
        _state.value = MobileRtkRelayState(
            message = "모바일 데이터 RTK 중계를 종료했습니다."
        )
        if (pipelineId != null && client != null) {
            client.unregisterMobileRtkRelay(pipelineId)
        }
    }

    private fun startHeartbeat(
        pipelineId: String,
        port: Int,
        client: LocalApiClient,
        session: RelaySession
    ) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(session.job) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                val result = client.registerMobileRtkRelay(pipelineId, port)
                synchronized(resourceLock) {
                    val current = _state.value
                    if (isCurrent(session) && current.active) {
                        val error = result.exceptionOrNull()
                        ConnectionDiagnostics.record("rtk_heartbeat", mapOf(
                            "success" to result.isSuccess, "active" to true,
                            "bytesFromCaster" to current.bytesFromCaster,
                            "exceptionClass" to error?.let(::diagnosticFailure)
                        ), incident = result.isFailure)
                        registrationResult(session, error?.let {
                            it.message ?: "RTK 중계 등록을 갱신하지 못했습니다."
                        })
                    }
                }
            }
        }
    }

    private suspend fun acquireCellularNetwork(session: RelaySession): CellularLease =
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
                    invalidateCellular(session, this, network, "모바일 데이터 연결이 끊어졌습니다.")
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (!isRtkCellularRoute(caps, vpn = false)) {
                        invalidateCellular(session, this, network, "사용 가능한 모바일 데이터 연결이 없습니다.")
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

    private suspend fun awaitMobileInternet(cellular: Network) {
        // onAvailable can precede Android's internet validation/capability snapshot.
        // Keep the lease while validation settles, rather than repeatedly releasing it.
        repeat(20) {
            try {
                val route = selectRtkRelayNetwork(connectivityManager, cellular)
                if (connectivityManager.getNetworkCapabilities(route)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) return
            } catch (error: RtkRelayRouteException) {
                if (error.reason !in setOf("CELLULAR_UNAVAILABLE", "NETWORK_UNKNOWN")) throw error
            }
            delay(250)
        }
        throw IOException("인터넷에 연결된 모바일 데이터 경로가 없습니다. 모바일 데이터를 확인하세요.")
    }

    private fun invalidateCellular(
        session: RelaySession, callback: ConnectivityManager.NetworkCallback, network: Network, error: String
    ) {
        val routes = synchronized(resourceLock) {
            if (!isCurrent(session) || cellularLease?.callback !== callback || cellularLease?.network != network) return
            dataError(session, error)
            activeRoutes.toList()
        }

        routes.forEach { it.close() }
        retireSession(session, error, callback, network)
    }

    private fun retireSession(
        session: RelaySession, error: String,
        callback: ConnectivityManager.NetworkCallback? = null, network: Network? = null
    ) {
        scope.launch {
            mutex.withLock {
                val client = synchronized(resourceLock) {
                    if (!isCurrent(session) || (callback != null &&
                        (cellularLease?.callback !== callback || cellularLease?.network != network))) return@withLock
                    activeClient
                }
                val pipelineId = _state.value.pipelineId
                withContext(NonCancellable) { stopWithServiceLocked(client) }
                _state.value = MobileRtkRelayState(pipelineId = pipelineId, error = error)
            }
        }
    }

    private suspend fun acceptConnections(
        server: ServerSocket,
        cellularNetwork: Network,
        config: MobileRtkRelayConfig,
        allowedHost: String,
        session: RelaySession
    ) {
        traceRelay(0, RtkRelayPhase.LISTENING)
        while (session.job.isActive && !server.isClosed) {
            val downstream = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (error: IOException) {
                if (!server.isClosed) {
                    traceRelay(0, RtkRelayPhase.ACCEPT_FAILED, error = error)
                }
                break
            }
            if (!registerSocket(session, downstream)) break
            val connectionId = connectionSequence.incrementAndGet()
            val peer = downstream.inetAddress
            val peerBytes = peer.address
            val decision = checkRtkRelayPeer(peerBytes, allowedHost)
            // Compare the old predicate without logging either address. ALLOWED + legacyMatch=false
            // establishes that the former string check would have rejected this exact connection.
            traceRelay(
                connectionId, RtkRelayPhase.PEER_CHECK,
                "decision=$decision peerKind=${rtkRelayPeerKind(peerBytes)} " +
                    "legacyMatch=${peer.hostAddress == allowedHost}",
                success = decision == RtkRelayPeerDecision.ALLOWED
            )
            if (decision != RtkRelayPeerDecision.ALLOWED) {
                runCatching { downstream.close() }
                activeSockets.remove(downstream)
                continue
            }
            scope.launch(session.job + Dispatchers.IO) {
                relayConnection(downstream, cellularNetwork, config, connectionId, session)
            }
        }
    }

    private suspend fun relayConnection(
        downstream: Socket,
        cellularNetwork: Network,
        config: MobileRtkRelayConfig,
        connectionId: Long,
        session: RelaySession
    ) {
        val upstreamHost = config.upstreamHost ?: return downstream.close()
        val upstreamPort = config.upstreamPort ?: return downstream.close()
        if (!registerSocket(session, downstream)) return
        var route: RtkRelayRoute? = null
        val upstream = try {
            val selected = RtkRelayRoute(connectivityManager, cellularNetwork) { error ->
                traceRelay(connectionId, RtkRelayPhase.ROUTE_REJECTED, error = error)
                dataError(session, error.message ?: "RTK 네트워크 경로가 변경되었습니다.")
            }
            route = selected
            if (!registerRoute(session, selected)) throw CancellationException("Relay session stopped")
            selected.attach(downstream)
            selected.start()
            traceRelay(connectionId, RtkRelayPhase.ROUTE_SELECTED,
                "viaVpn=${selected.network != cellularNetwork}")
            connectOnNetwork(selected.network, upstreamHost, upstreamPort, connectionId, selected, session)
        } catch (error: Exception) {
            route?.let { it.close(); activeRoutes.remove(it) }
            traceRelay(connectionId, RtkRelayPhase.UPSTREAM_FAILED, error = error)
            runCatching { downstream.close() }
            activeSockets.remove(downstream)
            dataError(session,
                when (error) {
                    is RtkRelayRouteException -> error.message ?: "RTK 네트워크 경로가 변경되었습니다."
                    is SecurityException -> "RTK 소켓 연결 권한이 없습니다."
                    else -> "NTRIP caster 연결 실패 (${diagnosticFailure(error)})."
                }
            )
            if (error is SecurityException ||
                (error is RtkRelayRouteException && error.reason == "PERMISSION_DENIED")) {
                retireSession(session, "RTK 네트워크 경로 확인 권한이 없습니다.")
            }
            return
        }

        try {
            downstream.use { local ->
                upstream.use { remote ->
                    runCatching {
                        coroutineScope {
                            listOf(
                                async(Dispatchers.IO) {
                                    forwardNtripRequest(local, remote, upstreamHost, upstreamPort, connectionId)
                                    runCatching { remote.shutdownOutput() }
                                },
                                async(Dispatchers.IO) {
                                    copyCasterData(remote, local, connectionId, session)
                                    runCatching { local.shutdownOutput() }
                                }
                            ).awaitAll()
                        }
                    }.onFailure { error ->
                        if (error !is CancellationException) {
                            traceRelay(connectionId, RtkRelayPhase.STREAM_FAILED, error = error)
                            dataError(session, "NTRIP caster 데이터 중계가 중단되었습니다.")
                        }
                    }
                }
            }
        } finally {
            route?.let { it.close(); activeRoutes.remove(it) }
            traceRelay(connectionId, RtkRelayPhase.CLOSED)
            activeSockets.remove(downstream)
            activeSockets.remove(upstream)
        }
    }

    private fun connectOnNetwork(
        network: Network, host: String, port: Int, connectionId: Long, route: RtkRelayRoute?, session: RelaySession
    ): Socket {
        var lastError: IOException? = null
        route?.check()
        traceRelay(connectionId, RtkRelayPhase.DNS_STARTED)
        val addresses = network.getAllByName(host)
        traceRelay(connectionId, RtkRelayPhase.DNS_READY, "addressCount=${addresses.size}")
        for ((index, address) in addresses.withIndex()) {
            var socket: Socket? = null
            var socketCreated = false
            try {
                route?.check()
                traceRelay(connectionId, RtkRelayPhase.SOCKET_STARTED, "attempt=$index")
                socket = network.socketFactory.createSocket()
                socketCreated = true
                if (!registerSocket(session, socket)) throw CancellationException("Relay session stopped")
                route?.attach(socket)
                route?.check()
                traceRelay(connectionId, RtkRelayPhase.CONNECT_STARTED, "attempt=$index")
                socket.connect(InetSocketAddress(address, port), UPSTREAM_CONNECT_TIMEOUT_MILLIS)
                socket.tcpNoDelay = true
                route?.check()
                traceRelay(connectionId, RtkRelayPhase.UPSTREAM_CONNECTED, success = true)
                return socket
            } catch (error: Exception) {
                traceRelay(connectionId,
                    when {
                        error is RtkRelayRouteException -> RtkRelayPhase.ROUTE_REJECTED
                        socketCreated -> RtkRelayPhase.CONNECT_FAILED
                        else -> RtkRelayPhase.SOCKET_FAILED
                    },
                    "attempt=$index", error = error)
                socket?.let { runCatching { it.close() }; activeSockets.remove(it) }
                if (error !is IOException || error is RtkRelayRouteException) throw error
                lastError = error
            }
        }
        throw lastError ?: IOException("NTRIP caster 주소를 확인할 수 없습니다.")
    }

    private fun forwardNtripRequest(
        downstream: Socket,
        upstream: Socket,
        upstreamHost: String,
        upstreamPort: Int,
        connectionId: Long
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
                traceRelay(connectionId, RtkRelayPhase.REQUEST_FORWARDED, success = true)
                input.copyTo(output, RELAY_BUFFER_BYTES)
                return
            }
        }
        if (buffered.size() > 0) {
            output.write(buffered.toByteArray())
            output.flush()
        }
        traceRelay(connectionId, RtkRelayPhase.INCOMPLETE_HEADER, "bufferedBytes=${buffered.size()}")
    }

    private fun copyCasterData(upstream: Socket, downstream: Socket, connectionId: Long, session: RelaySession) {
        val input = upstream.getInputStream()
        val output = downstream.getOutputStream()
        val buffer = ByteArray(RELAY_BUFFER_BYTES)
        val response = NtripCasterResponseTracker()
        var receivedBytes = 0L
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    traceRelay(connectionId, RtkRelayPhase.CASTER_EOF, "receivedBytes=$receivedBytes")
                    synchronized(resourceLock) {
                        dataError(session, response.error
                            ?: session.dataError?.takeIf { response.validatedBytes == 0L }
                            ?: response.endOfInput())
                    }
                    break
                }
                if (count == 0) continue
                if (receivedBytes == 0L) {
                    traceRelay(connectionId, RtkRelayPhase.CASTER_FIRST_BYTES)
                }
                receivedBytes += count
                val verifiedBytes = response.accept(buffer, 0, count)
                response.error?.let { dataError(session, it) }
                // Forward the exact upstream bytes, including HTTP/chunk framing and errors.
                output.write(buffer, 0, count)
                output.flush()
                if (verifiedBytes == 0L) continue
                synchronized(resourceLock) {
                    val current = _state.value
                    if (isCurrent(session) && current.active) {
                        if (response.error == null) session.dataError = null
                        _state.value = current.copy(bytesFromCaster = current.bytesFromCaster + verifiedBytes,
                            error = session.dataError ?: session.registrationError)
                    }
                }
            }
        } catch (error: IOException) {
            val responseError = response.error ?: throw error
            traceRelay(connectionId, RtkRelayPhase.STREAM_FAILED, error = error)
            // A reset/closed socket after an HTTP rejection must not hide that rejection.
            dataError(session, responseError)
        }
    }

    /** Finite stages, booleans and counters only: never log hosts, requests or exception messages. */
    private fun traceRelay(
        connectionId: Long,
        phase: RtkRelayPhase,
        details: String = "",
        success: Boolean? = null,
        error: Throwable? = null
    ) {
        val failure = error?.let(::diagnosticFailure)
        val errno = generateSequence(error) { it.cause }.take(8)
            .filterIsInstance<ErrnoException>().firstOrNull()?.errno
        Log.i(LOG_TAG, "connection=$connectionId phase=$phase $details" +
            (failure?.let { " failure=$it exceptionClass=${error.javaClass.simpleName}" } ?: "") +
            (errno?.let { " errno=$it" } ?: "") +
            ((error as? RtkRelayRouteException)?.let { " reason=${it.reason}" } ?: ""))
        ConnectionDiagnostics.record("rtk_state", mapOf(
            "generation" to connectionId, "reasonCode" to phase, "success" to success,
            "exceptionClass" to failure
        ), incident = error != null)
    }

    private fun releaseLocalResourcesLocked() {
        val resources = synchronized(resourceLock) {
            val session = currentSession
            currentSession = null
            Triple(session, activeRoutes.toList(), activeSockets.toList()).also {
                activeRoutes.clear()
                activeSockets.clear()
            }
        }
        resources.first?.job?.cancel()
        heartbeatJob?.cancel()
        heartbeatJob = null
        acceptJob?.cancel()
        acceptJob = null
        resources.second.forEach { it.close() }
        runCatching { serverSocket?.close() }
        serverSocket = null
        resources.third.forEach { socket -> runCatching { socket.close() } }
        cellularLease?.let { lease ->
            runCatching { connectivityManager.unregisterNetworkCallback(lease.callback) }
        }
        cellularLease = null
        activeClient = null
        monitoring = false
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

private enum class RtkRelayPhase {
    LISTENING, ACCEPT_FAILED, PEER_CHECK, ROUTE_SELECTED, ROUTE_REJECTED,
    DNS_STARTED, DNS_READY, SOCKET_STARTED, SOCKET_FAILED, CONNECT_STARTED,
    CONNECT_FAILED, UPSTREAM_CONNECTED, UPSTREAM_FAILED, REQUEST_FORWARDED,
    INCOMPLETE_HEADER, CASTER_FIRST_BYTES, CASTER_EOF, STREAM_FAILED, CLOSED
}
