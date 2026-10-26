package com.example.jetsoncontroller.data.rtk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.system.ErrnoException
import android.util.Log
import com.example.jetsoncontroller.data.network.WifiDirectManager
import com.example.jetsoncontroller.model.MobileRtkRelayConfig
import com.example.jetsoncontroller.model.MobileRtkRelayState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import javax.net.SocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext

class RtkRelayConnectionTest {
    @Test
    fun `stop immediately after accept closes socket before delayed relay can run`() = fixture { manager, _ ->
        val server = Mockito.mock(ServerSocket::class.java)
        val downstream = Mockito.mock(Socket::class.java)
        val cellular = Mockito.mock(Network::class.java)
        val replacement = MobileRtkRelayState(active = true, pipelineId = "new", error = "new error")
        Mockito.`when`(server.accept()).thenReturn(downstream)
        Mockito.`when`(downstream.inetAddress).thenAnswer {
            manager.invoke("releaseLocalResourcesLocked")
            manager.invoke("createSession")
            manager.setState(replacement)
            InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 49, 1))
        }
        manager.invoke("acceptConnections", server, cellular,
            MobileRtkRelayConfig("old", true, "private.caster", 2101), "192.168.49.1", continuation())
        Mockito.verify(downstream, Mockito.atLeastOnce()).close()
        Mockito.verifyNoInteractions(manager.cm(), cellular)
        assertEquals(replacement, manager.state.value)
        assertTrue(manager.resources("activeSockets").isEmpty())
        assertTrue(manager.resources("activeRoutes").isEmpty())
    }

    @Test
    fun `cellular callback allows unvalidated NOT VPN but rejects lost internet`() = fixture { manager, _ ->
        val cellular = Mockito.mock(Network::class.java)
        val callback = manager.acquireLease(cellular)
        val unvalidated = cellularCaps()
        Mockito.`when`(unvalidated.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(false)
        callback.onCapabilitiesChanged(cellular, unvalidated)
        assertNull(manager.state.value.error)
        Mockito.`when`(unvalidated.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(false)
        callback.onCapabilitiesChanged(cellular, unvalidated)
        assertNotNull(manager.state.value.error)
    }

    @Test
    fun `retired cellular callback cannot invalidate new lease on the same network`() = fixture { manager, _ ->
        val cellular = Mockito.mock(Network::class.java)
        val oldCallback = manager.acquireLease(cellular)
        manager.invoke("releaseLocalResourcesLocked")
        manager.invoke("createSession")
        val newCallback = manager.acquireLease(cellular)
        val replacement = MobileRtkRelayState(active = true, pipelineId = "new", bytesFromCaster = 8)
        manager.setState(replacement)
        oldCallback.onLost(cellular)
        oldCallback.onCapabilitiesChanged(cellular, Mockito.mock(NetworkCapabilities::class.java))
        assertEquals(replacement, manager.state.value)
        newCallback.onLost(cellular)
        assertNotNull(manager.state.value.error)
    }

    private fun MobileRtkRelayManager.acquireLease(network: Network): ConnectivityManager.NetworkCallback {
        var callback: ConnectivityManager.NetworkCallback? = null
        Mockito.doAnswer {
            callback = it.getArgument(1)
            callback!!.onAvailable(network)
            null
        }.`when`(cm()).requestNetwork(Mockito.any(NetworkRequest::class.java),
            Mockito.any(ConnectivityManager.NetworkCallback::class.java), Mockito.eq(15_000))
        Mockito.mockConstruction(NetworkRequest.Builder::class.java) { builder, _ ->
            Mockito.`when`(builder.addTransportType(Mockito.anyInt())).thenReturn(builder)
            Mockito.`when`(builder.addCapability(Mockito.anyInt())).thenReturn(builder)
            Mockito.`when`(builder.build()).thenReturn(Mockito.mock(NetworkRequest::class.java))
        }.use {
            val lease = invoke("acquireCellularNetwork", continuation())
            javaClass.getDeclaredField("cellularLease").apply { isAccessible = true }.set(this, lease)
        }
        return callback!!
    }

    @Test
    fun `relay dispatched after stop cannot register old sockets routes or state`() = fixture { manager, _ ->
        val oldSession = manager.session()
        val downstream = Mockito.mock(Socket::class.java)
        val cellular = Mockito.mock(Network::class.java)
        manager.invoke("registerSocket", oldSession, downstream)
        manager.invoke("releaseLocalResourcesLocked")
        manager.invoke("createSession")
        val replacement = manager.state.value.copy(pipelineId = "new", error = "new error", bytesFromCaster = 17)
        manager.setState(replacement)
        manager.invoke("relayConnection", downstream, cellular,
            MobileRtkRelayConfig("old", true, "private.caster", 2101), 1L, oldSession, continuation())
        Mockito.verify(downstream, Mockito.atLeastOnce()).close()
        Mockito.verifyNoInteractions(manager.cm(), cellular)
        assertEquals(replacement, manager.state.value)
        assertTrue(manager.resources("activeSockets").isEmpty())
        assertTrue(manager.resources("activeRoutes").isEmpty())
    }

    @Test
    fun `stop during route selection rejects the late route before observer registration`() = fixture { manager, _ ->
        val cellular = Mockito.mock(Network::class.java)
        val cm = manager.cm()
        Mockito.doReturn(cellularCaps()).`when`(cm).getNetworkCapabilities(cellular)
        Mockito.`when`(cm.activeNetwork).thenAnswer {
            manager.invoke("releaseLocalResourcesLocked")
            manager.invoke("createSession")
            manager.setState(MobileRtkRelayState(active = true, pipelineId = "new", error = "new error"))
            cellular
        }
        val downstream = Mockito.mock(Socket::class.java)
        manager.invoke("relayConnection", downstream, cellular,
            MobileRtkRelayConfig("old", true, "private.caster", 2101), 1L, continuation())
        Mockito.verify(cm, Mockito.never()).registerDefaultNetworkCallback(Mockito.any(ConnectivityManager.NetworkCallback::class.java))
        Mockito.verify(cellular, Mockito.never()).getAllByName(Mockito.anyString())
        assertEquals("new error", manager.state.value.error)
        assertTrue(manager.resources("activeRoutes").isEmpty())
        assertTrue(manager.resources("activeSockets").isEmpty())
    }

    @Test
    fun `socket returned by factory after stop is closed and cannot enter replacement session`() = fixture { manager, _ ->
        val network = networkWithAddresses()
        val factory = Mockito.mock(SocketFactory::class.java)
        val socket = Mockito.mock(Socket::class.java)
        Mockito.`when`(network.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenAnswer {
            manager.invoke("releaseLocalResourcesLocked")
            manager.invoke("createSession")
            socket
        }
        assertTrue(catching { manager.connect(network) } is kotlinx.coroutines.CancellationException)
        Mockito.verify(socket, Mockito.atLeastOnce()).close()
        Mockito.verify(socket, Mockito.never()).connect(Mockito.any(), Mockito.anyInt())
        assertTrue(manager.resources("activeSockets").isEmpty())
    }

    @Test
    fun `late old stream and error completions cannot change replacement state`() = fixture { manager, _ ->
        val oldSession = manager.session()
        manager.invoke("releaseLocalResourcesLocked")
        manager.invoke("createSession")
        val replacement = MobileRtkRelayState(active = true, pipelineId = "new", bytesFromCaster = 99, error = "new error")
        manager.setState(replacement)
        manager.invoke("registrationResult", oldSession, null)
        manager.invoke("registrationResult", oldSession, "old heartbeat")
        manager.invoke("dataError", oldSession, "old route")
        val upstream = Mockito.mock(Socket::class.java)
        val downstream = Mockito.mock(Socket::class.java)
        Mockito.`when`(upstream.getInputStream()).thenReturn(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        Mockito.`when`(downstream.getOutputStream()).thenReturn(ByteArrayOutputStream())
        manager.invoke("copyCasterData", upstream, downstream, 1L, oldSession)
        assertEquals(replacement, manager.state.value)
    }

    @Test
    fun `data recovery clears data error but preserves registration error until registration recovers`() = fixture { manager, _ ->
        val session = manager.session()
        manager.invoke("dataError", session, "caster failed")
        manager.invoke("registrationResult", session, "registration failed")
        assertEquals("caster failed", manager.state.value.error)
        manager.invoke("registrationResult", session, null)
        assertEquals("caster failed", manager.state.value.error)
        manager.invoke("registrationResult", session, "registration failed")
        val upstream = Mockito.mock(Socket::class.java)
        val downstream = Mockito.mock(Socket::class.java)
        val frame = validRtcm3Frame()
        val wire = ascii("ICY 200 OK\r\n") + frame
        val output = ByteArrayOutputStream()
        Mockito.`when`(upstream.getInputStream()).thenReturn(object : ByteArrayInputStream(wire) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (available() == 0) {
                    assertEquals("registration failed", manager.state.value.error)
                    assertEquals(frame.size.toLong(), manager.state.value.bytesFromCaster)
                    manager.invoke("registrationResult", session, null)
                    assertNull(manager.state.value.error)
                }
                return super.read(buffer, offset, length)
            }
        })
        Mockito.`when`(downstream.getOutputStream()).thenReturn(output)
        manager.invoke("copyCasterData", upstream, downstream, 1L)
        assertArrayEquals(wire, output.toByteArray())
        assertTrue(manager.state.value.error.orEmpty().contains("수신이 종료"))
    }

    private fun MobileRtkRelayManager.cm() = javaClass.getDeclaredField("connectivityManager")
        .apply { isAccessible = true }.get(this) as ConnectivityManager

    private fun MobileRtkRelayManager.resources(name: String) = javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(this) as Set<*>

    @Suppress("UNCHECKED_CAST")
    private fun MobileRtkRelayManager.setState(value: MobileRtkRelayState) {
        (javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(this)
            as MutableStateFlow<MobileRtkRelayState>).value = value
    }

    private fun continuation() = object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
    }

    @Test
    fun `accept loop closes another mapped peer without touching cellular network`() {
        val peer = Inet6Address.getByAddress(null, ByteArray(12).apply {
            this[10] = 0xff.toByte(); this[11] = 0xff.toByte()
        } + byteArrayOf(192.toByte(), 168.toByte(), 49, 2), -1)
        assertRejectedPeer(peer, "192.168.49.1", "DIFFERENT_PEER")
    }

    @Test
    fun `accept loop closes native IPv6 without touching cellular network`() {
        assertRejectedPeer(Inet6Address.getByAddress(null, ByteArray(16).apply { this[15] = 1 }, -1),
            "192.168.49.1", "UNSUPPORTED_PEER")
    }

    @Test
    fun `accept loop fails closed for a nonnumeric owner without touching cellular network`() {
        assertRejectedPeer(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 49, 1)),
            "private.caster", "INVALID_OWNER")
    }

    @Test
    fun `network bound DNS failure is distinguishable without revealing host or exception message`() = fixture { manager, logs ->
        val network = Mockito.mock(Network::class.java)
        Mockito.`when`(network.getAllByName("private.caster")).thenThrow(UnknownHostException("private-secret"))
        assertTrue(catching { manager.connect(network) } is UnknownHostException)
        assertTrue(logs.any { "phase=DNS_STARTED" in it })
        assertFalse(logs.any { "phase=DNS_READY" in it })
        assertFalse(logs.any { "private" in it })
    }

    @Test
    fun `network socket factory failure is distinguishable from DNS and connect`() = fixture { manager, logs ->
        val network = networkWithAddresses()
        val factory = Mockito.mock(SocketFactory::class.java)
        Mockito.`when`(network.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenThrow(IOException("private-secret"))
        assertTrue(catching { manager.connect(network) } is IOException)
        assertTrue(logs.any { "phase=SOCKET_STARTED" in it })
        assertFalse(logs.any { "phase=CONNECT_STARTED" in it })
        assertFalse(logs.any { "private" in it })
    }

    @Test
    fun `connect failure closes the socket and tries the next address on the same network`() = fixture { manager, logs ->
        val network = networkWithAddresses(2)
        val factory = Mockito.mock(SocketFactory::class.java)
        val failed = Mockito.mock(Socket::class.java)
        val connected = Mockito.mock(Socket::class.java)
        Mockito.`when`(network.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenReturn(failed, connected)
        Mockito.doThrow(IOException("private-secret")).`when`(failed).connect(Mockito.any(), Mockito.eq(10_000))
        assertSame(connected, manager.connect(network))
        Mockito.verify(failed).close()
        Mockito.verify(connected).connect(InetSocketAddress(address(2), 2101), 10_000)
        Mockito.verify(connected).tcpNoDelay = true
        assertTrue(logs.any { "phase=CONNECT_FAILED" in it && "attempt=0" in it && "failure=IO" in it })
        assertTrue(logs.any { "phase=UPSTREAM_CONNECTED" in it })
        assertFalse(logs.any { "private" in it || "192.0.2." in it })
    }

    @Test
    fun `empty DNS answer fails before any socket is created`() = fixture { manager, logs ->
        val network = networkWithAddresses(0)
        assertTrue(catching { manager.connect(network) } is IOException)
        assertTrue(logs.any { "addressCount=0" in it })
        Mockito.verify(network, Mockito.never()).socketFactory
    }

    @Test
    fun `socket creation failure for IPv6 still tries IPv4`() = fixture { manager, logs ->
        val network = networkWithAddresses()
        val ipv6 = Inet6Address.getByAddress(null, ByteArray(16).apply { this[15] = 1 }, -1)
        Mockito.`when`(network.getAllByName("private.caster")).thenReturn(arrayOf(ipv6, address(2)))
        val factory = Mockito.mock(SocketFactory::class.java)
        val connected = Mockito.mock(Socket::class.java)
        Mockito.`when`(network.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenThrow(IOException("private-secret")).thenReturn(connected)
        assertSame(connected, manager.connect(network))
        Mockito.verify(connected).connect(InetSocketAddress(address(2), 2101), 10_000)
        assertTrue(logs.any { "phase=SOCKET_FAILED" in it && "attempt=0" in it && "exceptionClass=IOException" in it })
        assertFalse(logs.any { "private" in it })
    }

    @Test
    fun `permission failure after socket creation closes partial socket and never retries`() = fixture { manager, logs ->
        val network = networkWithAddresses(2)
        val factory = Mockito.mock(SocketFactory::class.java)
        val socket = Mockito.mock(Socket::class.java)
        Mockito.`when`(network.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenReturn(socket)
        Mockito.doThrow(SecurityException("private-secret")).`when`(socket).connect(Mockito.any(), Mockito.anyInt())
        assertTrue(catching { manager.connect(network) } is SecurityException)
        Mockito.verify(socket).close()
        Mockito.verify(factory).createSocket()
        assertTrue(logs.any { "exceptionClass=SecurityException" in it })
        assertFalse(logs.any { "private" in it })
    }

    @Test
    fun `socket setup failure closes socket before retrying`() = fixture { manager, _ ->
        val network = networkWithAddresses(2)
        val factory = Mockito.mock(SocketFactory::class.java)
        val partial = Mockito.mock(Socket::class.java)
        val connected = Mockito.mock(Socket::class.java)
        Mockito.`when`(network.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenReturn(partial, connected)
        Mockito.doThrow(java.net.SocketException("private-secret")).`when`(partial).tcpNoDelay = true
        assertSame(connected, manager.connect(network))
        Mockito.verify(partial).close()
    }

    @Test
    fun `socket errno is logged without the failing operation or exception message`() = fixture { manager, logs ->
        val network = networkWithAddresses()
        val factory = Mockito.mock(SocketFactory::class.java)
        val errno = Mockito.mock(ErrnoException::class.java)
        // ErrnoException's field is final; real Android constructor is a stub in JVM tests.
        ErrnoException::class.java.getDeclaredField("errno").apply { isAccessible = true }.setInt(errno, 13)
        Mockito.`when`(network.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenThrow(IOException("private-secret", errno))
        assertTrue(catching { manager.connect(network) } is IOException)
        assertTrue(logs.any { "errno=13" in it })
        assertFalse(logs.any { "private" in it })
    }

    @Test
    fun `relay uses VPN DNS and socket factory and cleans observer after socket failure`() = fixture { manager, logs ->
        val cm = manager.javaClass.getDeclaredField("connectivityManager").apply { isAccessible = true }
            .get(manager) as ConnectivityManager
        val cellular = Mockito.mock(Network::class.java)
        val vpn = networkWithAddresses()
        val cellCaps = cellularCaps()
        val vpnCaps = cellularCaps(vpn = true)
        Mockito.`when`(cm.getNetworkCapabilities(cellular)).thenReturn(cellCaps)
        Mockito.`when`(cm.activeNetwork).thenReturn(vpn)
        Mockito.`when`(cm.getNetworkCapabilities(vpn)).thenReturn(vpnCaps)
        val factory = Mockito.mock(SocketFactory::class.java)
        Mockito.`when`(vpn.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenThrow(IOException("private-secret"))
        val downstream = Mockito.mock(Socket::class.java)
        val continuation = object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
        }
        manager.invoke("relayConnection", downstream, cellular,
            MobileRtkRelayConfig("capture", true, "private.caster", 2101), 1L, continuation)
        Mockito.verify(vpn).getAllByName("private.caster")
        Mockito.verify(factory).createSocket()
        Mockito.verifyNoInteractions(cellular)
        Mockito.verify(downstream, Mockito.atLeastOnce()).close()
        Mockito.verify(cm).unregisterNetworkCallback(Mockito.any(ConnectivityManager.NetworkCallback::class.java))
        assertTrue(logs.any { "phase=ROUTE_SELECTED" in it && "viaVpn=true" in it })
        assertFalse(manager.state.value.error.orEmpty().contains("private"))
    }

    @Test
    fun `route change between IPv6 and IPv4 attempts prevents a second socket`() = fixture { manager, _ ->
        val cm = manager.javaClass.getDeclaredField("connectivityManager").apply { isAccessible = true }
            .get(manager) as ConnectivityManager
        val cellular = Mockito.mock(Network::class.java)
        val vpn = networkWithAddresses(2)
        Mockito.doReturn(cellularCaps()).`when`(cm).getNetworkCapabilities(cellular)
        Mockito.`when`(cm.activeNetwork).thenReturn(vpn)
        Mockito.doReturn(cellularCaps(vpn = true)).`when`(cm).getNetworkCapabilities(vpn)
        val route = RtkRelayRoute(cm, cellular) {}
        route.start()
        val factory = Mockito.mock(SocketFactory::class.java)
        Mockito.`when`(vpn.socketFactory).thenReturn(factory)
        Mockito.`when`(factory.createSocket()).thenAnswer {
            Mockito.`when`(cm.getNetworkCapabilities(vpn)).thenReturn(null)
            throw IOException("private-secret")
        }
        try {
            assertTrue(catching { manager.invoke("connectOnNetwork", vpn, "private.caster", 2101, 1L, route) }
                is RtkRelayRouteException)
            Mockito.verify(factory).createSocket()
        } finally { route.close() }
    }

    private fun cellularCaps(vpn: Boolean = false) = Mockito.mock(NetworkCapabilities::class.java).also {
        Mockito.`when`(it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true)
        Mockito.`when`(it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(vpn)
        Mockito.`when`(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        Mockito.`when`(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
        Mockito.`when`(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)).thenReturn(!vpn)
    }

    @Test
    fun `manager cleanup closes in flight route sockets and unregisters observer`() = fixture { manager, _ ->
        val cm = manager.javaClass.getDeclaredField("connectivityManager").apply { isAccessible = true }
            .get(manager) as ConnectivityManager
        val cellular = Mockito.mock(Network::class.java)
        val vpn = Mockito.mock(Network::class.java)
        Mockito.doReturn(cellularCaps()).`when`(cm).getNetworkCapabilities(cellular)
        Mockito.`when`(cm.activeNetwork).thenReturn(vpn)
        Mockito.doReturn(cellularCaps(vpn = true)).`when`(cm).getNetworkCapabilities(vpn)
        var invalidations = 0
        val route = RtkRelayRoute(cm, cellular) { invalidations++ }
        val socket = Mockito.mock(Socket::class.java)
        route.attach(socket)
        route.start()
        @Suppress("UNCHECKED_CAST")
        val routes = manager.javaClass.getDeclaredField("activeRoutes").apply { isAccessible = true }
            .get(manager) as MutableSet<RtkRelayRoute>
        routes.add(route)
        val captor = org.mockito.ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback::class.java)
        Mockito.verify(cm).registerDefaultNetworkCallback(captor.capture())
        manager.invoke("releaseLocalResourcesLocked")
        assertTrue(routes.isEmpty())
        Mockito.verify(socket).close()
        Mockito.verify(cm).unregisterNetworkCallback(captor.value)
        // An already queued event from the retired observer cannot update a new session.
        captor.value.onLost(vpn)
        assertEquals(0, invalidations)
    }

    @Test
    fun `request forwarding preserves auth and GGA while logging only its stage`() = fixture { manager, logs ->
        val downstream = Mockito.mock(Socket::class.java)
        val upstream = Mockito.mock(Socket::class.java)
        val request = "GET /private-mount HTTP/1.0\r\nHost: local\r\nAuthorization: Basic private-secret\r\n\r\n${'$'}GPGGA,private-position\r\n".toByteArray()
        val output = ByteArrayOutputStream()
        Mockito.`when`(downstream.getInputStream()).thenReturn(ByteArrayInputStream(request))
        Mockito.`when`(upstream.getOutputStream()).thenReturn(output)
        manager.invoke("forwardNtripRequest", downstream, upstream, "private.caster", 2101, 1L)
        assertArrayEquals(rewriteNtripHostHeader(request, "private.caster", 2101), output.toByteArray())
        assertTrue(logs.any { "phase=REQUEST_FORWARDED" in it })
        assertFalse(logs.any { "private" in it || "Authorization" in it || "GPGGA" in it })
    }

    @Test
    fun `EOF before a full header is distinguishable`() = fixture { manager, logs ->
        val downstream = Mockito.mock(Socket::class.java)
        val upstream = Mockito.mock(Socket::class.java)
        val request = "GET /private-mount".toByteArray()
        val output = ByteArrayOutputStream()
        Mockito.`when`(downstream.getInputStream()).thenReturn(ByteArrayInputStream(request))
        Mockito.`when`(upstream.getOutputStream()).thenReturn(output)
        manager.invoke("forwardNtripRequest", downstream, upstream, "private.caster", 2101, 1L)
        assertArrayEquals(request, output.toByteArray())
        assertTrue(logs.any { "phase=INCOMPLETE_HEADER" in it })
        assertFalse(logs.any { "phase=REQUEST_FORWARDED" in it || "private" in it })
    }

    @Test
    fun `caster bytes remain lossless and first byte marker is emitted once`() = fixture { manager, logs ->
        val downstream = Mockito.mock(Socket::class.java)
        val upstream = Mockito.mock(Socket::class.java)
        val frame = validRtcm3Frame()
        val payload = ByteArray(40_000 / frame.size * frame.size) { 0 }
        repeat(payload.size / frame.size) { index ->
            frame.copyInto(payload, index * frame.size)
        }
        val header = ascii("ICY 200 OK\r\n")
        val wire = header + payload
        val output = ByteArrayOutputStream()
        Mockito.`when`(upstream.getInputStream()).thenReturn(ByteArrayInputStream(wire))
        Mockito.`when`(downstream.getOutputStream()).thenReturn(output)
        manager.invoke("copyCasterData", upstream, downstream, 1L)
        assertArrayEquals(wire, output.toByteArray())
        assertEquals(payload.size.toLong(), manager.state.value.bytesFromCaster)
        assertEquals(1, logs.count { "phase=CASTER_FIRST_BYTES" in it })
        assertTrue(logs.any { "phase=CASTER_EOF" in it && "receivedBytes=${wire.size}" in it })
    }

    @Test
    fun `HTTP 401 body is forwarded losslessly without successful caster bytes`() = fixture { manager, _ ->
        val downstream = Mockito.mock(Socket::class.java)
        val upstream = Mockito.mock(Socket::class.java)
        val wire = ascii("HTTP/1.1 401 Unauthorized\r\nContent-Type: text/plain\r\n\r\nDenied") +
            validRtcm3Frame()
        val output = ByteArrayOutputStream()
        Mockito.`when`(upstream.getInputStream()).thenReturn(ByteArrayInputStream(wire))
        Mockito.`when`(downstream.getOutputStream()).thenReturn(output)
        manager.invoke("copyCasterData", upstream, downstream, 1L)
        assertArrayEquals(wire, output.toByteArray())
        assertEquals(0L, manager.state.value.bytesFromCaster)
        assertTrue(manager.state.value.error.orEmpty().contains("HTTP 401"))
    }

    @Test
    fun `caster EOF with zero bytes is explicit`() = fixture { manager, logs ->
        val downstream = Mockito.mock(Socket::class.java)
        val upstream = Mockito.mock(Socket::class.java)
        Mockito.`when`(upstream.getInputStream()).thenReturn(ByteArrayInputStream(byteArrayOf()))
        Mockito.`when`(downstream.getOutputStream()).thenReturn(ByteArrayOutputStream())
        manager.invoke("copyCasterData", upstream, downstream, 1L)
        assertTrue(logs.any { "phase=CASTER_EOF" in it && "receivedBytes=0" in it })
        assertFalse(logs.any { "phase=CASTER_FIRST_BYTES" in it })
        assertEquals(0L, manager.state.value.bytesFromCaster)
    }

    private fun address(last: Int) = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, last.toByte()))

    // RTCM3 type 1005 reference frame with a valid CRC24Q.
    private fun validRtcm3Frame() = hex("d300133ed00003000000000000000000000000000000950a66")

    private fun ascii(value: String) = value.toByteArray(Charsets.US_ASCII)

    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun assertRejectedPeer(peer: InetAddress, owner: String, decision: String) = fixture { manager, logs ->
        val server = Mockito.mock(ServerSocket::class.java)
        val downstream = Mockito.mock(Socket::class.java)
        val network = Mockito.mock(Network::class.java)
        Mockito.`when`(downstream.inetAddress).thenReturn(peer)
        Mockito.`when`(server.accept()).thenReturn(downstream).thenAnswer {
            Mockito.`when`(server.isClosed).thenReturn(true)
            throw IOException("stopped")
        }
        val continuation = object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
        }
        manager.invoke("acceptConnections", server, network,
            MobileRtkRelayConfig("capture", true, "private.caster", 2101), owner, continuation)
        Mockito.verify(downstream).close()
        Mockito.verifyNoInteractions(network)
        assertTrue(logs.any { "phase=PEER_CHECK" in it && "decision=$decision" in it })
        assertFalse(logs.any { "private" in it || "192.168" in it || "phase=DNS_STARTED" in it })
    }

    private fun networkWithAddresses(count: Int = 1) = Mockito.mock(Network::class.java).also {
        Mockito.`when`(it.getAllByName("private.caster")).thenReturn(Array(count) { index -> address(index + 1) })
    }

    private fun MobileRtkRelayManager.connect(network: Network) = invoke("connectOnNetwork", network, "private.caster", 2101, 1L, null)

    private fun MobileRtkRelayManager.invoke(name: String, vararg args: Any?): Any? = try {
        val method = javaClass.declaredMethods.single { it.name == name }.apply { isAccessible = true }
        val actual = args.toMutableList()
        val sessionIndex = method.parameterTypes.indexOfFirst { it.simpleName == "RelaySession" }
        if (sessionIndex >= 0 && !method.parameterTypes[sessionIndex].isInstance(actual.getOrNull(sessionIndex))) {
            actual.add(sessionIndex, session())
        }
        val responseIndex = method.parameterTypes.indexOf(NtripCasterResponseTracker::class.java)
        if (responseIndex >= 0 && actual.size < method.parameterCount) {
            actual.add(responseIndex, NtripCasterResponseTracker())
        }
        method.invoke(this, *actual.toTypedArray())
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    private fun MobileRtkRelayManager.session(): Any = javaClass.getDeclaredField("currentSession")
        .apply { isAccessible = true }.get(this)!!

    private fun catching(block: () -> Any?): Throwable {
        try { block() } catch (error: Throwable) { return error }
        throw AssertionError("Expected failure")
    }

    private fun fixture(block: (MobileRtkRelayManager, MutableList<String>) -> Unit) {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(Mockito.mock(ConnectivityManager::class.java))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val manager = MobileRtkRelayManager(context, Mockito.mock(WifiDirectManager::class.java), scope)
        manager.invoke("createSession")
        @Suppress("UNCHECKED_CAST")
        val state = manager.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(manager) as MutableStateFlow<MobileRtkRelayState>
        state.value = MobileRtkRelayState(active = true)
        val logs = mutableListOf<String>()
        try {
            Mockito.mockStatic(Log::class.java).use { mocked ->
                mocked.`when`<Int> { Log.i(Mockito.eq("MobileRtkRelay"), Mockito.anyString()) }.thenAnswer {
                    logs.add(it.getArgument(1)); 0
                }
                block(manager, logs)
            }
        } finally { scope.cancel() }
    }
}
