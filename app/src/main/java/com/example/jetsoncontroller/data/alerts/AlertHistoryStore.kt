package com.example.jetsoncontroller.data.alerts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.alertHistoryDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "alert_history")

enum class AlertDestination {
    DASHBOARD,
    STORAGE,
    SENSORS,
    PIPELINES,
    UPLOAD_QUEUE
}

enum class AlertSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class AlertRecord(
    val id: String,
    val title: String,
    val message: String,
    val destination: AlertDestination,
    val severity: AlertSeverity,
    val createdAtEpochMillis: Long,
    val read: Boolean = false
)

class AlertHistoryStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dataStore = context.applicationContext.alertHistoryDataStore
    private val gson = Gson()
    private val listType = object : TypeToken<List<AlertRecord>>() {}.type

    val alerts: Flow<List<AlertRecord>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> decode(preferences[ALERTS_JSON]) }

    suspend fun add(
        title: String,
        message: String,
        destination: AlertDestination,
        severity: AlertSeverity
    ): AlertRecord {
        val timestamp = clock()
        val alert = AlertRecord(
            id = "$timestamp-${UUID.randomUUID()}",
            title = title,
            message = message,
            destination = destination,
            severity = severity,
            createdAtEpochMillis = timestamp
        )
        dataStore.edit { preferences ->
            preferences[ALERTS_JSON] = gson.toJson(
                appendAlert(decode(preferences[ALERTS_JSON]), alert)
            )
        }
        return alert
    }

    suspend fun markRead(alertId: String) {
        update { alerts ->
            alerts.map { alert ->
                if (alert.id == alertId) alert.copy(read = true) else alert
            }
        }
    }

    suspend fun markAllRead() {
        update { alerts -> alerts.map { it.copy(read = true) } }
    }

    suspend fun delete(alertId: String) {
        update { alerts -> alerts.filterNot { it.id == alertId } }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(ALERTS_JSON) }
    }

    private suspend fun update(transform: (List<AlertRecord>) -> List<AlertRecord>) {
        dataStore.edit { preferences ->
            preferences[ALERTS_JSON] = gson.toJson(
                transform(decode(preferences[ALERTS_JSON]))
            )
        }
    }

    private fun decode(raw: String?): List<AlertRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<AlertRecord>>(raw, listType).orEmpty()
                .sortedByDescending { it.createdAtEpochMillis }
                .take(MAX_ALERTS)
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_ALERTS = 100
        val ALERTS_JSON = stringPreferencesKey("alerts_json")
    }
}

internal fun appendAlert(
    current: List<AlertRecord>,
    alert: AlertRecord,
    limit: Int = 100
): List<AlertRecord> = (listOf(alert) + current.filterNot { it.id == alert.id })
    .sortedByDescending { it.createdAtEpochMillis }
    .take(limit.coerceAtLeast(0))
