package com.example.jetsoncontroller.data.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiDirectPeer(
    val name: String,
    val deviceAddress: String,
    val status: Int
)

data class WifiDirectState(
    val enabled: Boolean = false,
    val discovering: Boolean = false,
    val peers: List<WifiDirectPeer> = emptyList(),
    val connected: Boolean = false,
    val groupOwnerAddress: String? = null,
    val error: String? = null
)

class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    private val _state = MutableStateFlow(WifiDirectState())
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _state.value = _state.value.copy(enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peerList ->
                        val peers = peerList.deviceList.map { 
                            WifiDirectPeer(it.deviceName ?: "Unknown", it.deviceAddress, it.status) 
                        }
                        _state.value = _state.value.copy(peers = peers)
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel) { info ->
                            if (info.groupFormed && info.groupOwnerAddress != null) {
                                _state.value = _state.value.copy(
                                    connected = true,
                                    groupOwnerAddress = info.groupOwnerAddress.hostAddress
                                )
                            }
                        }
                    } else {
                        _state.value = _state.value.copy(connected = false, groupOwnerAddress = null)
                    }
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _state.value = _state.value.copy(discovering = true)
            }
            override fun onFailure(reason: Int) {
                _state.value = _state.value.copy(discovering = false, error = "Discovery failed: $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(peer: WifiDirectPeer) {
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            groupOwnerIntent = 0 // Prefer client
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {
                _state.value = _state.value.copy(error = "Connect failed: $reason")
            }
        })
    }

    fun disconnect() {
        manager?.removeGroup(channel, null)
    }
}
