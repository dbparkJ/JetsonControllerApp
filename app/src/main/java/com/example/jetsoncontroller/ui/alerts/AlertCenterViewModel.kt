package com.example.jetsoncontroller.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.alerts.AlertHistoryStore
import com.example.jetsoncontroller.data.alerts.AlertRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlertCenterUiState(
    val alerts: List<AlertRecord> = emptyList(),
    val unreadCount: Int = 0
)

class AlertCenterViewModel(
    private val history: AlertHistoryStore
) : ViewModel() {
    val uiState = history.alerts
        .map { alerts ->
            AlertCenterUiState(
                alerts = alerts,
                unreadCount = alerts.count { !it.read }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlertCenterUiState()
        )

    fun markRead(alertId: String) {
        viewModelScope.launch { history.markRead(alertId) }
    }

    fun markAllRead() {
        viewModelScope.launch { history.markAllRead() }
    }

    fun delete(alertId: String) {
        viewModelScope.launch { history.delete(alertId) }
    }

    fun clear() {
        viewModelScope.launch { history.clear() }
    }

    class Factory(
        private val history: AlertHistoryStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AlertCenterViewModel(history) as T
        }
    }
}
