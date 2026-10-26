package com.example.jetsoncontroller.data.rtk

internal enum class RtkRelayPeerKind { IPV4, IPV4_MAPPED, IPV6, INVALID }

internal enum class RtkRelayPeerDecision { ALLOWED, INVALID_OWNER, UNSUPPORTED_PEER, DIFFERENT_PEER }

internal fun rtkRelayPeerKind(address: ByteArray): RtkRelayPeerKind = when {
    address.size == 4 -> RtkRelayPeerKind.IPV4
    address.size != 16 -> RtkRelayPeerKind.INVALID
    address.take(10).all { it == 0.toByte() } &&
        address[10] == 0xff.toByte() && address[11] == 0xff.toByte() -> RtkRelayPeerKind.IPV4_MAPPED
    else -> RtkRelayPeerKind.IPV6
}

/** Only the one numeric IPv4 group owner is trusted; never resolve names or allow a subnet. */
internal fun checkRtkRelayPeer(address: ByteArray, allowedHost: String): RtkRelayPeerDecision {
    val owner = parseRtkRelayOwner(allowedHost) ?: return RtkRelayPeerDecision.INVALID_OWNER
    val peer = when (rtkRelayPeerKind(address)) {
        RtkRelayPeerKind.IPV4 -> address
        RtkRelayPeerKind.IPV4_MAPPED -> address.copyOfRange(12, 16)
        else -> return RtkRelayPeerDecision.UNSUPPORTED_PEER
    }
    return if (peer.contentEquals(owner)) RtkRelayPeerDecision.ALLOWED
    else RtkRelayPeerDecision.DIFFERENT_PEER
}

private fun parseRtkRelayOwner(host: String): ByteArray? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    val bytes = ByteArray(4)
    for ((index, part) in parts.withIndex()) {
        if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' } ||
            (part.length > 1 && part[0] == '0')) return null
        val value = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        bytes[index] = value.toByte()
    }
    return bytes
}
