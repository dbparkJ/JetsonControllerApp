package com.example.jetsoncontroller.data.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal data class LanAddressPrefix(val address: InetAddress, val prefixLength: Int)

/** DNS can replace a Wi-Fi answer with another interface while the app is backgrounded. */
internal fun selectLanReconnectAddress(
    advertised: InetAddress,
    lastAuthenticated: InetAddress?,
    localPrefixes: List<LanAddressPrefix>
): InetAddress = if (
    localPrefixes.none { lanPrefixContains(it, advertised) } &&
    lastAuthenticated != null &&
    localPrefixes.any { lanPrefixContains(it, lastAuthenticated) }
) lastAuthenticated else advertised

/** A multi-interface Jetson may advertise Ethernet before its reachable Wi-Fi address. */
internal fun selectLanServiceAddress(
    advertised: List<InetAddress>,
    localPrefixes: List<LanAddressPrefix>
): InetAddress? = advertised
    .filterNot { it.isAnyLocalAddress || it.isLoopbackAddress || it.isMulticastAddress }
    // Scoped link-local URLs are not supported by the HTTPS client.
    .filterNot { it is Inet6Address && it.isLinkLocalAddress }
    .distinct()
    .sortedWith(
        compareByDescending<InetAddress> { candidate ->
            localPrefixes.any { lanPrefixContains(it, candidate) }
        }.thenByDescending { it is Inet4Address }
    )
    .firstOrNull()

private fun lanPrefixContains(prefix: LanAddressPrefix, candidate: InetAddress): Boolean {
    val local = prefix.address.address
    val remote = candidate.address
    if (local.size != remote.size || prefix.prefixLength !in 1..(local.size * 8)) return false
    val wholeBytes = prefix.prefixLength / 8
    if ((0 until wholeBytes).any { local[it] != remote[it] }) return false
    val remainingBits = prefix.prefixLength % 8
    if (remainingBits == 0) return true
    val mask = (0xff shl (8 - remainingBits)) and 0xff
    return (local[wholeBytes].toInt() and mask) == (remote[wholeBytes].toInt() and mask)
}
