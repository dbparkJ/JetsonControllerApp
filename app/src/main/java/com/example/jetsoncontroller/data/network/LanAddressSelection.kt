package com.example.jetsoncontroller.data.network

import java.net.Inet4Address
import java.net.InetAddress

internal data class LanAddressPrefix(val address: InetAddress, val prefixLength: Int)

/** Kernel interface prefixes also work when Android omits the P2P Network. */
internal fun kernelWifiDirectPrefixes(
    candidates: List<WifiDirectInterfaceAddressCandidate>
): List<LanAddressPrefix> = candidates.filter {
    isWifiDirectInterface(it.interfaceName) &&
        !it.address.isAnyLocalAddress && !it.address.isLoopbackAddress &&
        !it.address.isMulticastAddress &&
        it.prefixLength in 1..(it.address.address.size * 8)
}.map { LanAddressPrefix(it.address, it.prefixLength) }

internal fun isWifiDirectInterface(name: String): Boolean =
    name.contains("p2p", ignoreCase = true) || name.contains("wifi-direct", ignoreCase = true)

/** DNS can replace a Wi-Fi answer with another interface while the app is backgrounded. */
internal fun selectLanReconnectAddress(
    advertised: InetAddress,
    lastAuthenticated: InetAddress?,
    localPrefixes: List<LanAddressPrefix>,
    directPrefixes: List<LanAddressPrefix> = emptyList()
): InetAddress? = if (
    (!isUsableLanAddress(advertised) || directPrefixes.any { lanPrefixContains(it, advertised) } ||
        localPrefixes.none { lanPrefixContains(it, advertised) }) &&
    lastAuthenticated != null &&
    isUsableLanAddress(lastAuthenticated) &&
    directPrefixes.none { lanPrefixContains(it, lastAuthenticated) } &&
    localPrefixes.any { lanPrefixContains(it, lastAuthenticated) }
) lastAuthenticated else advertised.takeUnless { candidate ->
    !isUsableLanAddress(candidate) || directPrefixes.any { lanPrefixContains(it, candidate) }
}

/** A multi-interface Jetson may advertise Ethernet before its reachable Wi-Fi address. */
internal fun selectLanServiceAddress(
    advertised: List<InetAddress>,
    localPrefixes: List<LanAddressPrefix>,
    directPrefixes: List<LanAddressPrefix> = emptyList()
): InetAddress? = advertised
    .filterNot { candidate -> directPrefixes.any { lanPrefixContains(it, candidate) } }
    .filter(::isUsableLanAddress)
    .distinct()
    .sortedWith(
        compareByDescending<InetAddress> { candidate ->
            localPrefixes.any { lanPrefixContains(it, candidate) }
        }.thenByDescending { it is Inet4Address }
    )
    .firstOrNull()

// Remote interface names are absent from NSD answers. Docker veth IPv6 answers
// are normally link-local; scoped URLs cannot be used by the HTTPS client.
private fun isUsableLanAddress(address: InetAddress): Boolean =
    !address.isAnyLocalAddress && !address.isLoopbackAddress &&
        !address.isMulticastAddress && !address.isLinkLocalAddress

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
