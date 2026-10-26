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

data class AlertTarget(
    val deviceId: String,
    val deviceName: String? = null,
    val pipelineId: String? = null,
    val runId: String? = null,
    val transferJob: com.example.jetsoncontroller.model.UploadJob? = null
)

data class AlertRecord(
    val id: String,
    val title: String,
    val message: String,
    val destination: AlertDestination,
    val severity: AlertSeverity,
    val createdAtEpochMillis: Long,
    val read: Boolean = false,
    val target: AlertTarget? = null
)

class AlertHistoryStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context, clock: () -> Long = System::currentTimeMillis) :
        this(context.applicationContext.alertHistoryDataStore, clock)
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
        severity: AlertSeverity,
        target: AlertTarget? = null
    ): AlertRecord = addRecord(title, message, destination, severity, target)

    internal suspend fun addFanStall(
        episodeId: String,
        title: String,
        message: String,
        target: AlertTarget
    ): AlertRecord = addRecord(title, message, AlertDestination.SENSORS, AlertSeverity.ERROR, target, episodeId)

    private suspend fun addRecord(
        title: String,
        message: String,
        destination: AlertDestination,
        severity: AlertSeverity,
        target: AlertTarget?,
        fanEpisodeId: String? = null
    ): AlertRecord {
        val timestamp = clock()
        val alert = AlertRecord(
            id = fanEpisodeId ?: "$timestamp-${UUID.randomUUID()}",
            title = title,
            message = message,
            destination = destination,
            severity = severity,
            createdAtEpochMillis = timestamp,
            target = target
        )
        var saved = alert
        dataStore.edit { preferences ->
            val current = decode(preferences[ALERTS_JSON])
            val existing = fanEpisodeId?.let { id -> current.firstOrNull { it.id == id } }
            if (existing != null) {
                saved = existing
                return@edit
            }
            preferences[ALERTS_JSON] = gson.toJson(
                appendAlert(current, alert)
            )
        }
        return saved
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

/** How long an unread alert keeps counting toward the bell. */
internal const val RECENT_UNREAD_WINDOW_MILLIS = 24L * 60 * 60 * 1000

/**
 * Unread alerts raised within [windowMillis] of [nowEpochMillis]. This is the bell count.
 * An alert older than the window stops counting even if nobody opened the alert center.
 */
internal fun countRecentUnread(
    alerts: List<AlertRecord>,
    nowEpochMillis: Long,
    windowMillis: Long = RECENT_UNREAD_WINDOW_MILLIS
): Int = alerts.count { alert ->
    !alert.read && alert.createdAtEpochMillis > nowEpochMillis - windowMillis
}
