package com.example.jetsoncontroller.data.alerts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.alertDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "device_alerts")

data class AlertSettings(
    val storageEnabled: Boolean = true,
    val storageThresholdPercent: Int = 85,
    val temperatureEnabled: Boolean = true,
    val temperatureThresholdC: Int = 80,
    val pipelineStartedEnabled: Boolean = true,
    val pipelineFailedEnabled: Boolean = true,
    val uploadStartedEnabled: Boolean = true,
    val uploadEndedEnabled: Boolean = true,
    val storageAlertLatched: Boolean = false,
    val temperatureAlertLatched: Boolean = false
)

class AlertPreferencesStore(context: Context) {
    private val dataStore = context.applicationContext.alertDataStore

    val settings: Flow<AlertSettings> = dataStore.data.map { preferences ->
        AlertSettings(
            storageEnabled = preferences[STORAGE_ENABLED] ?: true,
            storageThresholdPercent = preferences[STORAGE_THRESHOLD] ?: 85,
            temperatureEnabled = preferences[TEMPERATURE_ENABLED] ?: true,
            temperatureThresholdC = preferences[TEMPERATURE_THRESHOLD] ?: 80,
            pipelineStartedEnabled = preferences[PIPELINE_STARTED_ENABLED] ?: true,
            pipelineFailedEnabled = preferences[PIPELINE_FAILED_ENABLED] ?: true,
            uploadStartedEnabled = preferences[UPLOAD_STARTED_ENABLED] ?: true,
            uploadEndedEnabled = preferences[UPLOAD_ENDED_ENABLED] ?: true,
            storageAlertLatched = preferences[STORAGE_LATCHED] ?: false,
            temperatureAlertLatched = preferences[TEMPERATURE_LATCHED] ?: false
        )
    }

    suspend fun setStorageEnabled(enabled: Boolean) {
        dataStore.edit { it[STORAGE_ENABLED] = enabled }
    }

    suspend fun setStorageThreshold(percent: Int) {
        dataStore.edit {
            it[STORAGE_THRESHOLD] = percent.coerceIn(50, 99)
            it[STORAGE_LATCHED] = false
        }
    }

    suspend fun setTemperatureEnabled(enabled: Boolean) {
        dataStore.edit { it[TEMPERATURE_ENABLED] = enabled }
    }

    suspend fun setTemperatureThreshold(celsius: Int) {
        dataStore.edit {
            it[TEMPERATURE_THRESHOLD] = celsius.coerceIn(40, 110)
            it[TEMPERATURE_LATCHED] = false
        }
    }

    suspend fun setPipelineStartedEnabled(enabled: Boolean) {
        dataStore.edit { it[PIPELINE_STARTED_ENABLED] = enabled }
    }

    suspend fun setPipelineFailedEnabled(enabled: Boolean) {
        dataStore.edit { it[PIPELINE_FAILED_ENABLED] = enabled }
    }

    suspend fun setUploadStartedEnabled(enabled: Boolean) {
        dataStore.edit { it[UPLOAD_STARTED_ENABLED] = enabled }
    }

    suspend fun setUploadEndedEnabled(enabled: Boolean) {
        dataStore.edit { it[UPLOAD_ENDED_ENABLED] = enabled }
    }

    suspend fun setLatches(storage: Boolean, temperature: Boolean) {
        dataStore.edit {
            it[STORAGE_LATCHED] = storage
            it[TEMPERATURE_LATCHED] = temperature
        }
    }

    private companion object {
        val STORAGE_ENABLED = booleanPreferencesKey("storage_enabled")
        val STORAGE_THRESHOLD = intPreferencesKey("storage_threshold")
        val TEMPERATURE_ENABLED = booleanPreferencesKey("temperature_enabled")
        val TEMPERATURE_THRESHOLD = intPreferencesKey("temperature_threshold")
        val PIPELINE_STARTED_ENABLED = booleanPreferencesKey("pipeline_started_enabled")
        val PIPELINE_FAILED_ENABLED = booleanPreferencesKey("pipeline_failed_enabled")
        val UPLOAD_STARTED_ENABLED = booleanPreferencesKey("upload_started_enabled")
        val UPLOAD_ENDED_ENABLED = booleanPreferencesKey("upload_ended_enabled")
        val STORAGE_LATCHED = booleanPreferencesKey("storage_latched")
        val TEMPERATURE_LATCHED = booleanPreferencesKey("temperature_latched")
    }
}
