package com.example.jetsoncontroller.data.rtk

import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RtkRelayPeerTest {
    private val owner = "192.168.49.1"
    private val ownerBytes = byteArrayOf(192.toByte(), 168.toByte(), 49, 1)

    @Test
    fun `numeric IPv4 group owner is allowed`() {
        assertEquals(RtkRelayPeerDecision.ALLOWED, checkRtkRelayPeer(ownerBytes, owner))
    }

    @Test
    fun `mapped Inet6Address reproduces old string rejection and permits the same owner bytes`() {
        // getByAddress on Inet6Address deliberately preserves the mapped family on the JVM.
        val peer = Inet6Address.getByAddress(null, mapped(ownerBytes), -1)
        assertNotEquals(owner, peer.hostAddress)
        assertEquals(RtkRelayPeerKind.IPV4_MAPPED, rtkRelayPeerKind(peer.address))
        assertEquals(RtkRelayPeerDecision.ALLOWED, checkRtkRelayPeer(peer.address, owner))
    }

    @Test
    fun `ordinary Inet4Address representation is also allowed`() {
        val peer = InetAddress.getByAddress(ownerBytes)
        assertEquals(owner, peer.hostAddress)
        assertEquals(RtkRelayPeerDecision.ALLOWED, checkRtkRelayPeer(peer.address, owner))
    }

    @Test
    fun `every other address on group owner subnet is denied in either representation`() {
        for (last in 0..255) {
            if (last == 1) continue
            val peer = ownerBytes.copyOf().apply { this[3] = last.toByte() }
            assertEquals(RtkRelayPeerDecision.DIFFERENT_PEER, checkRtkRelayPeer(peer, owner))
            assertEquals(RtkRelayPeerDecision.DIFFERENT_PEER, checkRtkRelayPeer(mapped(peer), owner))
        }
    }

    @Test
    fun `each of the four address bytes must match`() {
        for (index in ownerBytes.indices) {
            val peer = ownerBytes.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() }
            assertEquals(RtkRelayPeerDecision.DIFFERENT_PEER, checkRtkRelayPeer(peer, owner))
            assertEquals(RtkRelayPeerDecision.DIFFERENT_PEER, checkRtkRelayPeer(mapped(peer), owner))
        }
    }

    @Test
    fun `native IPv6 and IPv4 compatible addresses are denied even with the same suffix`() {
        val compatible = ByteArray(12) + ownerBytes
        val native = mapped(ownerBytes).apply { this[0] = 0x20; this[1] = 0x01 }
        val nat64 = ByteArray(12).apply { this[1] = 0x64; this[2] = 0xff.toByte(); this[3] = 0x9b.toByte() } + ownerBytes
        for (peer in listOf(compatible, native, nat64, ByteArray(16))) {
            assertEquals(RtkRelayPeerKind.IPV6, rtkRelayPeerKind(peer))
            assertEquals(RtkRelayPeerDecision.UNSUPPORTED_PEER, checkRtkRelayPeer(peer, owner))
        }
    }

    @Test
    fun `entire mapped prefix must match`() {
        for (index in 0..11) {
            val peer = mapped(ownerBytes).apply { this[index] = (this[index].toInt() xor 1).toByte() }
            assertEquals(RtkRelayPeerDecision.UNSUPPORTED_PEER, checkRtkRelayPeer(peer, owner))
        }
    }

    @Test
    fun `malformed peer byte lengths fail closed`() {
        for (length in listOf(0, 1, 3, 5, 15, 17, 32)) {
            assertEquals(RtkRelayPeerDecision.UNSUPPORTED_PEER, checkRtkRelayPeer(ByteArray(length), owner))
        }
    }

    @Test
    fun `hostnames and nonnumeric owner inputs are never resolved`() {
        for (host in listOf("localhost", "caster.example", "192.168.49.example", "", " 192.168.49.1", "192.168.49.1\n", "192.168.49.1:2101", "192.168.49.1/24")) {
            assertEquals(host, RtkRelayPeerDecision.INVALID_OWNER, checkRtkRelayPeer(ownerBytes, host))
        }
    }

    @Test
    fun `ambiguous abbreviated and out of range owner literals fail closed`() {
        for (host in listOf("192.168.49", "192.168.49.1.2", "192.168..1", "192.168.49.256", "192.168.49.-1", "192.168.49.+1", "192.168.049.1", "0xc0.168.49.1", "3232248065", "１９２.168.49.1")) {
            assertEquals(host, RtkRelayPeerDecision.INVALID_OWNER, checkRtkRelayPeer(ownerBytes, host))
        }
    }

    @Test
    fun `IPv6 and mapped text owner inputs cannot broaden IPv4 owner policy`() {
        for (host in listOf("::1", "::ffff:192.168.49.1", "[::ffff:192.168.49.1]", "fe80::1%p2p0")) {
            assertEquals(RtkRelayPeerDecision.INVALID_OWNER, checkRtkRelayPeer(mapped(ownerBytes), host))
        }
    }

    private fun mapped(ipv4: ByteArray) = ByteArray(12).apply {
        this[10] = 0xff.toByte()
        this[11] = 0xff.toByte()
    } + ipv4
}
