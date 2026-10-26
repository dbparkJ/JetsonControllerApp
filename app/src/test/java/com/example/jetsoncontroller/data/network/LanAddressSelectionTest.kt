package com.example.jetsoncontroller.data.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanAddressSelectionTest {
    private fun address(host: String) = InetAddress.getByName(host)

    @Test
    fun `Docker veth and wlan link local IPv6 are excluded regardless of answer order`() {
        val veth = address("fe80::42:acff:fe11:2")
        val wlan = address("fe80::1234")
        val reachable = address("111.111.111.45")
        val tablet = LanAddressPrefix(address("111.111.111.72"), 24)
        assertEquals(reachable, selectLanServiceAddress(listOf(veth, wlan, reachable), listOf(tablet)))
        assertEquals(reachable, selectLanServiceAddress(listOf(reachable, wlan, veth), listOf(tablet)))
        assertNull(selectLanServiceAddress(listOf(veth, wlan), listOf(tablet)))
        assertNull(selectLanReconnectAddress(veth, null, listOf(tablet)))
        assertEquals(reachable, selectLanReconnectAddress(veth, reachable, listOf(tablet)))
        assertNull(selectLanReconnectAddress(address("169.254.1.1"), null, emptyList()))
        for (host in listOf("0.0.0.0", "127.0.0.1", "224.0.0.1", "::", "::1", "ff02::1")) {
            assertNull(selectLanServiceAddress(listOf(address(host)), emptyList()))
            assertNull(selectLanReconnectAddress(address(host), null, emptyList()))
        }
    }

    @Test
    fun `kernel P2P prefix excludes Samsung service without Network or local LAN prefixes`() {
        val direct = kernelWifiDirectPrefixes(listOf(
            WifiDirectInterfaceAddressCandidate("p2p-wlan0-0", address("192.168.49.2"), 24),
            WifiDirectInterfaceAddressCandidate("wlan0", address("10.20.30.2"), 24),
            WifiDirectInterfaceAddressCandidate("eth0", address("172.16.4.2"), 24)
        ))
        val owner = address("192.168.49.1")
        val lan = address("10.20.30.40")
        assertEquals(1, direct.size)
        assertNull(selectLanServiceAddress(listOf(owner), emptyList(), direct))
        assertEquals(lan, selectLanServiceAddress(listOf(owner, lan), emptyList(), direct))
        assertEquals(lan, selectLanServiceAddress(listOf(lan, owner), emptyList(), direct))
        assertNull(selectLanReconnectAddress(owner, null, emptyList(), direct))
        assertNull(selectLanReconnectAddress(owner, owner, emptyList(), direct))
        assertEquals(lan, selectLanReconnectAddress(lan, owner, emptyList(), direct))
        val phone = LanAddressPrefix(address("10.20.30.2"), 24)
        assertEquals(lan, selectLanReconnectAddress(owner, lan, listOf(phone), direct))
        assertNull(selectLanReconnectAddress(owner, owner, listOf(LanAddressPrefix(owner, 24)), direct))
    }

    @Test
    fun `P2P exclusion follows actual nonstandard IPv4 and IPv6 prefixes only`() {
        val direct = kernelWifiDirectPrefixes(listOf(
            WifiDirectInterfaceAddressCandidate("P2P-wlan0-1", address("10.1.1.100"), 25),
            WifiDirectInterfaceAddressCandidate("wifi-direct0", address("fd12:3456::2"), 64)
        ))
        assertNull(selectLanServiceAddress(listOf(address("10.1.1.80")), emptyList(), direct))
        assertNull(selectLanServiceAddress(listOf(address("fd12:3456::1")), emptyList(), direct))
        assertNull(selectLanReconnectAddress(address("fd12:3456::1"), null, emptyList(), direct))
        for (host in listOf("10.1.1.200", "192.168.49.1", "172.16.4.10", "fd12:3457::1", "2001:db8::1")) {
            assertEquals(address(host), selectLanServiceAddress(listOf(address(host)), emptyList(), direct))
            assertEquals(address(host), selectLanReconnectAddress(address(host), null, emptyList(), direct))
        }
        val ipv6 = address("2001:db8::1")
        val local = LanAddressPrefix(address("2001:db8::2"), 64)
        assertEquals(ipv6, selectLanServiceAddress(listOf(address("172.16.4.10"), ipv6), listOf(local), direct))
    }

    @Test
    fun `normal LAN on usual P2P subnet and invalid kernel prefixes stay unblocked`() {
        val direct = kernelWifiDirectPrefixes(listOf(
            WifiDirectInterfaceAddressCandidate("wlan0", address("192.168.49.2"), 24),
            WifiDirectInterfaceAddressCandidate("eth0", address("10.0.0.2"), 8),
            WifiDirectInterfaceAddressCandidate("p2p0", address("0.0.0.0"), 24),
            WifiDirectInterfaceAddressCandidate("p2p0", address("10.0.0.2"), 0),
            WifiDirectInterfaceAddressCandidate("p2p0", address("10.0.0.2"), 33)
        ))
        assertEquals(emptyList<LanAddressPrefix>(), direct)
        val lan = address("192.168.49.1")
        assertEquals(lan, selectLanServiceAddress(listOf(lan), emptyList(), direct))
        assertEquals(lan, selectLanReconnectAddress(lan, null, emptyList(), direct))
    }

    @Test
    fun `reconnect recovers authenticated wifi after DNS switches to unreachable ethernet`() {
        val ethernet = InetAddress.getByName("192.168.1.102")
        val wifi = InetAddress.getByName("111.111.111.67")
        val phone = LanAddressPrefix(InetAddress.getByName("111.111.111.192"), 24)
        assertEquals(wifi, selectLanReconnectAddress(ethernet, wifi, listOf(phone)))
    }

    @Test
    fun `reconnect accepts a new advertised address on the current subnet`() {
        val previous = InetAddress.getByName("111.111.111.67")
        val current = InetAddress.getByName("111.111.111.68")
        val phone = LanAddressPrefix(InetAddress.getByName("111.111.111.192"), 24)
        assertEquals(current, selectLanReconnectAddress(current, previous, listOf(phone)))
    }

    @Test
    fun `reconnect does not reuse old wifi after network change or missing network access`() {
        val ethernet = InetAddress.getByName("192.168.1.102")
        val wifi = InetAddress.getByName("111.111.111.67")
        val newNetwork = LanAddressPrefix(InetAddress.getByName("192.168.1.50"), 24)
        assertEquals(ethernet, selectLanReconnectAddress(ethernet, wifi, listOf(newNetwork)))
        assertEquals(ethernet, selectLanReconnectAddress(ethernet, wifi, emptyList()))
        assertEquals(ethernet, selectLanReconnectAddress(ethernet, null, listOf(newNetwork)))
    }

    @Test
    fun `dual interface Jetson selects phone reachable wifi regardless of DNS order`() {
        val ethernet = InetAddress.getByName("192.168.1.102")
        val wifi = InetAddress.getByName("111.111.111.67")
        val phone = LanAddressPrefix(InetAddress.getByName("111.111.111.192"), 24)
        assertEquals(wifi, selectLanServiceAddress(listOf(ethernet, wifi), listOf(phone)))
        assertEquals(wifi, selectLanServiceAddress(listOf(wifi, ethernet), listOf(phone)))
    }

    @Test
    fun `routed addresses still work when no candidate shares the phones subnet`() {
        val routed = InetAddress.getByName("10.20.30.40")
        assertEquals(routed, selectLanServiceAddress(listOf(routed), emptyList()))
        assertNull(selectLanServiceAddress(listOf(InetAddress.getByName("127.0.0.1")), emptyList()))
        assertNull(selectLanServiceAddress(listOf(InetAddress.getByName("fe80::1234")), emptyList()))
    }

    @Test
    fun `non byte aligned subnet selects matching address instead of adjacent subnet`() {
        val other = InetAddress.getByName("10.1.1.200")
        val reachable = InetAddress.getByName("10.1.1.80")
        val phone = LanAddressPrefix(InetAddress.getByName("10.1.1.100"), 25)
        assertEquals(reachable, selectLanServiceAddress(listOf(other, reachable), listOf(phone)))
    }
}
