package com.example.jetsoncontroller.data.rtk

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.Socket

internal class RtkRelayRouteException(val reason: String) : IOException(
    when (reason) {
        "CELLULAR_UNAVAILABLE" -> "사용 가능한 모바일 데이터 연결이 없습니다."
        "VPN_NOT_CELLULAR" -> "VPN의 인터넷 경로가 셀룰러 전용임을 확인할 수 없습니다."
        "NETWORK_UNKNOWN" -> "활성 인터넷 경로를 확인할 수 없습니다."
        "PERMISSION_DENIED" -> "RTK 네트워크 경로 확인 권한이 없습니다."
        else -> "RTK 네트워크 경로가 변경되어 연결을 종료했습니다."
    }
)

// SDK 36/37 expose hasTransport, but not getUnderlyingNetworks/getTransportTypes.
// Android reports a VPN's underlying transports on its public NetworkCapabilities.
internal fun isRtkCellularRoute(caps: NetworkCapabilities?, vpn: Boolean): Boolean =
    caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (if (vpn) caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        else caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) &&
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == vpn &&
        OTHER_TRANSPORTS.none(caps::hasTransport)

private val OTHER_TRANSPORTS = intArrayOf(
    NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_ETHERNET,
    NetworkCapabilities.TRANSPORT_BLUETOOTH, NetworkCapabilities.TRANSPORT_WIFI_AWARE,
    NetworkCapabilities.TRANSPORT_LOWPAN, NetworkCapabilities.TRANSPORT_USB,
    NetworkCapabilities.TRANSPORT_THREAD, NetworkCapabilities.TRANSPORT_SATELLITE
)

internal fun selectRtkRelayNetwork(cm: ConnectivityManager, cellular: Network): Network {
    try {
        val cellularCaps = cm.getNetworkCapabilities(cellular)
        if (!isRtkCellularRoute(cellularCaps, vpn = false)) {
            throw RtkRelayRouteException("CELLULAR_UNAVAILABLE")
        }
        val active = cm.activeNetwork ?: throw RtkRelayRouteException("NETWORK_UNKNOWN")
        val caps = cm.getNetworkCapabilities(active) ?: throw RtkRelayRouteException("NETWORK_UNKNOWN")
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                throw RtkRelayRouteException("NETWORK_UNKNOWN")
            }
            return cellular
        }
        if (!isRtkCellularRoute(caps, vpn = true)) throw RtkRelayRouteException("VPN_NOT_CELLULAR")
        return active
    } catch (_: SecurityException) {
        throw RtkRelayRouteException("PERMISSION_DENIED")
    }
}

/** One connection owns this observer; the manager continues to own the cellular lease. */
internal class RtkRelayRoute(
    private val cm: ConnectivityManager,
    private val cellular: Network,
    private val onInvalidated: (RtkRelayRouteException) -> Unit
) : AutoCloseable {
    val network = selectRtkRelayNetwork(cm, cellular)
    private val vpn = network != cellular
    private val sockets = mutableSetOf<Socket>()
    private var closed = false
    private var registered = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (vpn && network != this@RtkRelayRoute.network) invalidate()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            // Use the delivered snapshot: synchronous queries here can lag behind this event.
            if (vpn) {
                if (network != this@RtkRelayRoute.network || !isRtkCellularRoute(caps, vpn = true)) invalidate()
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                invalidate()
            } else if (network == cellular && !isRtkCellularRoute(caps, vpn = false)) {
                invalidate()
            }
        }

        override fun onLost(network: Network) {
            if (network == this@RtkRelayRoute.network) invalidate()
        }
    }

    @Synchronized
    fun start() {
        if (closed) throw RtkRelayRouteException("ROUTE_CHANGED")
        try {
            cm.registerDefaultNetworkCallback(callback)
            registered = true
            check()
        } catch (error: Exception) {
            close()
            if (error is SecurityException) throw RtkRelayRouteException("PERMISSION_DENIED")
            throw error
        }
    }

    @Synchronized
    fun check() {
        if (closed || selectRtkRelayNetwork(cm, cellular) != network) {
            throw RtkRelayRouteException("ROUTE_CHANGED")
        }
    }

    @Synchronized
    fun attach(socket: Socket) {
        if (closed) {
            runCatching { socket.close() }
            throw RtkRelayRouteException("ROUTE_CHANGED")
        }
        sockets.add(socket)
    }

    @Synchronized
    private fun invalidate() {
        if (closed) return
        close()
        onInvalidated(RtkRelayRouteException("ROUTE_CHANGED"))
    }

    @Synchronized
    override fun close() {
        closed = true
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        if (registered) {
            registered = false
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
}
