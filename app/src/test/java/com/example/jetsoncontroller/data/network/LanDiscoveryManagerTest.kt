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

class LanDiscoveryManagerTest {
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
        val connectivity = Mockito.mock(ConnectivityManager::class.java)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        private val interfaces = Mockito.mockStatic(NetworkInterface::class.java)
        private val logs = Mockito.mockStatic(Log::class.java)
        private lateinit var listener: NsdManager.DiscoveryListener
        val manager: LanDiscoveryManager

        init {
            val context = Mockito.mock(Context::class.java)
            val nsd = Mockito.mock(NsdManager::class.java)
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
                null
            }.`when`(nsd).discoverServices(Mockito.anyString(), Mockito.anyInt(), Mockito.any(NsdManager.DiscoveryListener::class.java))
            Mockito.doAnswer {
                it.getArgument<NsdManager.ResolveListener>(1).onServiceResolved(it.getArgument(0))
                null
            }.`when`(nsd).resolveService(Mockito.any(NsdServiceInfo::class.java), Mockito.any(NsdManager.ResolveListener::class.java))
            manager = LanDiscoveryManager(context)
            manager.startDiscovery()
        }

        fun resolve(host: String) {
            val service = Mockito.mock(NsdServiceInfo::class.java)
            Mockito.`when`(service.serviceType).thenReturn("_jetsonctl._tcp.")
            Mockito.`when`(service.serviceName).thenReturn("GEO-X-1")
            Mockito.`when`(service.attributes).thenReturn(mapOf("api" to "1".toByteArray(), "tls" to "1".toByteArray(), "id" to ID.toByteArray()))
            Mockito.`when`(service.host).thenReturn(InetAddress.getByName(host))
            Mockito.`when`(service.hostAddresses).thenReturn(listOf(InetAddress.getByName(host)))
            Mockito.`when`(service.port).thenReturn(8000)
            listener.onServiceFound(service)
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
            logs.close()
            interfaces.close()
        }
    }

    companion object {
        private const val ID = "d606c26d-98d6-4b09-99d7-c3da7dda4de0"
    }
}
