package com.example.jetsoncontroller

import android.app.Application
import com.example.jetsoncontroller.data.alerts.AlertPreferencesStore
import com.example.jetsoncontroller.data.alerts.AlertHistoryStore
import com.example.jetsoncontroller.data.alerts.DeviceAlertMonitor
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.data.repository.JetsonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class JetsonApplication :
    Application() {

    lateinit var repository:
        JetsonRepository
        private set

    lateinit var credentialStore:
        DeviceCredentialStore
        private set

    lateinit var alertPreferences: AlertPreferencesStore
        private set

    lateinit var alertHistory: AlertHistoryStore
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {

        super.onCreate()

        credentialStore =
            DeviceCredentialStore(this)

        repository =
            JetsonRepository(this, credentialStore)

        alertPreferences = AlertPreferencesStore(this)
        alertHistory = AlertHistoryStore(this)
        DeviceAlertMonitor(
            context = this,
            repository = repository,
            preferences = alertPreferences,
            history = alertHistory,
            scope = applicationScope
        ).start()
    }
}
