package com.example.jetsoncontroller.ui.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.repository.JetsonRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class WifiDirectViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    val uiState = repository.wifiDirectState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.wifiDirectState.value
    )

    fun startDiscovery() {
        val state = repository.wifiDirectState.value
        if (!state.supported || state.cleaningUp || state.connectingPeerAddress != null || state.connected || state.discovering) return
        repository.startWifiDirectDiscovery()
    }

    fun stopDiscovery() {
        repository.stopWifiDirectDiscovery()
    }

    fun connect(peer: WifiDirectPeer) {
        val state = repository.wifiDirectState.value
        if (!canConnectDirectPeer(state, permissionGranted = true, address = peer.deviceAddress)) return
        state.peers.firstOrNull { it.deviceAddress == peer.deviceAddress }?.let(repository::connectWifiDirect)
    }

    fun retryApi() {
        if (directStage(repository.wifiDirectState.value, permissionGranted = true) != DirectStage.AUTHENTICATION_ERROR) return
        repository.retryWifiDirectApi()
    }

    fun cancel() {
        repository.cancelWifiDirectConnection()
    }

    override fun onCleared() {
        repository.stopWifiDirectDiscovery()
        super.onCleared()
    }

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WifiDirectViewModel(repository) as T
        }
    }
}
