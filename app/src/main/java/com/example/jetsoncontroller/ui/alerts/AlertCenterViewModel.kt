package com.example.jetsoncontroller.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.alerts.AlertHistoryStore
import com.example.jetsoncontroller.data.alerts.AlertRecord
import com.example.jetsoncontroller.data.alerts.countRecentUnread
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlertCenterUiState(
    val alerts: List<AlertRecord> = emptyList(),
    /** Unread alerts from the last 24 hours. This is the number the bell shows. */
    val unreadCount: Int = 0
)

class AlertCenterViewModel(
    private val history: AlertHistoryStore,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    /** Emits once a minute so an alert leaves the bell count when it passes 24 hours. */
    private val recountTicks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(RECOUNT_INTERVAL_MS)
        }
    }

    val uiState = combine(history.alerts, recountTicks) { alerts, _ ->
        AlertCenterUiState(
            alerts = alerts,
            unreadCount = countRecentUnread(alerts, clock())
        )
    }.stateIn(
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

    private companion object {
        const val RECOUNT_INTERVAL_MS = 60_000L
    }
}
