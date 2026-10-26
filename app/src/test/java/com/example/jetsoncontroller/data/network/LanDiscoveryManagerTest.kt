package com.example.jetsoncontroller.data.network

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.LinkProperties
import android.net.Network
import android.net.RouteInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.jetsoncontroller.data.repository.isLanEndpointCandidate
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.EndpointTransport
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Collections
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class LanDiscoveryManagerTest {
    @Test
    fun `authenticated address survives a new manager and retains device ID and port`() {
        Fixture().use { f ->
            assertNull(f.manager.authenticatedEndpoint(ID, "GEO-X-1"))
            f.manager.rememberAuthenticatedEndpoint(f.endpoint("111.111.111.45").copy(port = 8765))
            val restored = f.newManager().authenticatedEndpoint(ID.uppercase(), "GEO-X-1")!!
            assertEquals(ID, restored.deviceId)
            assertEquals("111.111.111.45", restored.host)
            assertEquals(8765, restored.port)
            assertNull(f.newManager().authenticatedEndpoint("6331d64d-d79a-429a-b048-c94ab3116531", "other"))
            assertNull(f.newManager().authenticatedEndpoint("invalid-id", "other"))
            f.manager.rememberAuthenticatedEndpoint(f.endpoint("fe80::1234"))
            assertNull(f.newManager().authenticatedEndpoint(ID, "GEO-X-1"))
        }
    }

    @Test
    fun `explicit restart retires active listener and ignores late discovery callbacks`() {
        Fixture().use { f ->
            val previous = f.listener
            f.manager.startDiscovery()
            Mockito.verify(f.nsd).stopServiceDiscovery(previous)
            previous.onDiscoveryStopped("_jetsonctl._tcp.")
            f.scheduler.runCurrent()
            assertEquals(2, f.discoverCalls())
            previous.onServiceFound(f.service("111.111.111.45"))
            f.scheduler.runCurrent()
            assertEquals(0, f.resolveCalls.size)
            // A late stop from the retired listener must not turn off the new search.
            previous.onDiscoveryStopped("_jetsonctl._tcp.")
            f.scheduler.runCurrent()
            assertTrue(f.manager.isDiscovering.value)
            f.manager.ensureDiscovery()
            assertEquals(2, f.discoverCalls())
        }
    }

    @Test
    fun `resolve failure and already active discovery retry after bounded cleanup`() {
        Fixture().use { f ->
            f.autoResolve = false
            f.resolve("111.111.111.45")
            f.resolveCalls.single().second.onResolveFailed(f.service("111.111.111.45"), NsdManager.FAILURE_ALREADY_ACTIVE)
            f.scheduler.runCurrent()
            f.advance(LanDiscoveryManager.RETRY_MILLIS + LanDiscoveryManager.STOP_TIMEOUT_MILLIS)
            assertEquals(2, f.discoverCalls())
            f.listener.onStartDiscoveryFailed("_jetsonctl._tcp.", NsdManager.FAILURE_ALREADY_ACTIVE)
            f.scheduler.runCurrent()
            f.advance(LanDiscoveryManager.RETRY_MILLIS + LanDiscoveryManager.STOP_TIMEOUT_MILLIS)
            assertEquals(3, f.discoverCalls())
            f.autoResolve = true
            f.resolve("111.111.111.45")
            assertEquals("111.111.111.45", f.manager.discoveredEndpoints.value.single().host)
        }
    }

    @Test
    fun `missing resolve callback retries and ignores timed out answer`() {
        Fixture().use { f ->
            f.autoResolve = false
            f.resolve("111.111.111.45")
            val timedOut = f.resolveCalls.single().second
            f.advance(LanDiscoveryManager.RESOLVE_TIMEOUT_MILLIS + LanDiscoveryManager.RETRY_MILLIS + LanDiscoveryManager.STOP_TIMEOUT_MILLIS)
            assertEquals(2, f.discoverCalls())
            timedOut.onServiceResolved(f.service("111.111.111.99"))
            f.scheduler.runCurrent()
            assertTrue(f.manager.discoveredEndpoints.value.isEmpty())
            f.autoResolve = true
            f.resolve("111.111.111.45")
            assertEquals("111.111.111.45", f.manager.discoveredEndpoints.value.single().host)
        }
    }

    @Test
    fun `Android 11 single link local answer triggers another discovery for IPv4`() {
        Fixture().use { f ->
            f.resolve("fe80::42:acff:fe11:2")
            assertTrue(f.manager.discoveredEndpoints.value.isEmpty())
            f.advance(LanDiscoveryManager.RETRY_MILLIS + LanDiscoveryManager.STOP_TIMEOUT_MILLIS)
            assertEquals(2, f.discoverCalls())
            f.resolve("111.111.111.45")
            assertEquals("111.111.111.45", f.manager.discoveredEndpoints.value.single().host)
            assertNull(f.manager.error.value)
        }
    }

    @Test
    fun `Android 11 resolves only one service at a time and stop cancels retries`() {
        Fixture().use { f ->
            f.autoResolve = false
            f.resolve("111.111.111.45")
            f.listener.onServiceFound(f.service("111.111.111.46", "GEO-X-2"))
            f.scheduler.runCurrent()
            assertEquals(1, f.resolveCalls.size)
            f.resolveCalls.first().second.onServiceResolved(f.service("111.111.111.45"))
            f.scheduler.runCurrent()
            assertEquals(2, f.resolveCalls.size)
            f.manager.stopDiscovery()
            f.resolveCalls.last().second.onResolveFailed(f.service("111.111.111.46"), 3)
            f.advance(60_000L)
            assertEquals(1, f.discoverCalls())
            assertFalse(f.manager.isDiscovering.value)
        }
    }

    @Test
    fun `missing discovery start callback and healthy discovery both refresh`() {
        Fixture().use { f ->
            f.advance(LanDiscoveryManager.REFRESH_MILLIS + LanDiscoveryManager.STOP_TIMEOUT_MILLIS)
            assertEquals(2, f.discoverCalls())
            f.autoStart = false
            f.manager.startDiscovery()
            f.advance(LanDiscoveryManager.STOP_TIMEOUT_MILLIS)
            assertEquals(3, f.discoverCalls())
            f.advance(LanDiscoveryManager.START_TIMEOUT_MILLIS + LanDiscoveryManager.STOP_TIMEOUT_MILLIS)
            assertEquals(4, f.discoverCalls())
        }
    }

    @Test
    fun `NSD and reconnect reject kernel P2P without any ConnectivityManager Network`() {
        Fixture("p2p-wlan0-0", "192.168.49.2", 24).use { f ->
            f.resolve("192.168.49.1")
            assertTrue(f.manager.discoveredEndpoints.value.isEmpty())
            assertFalse(isLanEndpointCandidate(f.manager.reconnectEndpoint(f.endpoint("192.168.49.1"))))
            Mockito.`when`(f.preferences.getString("$ID.host", null)).thenReturn("192.168.49.1")
            Mockito.`when`(f.preferences.getInt("$ID.port", -1)).thenReturn(8000)
            assertFalse(isLanEndpointCandidate(f.manager.reconnectEndpoint(f.endpoint("192.168.49.1"))))
            for (host in listOf("10.20.30.40", "2001:db8::1")) {
                f.resolve(host)
                assertEquals(InetAddress.getByName(host), InetAddress.getByName(f.manager.discoveredEndpoints.value.single().host))
                assertTrue(isLanEndpointCandidate(f.manager.reconnectEndpoint(f.endpoint(host))))
            }
        }
    }

    @Test
    fun `normal kernel ethernet on usual P2P subnet remains a LAN candidate`() {
        Fixture("eth0", "192.168.49.2", 24).use { f ->
            f.resolve("192.168.49.1")
            assertEquals("192.168.49.1", f.manager.discoveredEndpoints.value.single().host)
            assertTrue(isLanEndpointCandidate(f.manager.reconnectEndpoint(f.endpoint("192.168.49.1"))))
        }
    }

    @Test
    fun `P2P Network routes exclude only their actual destinations and ignore default route`() {
        Fixture().use { f ->
            val network = Mockito.mock(Network::class.java)
            val link = Mockito.mock(LinkProperties::class.java)
            Mockito.`when`(f.connectivity.allNetworks).thenReturn(arrayOf(network))
            Mockito.`when`(f.connectivity.getLinkProperties(network)).thenReturn(link)
            Mockito.`when`(link.interfaceName).thenReturn("p2p0")
            Mockito.`when`(link.linkAddresses).thenReturn(emptyList())
            val routes = listOf(f.route("172.19.8.0", 25), f.route("0.0.0.0", 0))
            Mockito.`when`(link.routes).thenReturn(routes)
            f.resolve("172.19.8.1")
            assertTrue(f.manager.discoveredEndpoints.value.isEmpty())
            assertFalse(isLanEndpointCandidate(f.manager.reconnectEndpoint(f.endpoint("172.19.8.1"))))
            f.resolve("172.19.8.200")
            assertEquals("172.19.8.200", f.manager.discoveredEndpoints.value.single().host)
        }
    }

    private class Fixture(name: String? = null, host: String = "10.0.0.2", prefix: Int = 24) : AutoCloseable {
        val scheduler = TestCoroutineScheduler()
        private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val context = Mockito.mock(Context::class.java)
        val nsd = Mockito.mock(NsdManager::class.java)
        var autoResolve = true
        var autoStart = true
        val resolveCalls = mutableListOf<Pair<NsdServiceInfo, NsdManager.ResolveListener>>()
        val connectivity = Mockito.mock(ConnectivityManager::class.java)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        private val interfaces = Mockito.mockStatic(NetworkInterface::class.java)
        private val logs = Mockito.mockStatic(Log::class.java)
        lateinit var listener: NsdManager.DiscoveryListener
        val manager: LanDiscoveryManager

        init {
            val saved = mutableMapOf<String, Any>()
            val editor = Mockito.mock(SharedPreferences.Editor::class.java)
            Mockito.`when`(preferences.edit()).thenReturn(editor)
            Mockito.`when`(editor.putString(Mockito.anyString(), Mockito.anyString())).thenAnswer {
                saved[it.getArgument(0)] = it.getArgument<String>(1); editor
            }
            Mockito.`when`(editor.putInt(Mockito.anyString(), Mockito.anyInt())).thenAnswer {
                saved[it.getArgument(0)] = it.getArgument<Int>(1); editor
            }
            Mockito.`when`(preferences.getString(Mockito.anyString(), Mockito.isNull())).thenAnswer { saved[it.getArgument(0)] as? String }
            Mockito.`when`(preferences.getInt(Mockito.anyString(), Mockito.anyInt())).thenAnswer { saved[it.getArgument(0)] as? Int ?: it.getArgument<Int>(1) }
            Mockito.`when`(context.applicationContext).thenReturn(context)
            Mockito.`when`(context.getSharedPreferences("authenticated_lan_endpoints", Context.MODE_PRIVATE)).thenReturn(preferences)
            Mockito.`when`(context.getSystemService(Context.NSD_SERVICE)).thenReturn(nsd)
            Mockito.`when`(context.getSystemService(ConnectivityManager::class.java)).thenReturn(connectivity)
            Mockito.`when`(connectivity.allNetworks).thenReturn(emptyArray())
            val kernel = if (name == null) emptyList() else {
                val network = Mockito.mock(NetworkInterface::class.java)
                val address = Mockito.mock(InterfaceAddress::class.java)
                Mockito.`when`(network.name).thenReturn(name)
                Mockito.`when`(network.isUp).thenReturn(true)
                Mockito.`when`(network.interfaceAddresses).thenReturn(listOf(address))
                Mockito.`when`(address.address).thenReturn(InetAddress.getByName(host))
                Mockito.`when`(address.networkPrefixLength).thenReturn(prefix.toShort())
                listOf(network)
            }
            interfaces.`when`<java.util.Enumeration<NetworkInterface>> { NetworkInterface.getNetworkInterfaces() }
                .thenAnswer { Collections.enumeration(kernel) }
            Mockito.doAnswer {
                listener = it.getArgument(2)
                if (autoStart) listener.onDiscoveryStarted("_jetsonctl._tcp.")
                null
            }.`when`(nsd).discoverServices(Mockito.anyString(), Mockito.anyInt(), Mockito.any(NsdManager.DiscoveryListener::class.java))
            Mockito.doAnswer {
                val service = it.getArgument<NsdServiceInfo>(0)
                val resolver = it.getArgument<NsdManager.ResolveListener>(1)
                resolveCalls += service to resolver
                if (autoResolve) resolver.onServiceResolved(service)
                null
            }.`when`(nsd).resolveService(Mockito.any(NsdServiceInfo::class.java), Mockito.any(NsdManager.ResolveListener::class.java))
            manager = newManager()
            manager.startDiscovery()
            scheduler.runCurrent()
        }

        fun newManager() = LanDiscoveryManager(context, scope)

        fun advance(millis: Long) { scheduler.advanceTimeBy(millis); scheduler.runCurrent() }

        fun discoverCalls() = Mockito.mockingDetails(nsd).invocations.count { it.method.name == "discoverServices" }

        fun resolve(host: String) {
            listener.onServiceFound(service(host))
            scheduler.runCurrent()
        }

        fun service(host: String, name: String = "GEO-X-1"): NsdServiceInfo {
            val service = Mockito.mock(NsdServiceInfo::class.java)
            Mockito.`when`(service.serviceType).thenReturn("_jetsonctl._tcp.")
            Mockito.`when`(service.serviceName).thenReturn(name)
            Mockito.`when`(service.attributes).thenReturn(mapOf("api" to "1".toByteArray(), "tls" to "1".toByteArray(), "id" to ID.toByteArray()))
            Mockito.`when`(service.host).thenReturn(InetAddress.getByName(host))
            Mockito.`when`(service.hostAddresses).thenReturn(listOf(InetAddress.getByName(host)))
            Mockito.`when`(service.port).thenReturn(8000)
            return service
        }

        fun endpoint(host: String) = DeviceEndpoint(ID, "GEO-X-1", host, 8000, EndpointTransport.LAN)

        fun route(host: String, length: Int): RouteInfo {
            val route = Mockito.mock(RouteInfo::class.java)
            val prefix = Mockito.mock(IpPrefix::class.java)
            Mockito.`when`(route.isDefaultRoute).thenReturn(length == 0)
            Mockito.`when`(route.destination).thenReturn(prefix)
            Mockito.`when`(prefix.address).thenReturn(InetAddress.getByName(host))
            Mockito.`when`(prefix.prefixLength).thenReturn(length)
            return route
        }

        override fun close() {
            scope.cancel()
            logs.close()
            interfaces.close()
        }
    }

    companion object {
        private const val ID = "d606c26d-98d6-4b09-99d7-c3da7dda4de0"
    }
}
