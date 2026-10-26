package com.example.jetsoncontroller.ui.sensors

import java.net.InetAddress

/** Framework-free snapshots; null routes mean that the route table could not be read. */
internal data class CameraPreviewNetwork(
    val vpn: Boolean = false,
    val wifiOrEthernet: Boolean = false,
    val unmetered: Boolean = false,
    val routes: List<CameraPreviewRoute>? = null
)

internal data class CameraPreviewRoute(
    val destination: List<Byte>,
    val prefixLength: Int,
    val hasGateway: Boolean = false,
    val unicast: Boolean = true
) {
    fun matches(address: List<Byte>): Boolean =
        destination.size == address.size && prefixLength in 0..(address.size * 8) &&
            (0 until prefixLength).all { bit ->
                val mask = 1 shl (7 - bit % 8)
                (destination[bit / 8].toInt() and mask) == (address[bit / 8].toInt() and mask)
            }
}

internal data class CameraPreviewNetworkDecision(
    val localLanRoute: Boolean = false,
    val unmetered: Boolean = false
)

/** Accept only numeric literals, including bracketed IPv6 endpoints. Never perform DNS. */
internal fun cameraPreviewNumericAddress(endpoint: String?): InetAddress? {
    val host = endpoint?.let {
        if (it.startsWith("[")) it.substringAfter('[').substringBefore(']')
        else it.substringBefore(':')
    } ?: return null
    val ipv4 = host.split('.').let { parts ->
        parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all { it in '0'..'9' } &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
    val ipv6 = host.contains(':') && host.matches(Regex("[0-9a-fA-F:]+"))
    if (!ipv4 && !ipv6) return null
    return runCatching { InetAddress.getByName(host) }.getOrNull()?.takeUnless {
        it.isLoopbackAddress || it.isAnyLocalAddress || it.isMulticastAddress
    }
}

internal fun cameraPreviewNetworkDecision(
    address: InetAddress?,
    active: CameraPreviewNetwork,
    fallbackNetworks: List<CameraPreviewNetwork> = emptyList(),
    underlyingNetworks: List<CameraPreviewNetwork>? = null
): CameraPreviewNetworkDecision {
    val bytes = address?.address?.toList()
    if (!active.vpn) return CameraPreviewNetworkDecision(
        localLanRoute = bytes != null && active.wifiOrEthernet && active.routes
            ?.filter { it.matches(bytes) }?.maxByOrNull { it.prefixLength }
            ?.let { it.prefixLength > 0 && !it.hasGateway && it.unicast } == true,
        unmetered = active.unmetered
    )
    if (bytes == null || active.routes == null) return CameraPreviewNetworkDecision()
    // Include default routes. Any best VPN route capturing the target keeps overlap off.
    if (active.routes.filter { it.matches(bytes) }.maxByOrNull { it.prefixLength } != null) {
        return CameraPreviewNetworkDecision()
    }
    val candidates = (underlyingNetworks ?: fallbackNetworks)
        .filter { !it.vpn && it.wifiOrEthernet }
        .mapNotNull { network ->
            network.routes?.filter { it.matches(bytes) }?.maxByOrNull { it.prefixLength }
                ?.let { network to it }
        }
    val longest = candidates.maxOfOrNull { it.second.prefixLength }
    // Multiple equally specific interfaces do not identify the actual egress reliably.
    val (network, route) = candidates.filter { it.second.prefixLength == longest }
        .singleOrNull() ?: return CameraPreviewNetworkDecision()
    return CameraPreviewNetworkDecision(
        localLanRoute = route.prefixLength > 0 && !route.hasGateway && route.unicast,
        unmetered = network.unmetered
    )
}
