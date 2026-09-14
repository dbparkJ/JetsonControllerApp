package com.example.jetsoncontroller.data.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanAddressSelectionTest {
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
