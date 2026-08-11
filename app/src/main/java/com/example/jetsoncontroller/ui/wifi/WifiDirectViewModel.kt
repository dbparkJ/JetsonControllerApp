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
        repository.startWifiDirectDiscovery()
    }

    fun stopDiscovery() {
        repository.stopWifiDirectDiscovery()
    }

    fun connect(peer: WifiDirectPeer) {
        repository.connectWifiDirect(peer)
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
