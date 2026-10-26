package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class CameraPreviewNetworkPolicyTest {
    private val target = cameraPreviewNumericAddress("111.111.111.45:8080")!!
    private fun route(cidr: String, gateway: Boolean = false) = CameraPreviewRoute(
        InetAddress.getByName(cidr.substringBefore('/')).address.toList(),
        cidr.substringAfter('/').toInt(), gateway
    )
    private val wifi = CameraPreviewNetwork(wifiOrEthernet = true, unmetered = true,
        routes = listOf(route("0.0.0.0/0", true), route("111.111.111.0/24")))
    private val vpn = CameraPreviewNetwork(vpn = true, wifiOrEthernet = true,
        routes = listOf(route("100.101.102.103/32"), route("100.104.105.106/32"),
            route("fd7a:115c:a1e0::/48")))

    private fun decision(active: CameraPreviewNetwork = vpn,
                         fallback: List<CameraPreviewNetwork> = listOf(wifi)) =
        cameraPreviewNetworkDecision(target, active, fallback)

    private fun overlaps(decision: CameraPreviewNetworkDecision) = cameraPreviewCanOverlap(
        TransportState.Connected(TransportType.LAN),
        CameraPreviewEnvironment(unmetered = decision.unmetered, powerSave = false,
            localLanRoute = decision.localLanRoute)
    )

    @Test fun tailscaleSplitTunnelUsesDirectWifiAndItsMetering() {
        assertTrue(overlaps(decision())) // VPN itself does not advertise unmetered.
    }

    @Test fun fullTunnelCapturesTargetEvenWithDirectWifi() {
        assertFalse(overlaps(decision(vpn.copy(routes = vpn.routes!! + route("0.0.0.0/0")))))
    }

    @Test fun vpnSubnetAndHostRoutesCaptureTarget() {
        for (cidr in listOf("111.111.111.0/24", "111.111.111.45/32")) {
            assertFalse(overlaps(decision(vpn.copy(routes = vpn.routes!! + route(cidr)))))
        }
    }

    @Test fun noVpnKeepsNearbyAndRemoteBehavior() {
        assertTrue(overlaps(decision(wifi)))
        assertFalse(overlaps(decision(wifi.copy(routes = listOf(route("0.0.0.0/0", true))))))
        assertFalse(overlaps(decision(wifi.copy(wifiOrEthernet = false))))
        assertFalse(overlaps(decision(wifi.copy(routes = listOf(route("111.111.111.0/24", true))))))
    }

    @Test fun unknownUnderlyingOrRouteTableDisablesOverlap() {
        assertFalse(overlaps(decision(fallback = emptyList())))
        assertFalse(overlaps(decision(fallback = listOf(wifi.copy(routes = null)))))
        assertFalse(overlaps(decision(vpn.copy(routes = null))))
        assertFalse(overlaps(decision(fallback = listOf(vpn))))
    }

    @Test fun meteringUsesActualLanNetwork() {
        assertFalse(overlaps(decision(vpn.copy(unmetered = true), listOf(wifi.copy(unmetered = false)))))
        assertFalse(overlaps(decision(wifi.copy(unmetered = false))))
    }

    @Test fun longestPrefixWinsOverLessSpecificDirectRoute() {
        val overridden = wifi.copy(routes = wifi.routes!! + route("111.111.111.45/32", true))
        assertFalse(overlaps(decision(overridden)))
        assertFalse(overlaps(decision(fallback = listOf(overridden))))
        val specificDirect = wifi.copy(routes = listOf(route("111.111.0.0/16", true),
            route("111.111.111.0/24")))
        assertTrue(overlaps(decision(fallback = listOf(specificDirect))))
    }

    @Test fun ambiguousFallbackInterfacesDisableOverlap() {
        assertFalse(overlaps(decision(fallback = listOf(wifi, wifi.copy(unmetered = false)))))
    }

    @Test fun knownUnderlyingNetworksTakePrecedenceOverFallback() {
        assertFalse(overlaps(cameraPreviewNetworkDecision(target, vpn, listOf(wifi), emptyList())))
        assertFalse(overlaps(cameraPreviewNetworkDecision(target, vpn, listOf(wifi),
            listOf(wifi.copy(unmetered = false)))))
        assertTrue(overlaps(cameraPreviewNetworkDecision(target, vpn, emptyList(), listOf(wifi))))
    }

    @Test fun ipv6UsesAddressFamilyAndIncludesDefaultRoute() {
        val ipv6 = cameraPreviewNumericAddress("[2001:db8:1234::45]:8080")!!
        val ethernet = wifi.copy(routes = listOf(route("2001:db8:1234::/64")))
        assertTrue(overlaps(cameraPreviewNetworkDecision(ipv6, vpn, listOf(ethernet))))
        val fullTunnel = vpn.copy(routes = vpn.routes!! + route("::/0"))
        assertFalse(overlaps(cameraPreviewNetworkDecision(ipv6, fullTunnel, listOf(ethernet))))
        val subnet = vpn.copy(routes = listOf(route("2001:db8:1234::/64")))
        assertFalse(overlaps(cameraPreviewNetworkDecision(ipv6, subnet, listOf(ethernet))))
    }

    @Test fun nonByteAlignedPrefixesMatchOnlyTheirSubnet() {
        val prefix = route("111.111.111.32/27")
        assertTrue(prefix.matches(target.address.toList()))
        assertFalse(prefix.matches(InetAddress.getByName("111.111.111.64").address.toList()))
    }

    @Test fun numericEndpointsOnlyAndUnknownAddressesDisableOverlap() {
        for (endpoint in listOf(null, "jetson.local:8080", "999.1.1.1:8080", "127.0.0.1:8080",
            "0.0.0.0:8080", "224.0.0.1:8080", "111.111.111:8080", "[::1]:8080", "[:::]:8080")) {
            assertNull(endpoint, cameraPreviewNumericAddress(endpoint))
        }
        assertFalse(overlaps(cameraPreviewNetworkDecision(null, vpn, listOf(wifi))))
    }
}
