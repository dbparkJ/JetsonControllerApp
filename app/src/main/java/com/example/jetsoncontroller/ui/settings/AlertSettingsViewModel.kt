package com.example.jetsoncontroller.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.alerts.AlertPreferencesStore
import com.example.jetsoncontroller.data.alerts.AlertSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertSettingsViewModel(
    private val preferences: AlertPreferencesStore
) : ViewModel() {
    val settings = preferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlertSettings()
    )

    fun setStorageEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setStorageEnabled(enabled)
    }

    fun setStorageThreshold(value: Int) = viewModelScope.launch {
        preferences.setStorageThreshold(value)
    }

    fun setTemperatureEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setTemperatureEnabled(enabled)
    }

    fun setTemperatureThreshold(value: Int) = viewModelScope.launch {
        preferences.setTemperatureThreshold(value)
    }

    class Factory(
        private val preferences: AlertPreferencesStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AlertSettingsViewModel(preferences) as T
    }
}
