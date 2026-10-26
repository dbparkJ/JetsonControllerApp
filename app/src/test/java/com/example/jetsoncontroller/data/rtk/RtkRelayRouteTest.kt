package com.example.jetsoncontroller.data.rtk

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class RtkRelayRouteTest {
    private val cm = Mockito.mock(ConnectivityManager::class.java)
    private val cellular = Mockito.mock(Network::class.java)
    private val vpn = Mockito.mock(Network::class.java)

    private fun caps(vararg transports: Int, internet: Boolean = true, validated: Boolean = true) =
        Mockito.mock(NetworkCapabilities::class.java).also { caps ->
            transports.forEach { Mockito.`when`(caps.hasTransport(it)).thenReturn(true) }
            Mockito.`when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(internet)
            Mockito.`when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(validated)
            Mockito.`when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)).thenReturn(VPN !in transports)
        }

    private fun fixture(activeCaps: NetworkCapabilities? = caps(CELL, VPN)) {
        Mockito.doReturn(caps(CELL)).`when`(cm).getNetworkCapabilities(cellular)
        Mockito.`when`(cm.activeNetwork).thenReturn(vpn)
        Mockito.`when`(cm.getNetworkCapabilities(vpn)).thenReturn(activeCaps)
    }

    private fun rejected(reason: String, block: () -> Unit) {
        try {
            block()
            fail("Expected route rejection")
        } catch (error: RtkRelayRouteException) {
            assertEquals(reason, error.reason)
            assertFalse(error.message.orEmpty().contains("private"))
        }
    }

    @Test fun `validated cellular VPN is selected without releasing the lease`() {
        fixture()
        assertSame(vpn, selectRtkRelayNetwork(cm, cellular))
        Mockito.verify(cm, Mockito.never()).unregisterNetworkCallback(Mockito.any(ConnectivityManager.NetworkCallback::class.java))
        Mockito.verifyNoInteractions(cellular, vpn)
    }

    @Test fun `non VPN default keeps explicit cellular route even with WiFi Direct`() {
        fixture(caps(NetworkCapabilities.TRANSPORT_WIFI, internet = false, validated = false))
        assertSame(cellular, selectRtkRelayNetwork(cm, cellular))
    }

    @Test fun `WiFi VPN cannot fall back to cellular`() {
        fixture(caps(VPN, NetworkCapabilities.TRANSPORT_WIFI))
        rejected("VPN_NOT_CELLULAR") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `mixed cellular VPN rejects every other public physical transport`() {
        intArrayOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_BLUETOOTH, NetworkCapabilities.TRANSPORT_WIFI_AWARE,
            NetworkCapabilities.TRANSPORT_LOWPAN, NetworkCapabilities.TRANSPORT_USB,
            NetworkCapabilities.TRANSPORT_THREAD, NetworkCapabilities.TRANSPORT_SATELLITE).forEach {
            fixture(caps(CELL, VPN, it))
            rejected("VPN_NOT_CELLULAR") { selectRtkRelayNetwork(cm, cellular) }
        }
    }

    @Test fun `VPN with unknown underlying transport is rejected`() {
        fixture(caps(VPN))
        rejected("VPN_NOT_CELLULAR") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `VPN without internet is rejected`() {
        fixture(caps(CELL, VPN, internet = false))
        rejected("VPN_NOT_CELLULAR") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `unvalidated VPN is rejected`() {
        fixture(caps(CELL, VPN, validated = false))
        rejected("VPN_NOT_CELLULAR") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `missing active capabilities cannot authorize fallback`() {
        fixture(null)
        rejected("NETWORK_UNKNOWN") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `unknown active VPN identity cannot authorize fallback`() {
        val unknown = Mockito.mock(NetworkCapabilities::class.java)
        fixture(unknown)
        rejected("NETWORK_UNKNOWN") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `missing active network cannot authorize fallback`() {
        fixture()
        Mockito.`when`(cm.activeNetwork).thenReturn(null)
        rejected("NETWORK_UNKNOWN") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `lost cellular lease rejects VPN too`() {
        fixture()
        Mockito.doReturn(null).`when`(cm).getNetworkCapabilities(cellular)
        rejected("CELLULAR_UNAVAILABLE") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `unvalidated NOT VPN cellular lease keeps direct caster access`() {
        fixture(caps(NetworkCapabilities.TRANSPORT_WIFI, internet = false, validated = false))
        Mockito.doReturn(caps(CELL, validated = false)).`when`(cm).getNetworkCapabilities(cellular)
        assertSame(cellular, selectRtkRelayNetwork(cm, cellular))
        val route = RtkRelayRoute(cm, cellular) {}
        val socket = Mockito.mock(Socket::class.java)
        route.attach(socket)
        route.start()
        callback().onCapabilitiesChanged(cellular, caps(CELL, validated = false))
        Mockito.verify(socket, Mockito.never()).close()
        route.check()
        route.close()
    }

    @Test fun `validation is required on VPN route and not on its cellular lease`() {
        fixture()
        Mockito.doReturn(caps(CELL, validated = false)).`when`(cm).getNetworkCapabilities(cellular)
        assertSame(vpn, selectRtkRelayNetwork(cm, cellular))
        Mockito.doReturn(caps(CELL, VPN, validated = false)).`when`(cm).getNetworkCapabilities(vpn)
        rejected("VPN_NOT_CELLULAR") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `unvalidated cellular lease never allows unsafe VPN fallback`() {
        for (unsafe in listOf(caps(VPN, NetworkCapabilities.TRANSPORT_WIFI),
            caps(CELL, VPN, NetworkCapabilities.TRANSPORT_WIFI), caps(VPN), caps(CELL, VPN, validated = false))) {
            fixture(unsafe)
            Mockito.doReturn(caps(CELL, validated = false)).`when`(cm).getNetworkCapabilities(cellular)
            rejected("VPN_NOT_CELLULAR") { selectRtkRelayNetwork(cm, cellular) }
        }
    }

    @Test fun `cellular lease without explicit NOT VPN is rejected`() {
        fixture(caps(CELL))
        val unknown = caps(CELL, validated = false)
        Mockito.`when`(unknown.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)).thenReturn(false)
        Mockito.doReturn(unknown).`when`(cm).getNetworkCapabilities(cellular)
        rejected("CELLULAR_UNAVAILABLE") { selectRtkRelayNetwork(cm, cellular) }
    }

    @Test fun `unknown default VPN identity immediately invalidates direct route`() {
        fixture(caps(CELL))
        val route = RtkRelayRoute(cm, cellular) {}
        val socket = Mockito.mock(Socket::class.java)
        route.attach(socket)
        route.start()
        callback().onCapabilitiesChanged(vpn, Mockito.mock(NetworkCapabilities::class.java))
        Mockito.verify(socket).close()
    }

    @Test fun `permission failure is explicit and does not expose system message`() {
        fixture()
        Mockito.`when`(cm.activeNetwork).thenThrow(SecurityException("private-secret"))
        rejected("PERMISSION_DENIED") { selectRtkRelayNetwork(cm, cellular) }
    }

    private fun callback(): ConnectivityManager.NetworkCallback {
        val captor = ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback::class.java)
        Mockito.verify(cm).registerDefaultNetworkCallback(captor.capture())
        return captor.value
    }

    @Test fun `VPN switching to WiFi closes both sockets immediately using delivered capabilities`() {
        fixture()
        var invalidations = 0
        val route = RtkRelayRoute(cm, cellular) { invalidations++ }
        val downstream = Mockito.mock(Socket::class.java)
        val upstream = Mockito.mock(Socket::class.java)
        route.attach(downstream)
        route.start()
        route.attach(upstream)
        // The synchronous snapshot deliberately remains cellular-only.
        callback().onCapabilitiesChanged(vpn, caps(VPN, NetworkCapabilities.TRANSPORT_WIFI))
        Mockito.verify(downstream).close()
        Mockito.verify(upstream).close()
        assertEquals(1, invalidations)
        rejected("ROUTE_CHANGED") { route.check() }
        route.close()
        Mockito.verify(cm).unregisterNetworkCallback(Mockito.any(ConnectivityManager.NetworkCallback::class.java))
    }

    @Test fun `new sockets cannot escape a route invalidated before socket creation completed`() {
        fixture()
        val route = RtkRelayRoute(cm, cellular) {}
        route.start()
        callback().onLost(vpn)
        val socket = Mockito.mock(Socket::class.java)
        rejected("ROUTE_CHANGED") { route.attach(socket) }
        Mockito.verify(socket).close()
    }

    @Test fun `VPN validation loss terminates the connection`() {
        fixture()
        val route = RtkRelayRoute(cm, cellular) {}
        val socket = Mockito.mock(Socket::class.java)
        route.attach(socket)
        route.start()
        callback().onCapabilitiesChanged(vpn, caps(CELL, VPN, validated = false))
        Mockito.verify(socket).close()
    }

    @Test fun `default VPN replacement terminates the old connection`() {
        fixture()
        val route = RtkRelayRoute(cm, cellular) {}
        val socket = Mockito.mock(Socket::class.java)
        route.attach(socket)
        route.start()
        callback().onAvailable(Mockito.mock(Network::class.java))
        Mockito.verify(socket).close()
    }

    @Test fun `unchanged cellular VPN stays open`() {
        fixture()
        val route = RtkRelayRoute(cm, cellular) {}
        val socket = Mockito.mock(Socket::class.java)
        route.attach(socket)
        route.start()
        callback().onAvailable(vpn)
        callback().onCapabilitiesChanged(vpn, caps(CELL, VPN))
        Mockito.verify(socket, Mockito.never()).close()
        route.close()
    }

    @Test fun `permission denied during observer registration closes owned sockets`() {
        fixture()
        val route = RtkRelayRoute(cm, cellular) {}
        val socket = Mockito.mock(Socket::class.java)
        route.attach(socket)
        Mockito.doThrow(SecurityException("private-secret")).`when`(cm)
            .registerDefaultNetworkCallback(Mockito.any(ConnectivityManager.NetworkCallback::class.java))
        rejected("PERMISSION_DENIED") { route.start() }
        Mockito.verify(socket).close()
    }

    @Test fun `every attempt revalidates the active VPN and lease`() {
        fixture()
        val route = RtkRelayRoute(cm, cellular) {}
        route.start()
        Mockito.doReturn(caps(CELL, VPN, NetworkCapabilities.TRANSPORT_WIFI)).`when`(cm).getNetworkCapabilities(vpn)
        rejected("VPN_NOT_CELLULAR") { route.check() }
        route.close()
    }

    @Test fun `observer registration race is caught by immediate revalidation`() {
        fixture()
        val route = RtkRelayRoute(cm, cellular) {}
        Mockito.doAnswer {
            Mockito.doReturn(caps(VPN)).`when`(cm).getNetworkCapabilities(vpn)
            null
        }.`when`(cm).registerDefaultNetworkCallback(Mockito.any(ConnectivityManager.NetworkCallback::class.java))
        rejected("VPN_NOT_CELLULAR") { route.start() }
        Mockito.verify(cm).unregisterNetworkCallback(Mockito.any(ConnectivityManager.NetworkCallback::class.java))
    }

    @Test fun `VPN appearing invalidates an existing directly bound cellular connection`() {
        fixture(caps(CELL))
        val route = RtkRelayRoute(cm, cellular) {}
        val socket = Mockito.mock(Socket::class.java)
        route.attach(socket)
        route.start()
        callback().onCapabilitiesChanged(vpn, caps(CELL, VPN))
        Mockito.verify(socket).close()
    }

    companion object {
        private const val CELL = NetworkCapabilities.TRANSPORT_CELLULAR
        private const val VPN = NetworkCapabilities.TRANSPORT_VPN
    }
}
