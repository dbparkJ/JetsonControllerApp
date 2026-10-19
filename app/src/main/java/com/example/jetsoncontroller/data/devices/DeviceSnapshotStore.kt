package com.example.jetsoncontroller.data.devices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jetsoncontroller.model.MetricValidity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.deviceSnapshotDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "device_snapshots")

/**
 * The last values the phone saw from one device. It is shown while the device is offline,
 * so every reading is optional: a device may not report it, and a missing value is shown
 * as unknown rather than as zero.
 */
data class DeviceSnapshot(
    val deviceId: String,
    val deviceName: String,
    val lastSeenAtEpochMillis: Long,
    val temperatureC: Float? = null,
    val storagePercent: Int? = null,
    val storageAvailableBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val cpuPercent: Int? = null,
    val gpuPercent: Int? = null,
    val memoryUsedMb: Int? = null,
    val memoryTotalMb: Int? = null,
    val runningLabel: String? = null,
    val pendingTransferCount: Int? = null,
    val metricValidity: Map<String, MetricValidity>? = null
)

/**
 * Keeps one [DeviceSnapshot] per device on the phone, so the device root can show the
 * last known state without a connection. Device ids are matched without regard to case.
 */
class DeviceSnapshotStore(context: Context) {
    private val dataStore = context.applicationContext.deviceSnapshotDataStore

    val snapshots: Flow<Map<String, DeviceSnapshot>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> decodeDeviceSnapshots(preferences[SNAPSHOTS_JSON]) }

    /** Stores a reading unless a newer one for the same device is already saved. */
    suspend fun record(snapshot: DeviceSnapshot) {
        dataStore.edit { preferences ->
            preferences[SNAPSHOTS_JSON] = encodeDeviceSnapshots(
                mergeDeviceSnapshot(decodeDeviceSnapshots(preferences[SNAPSHOTS_JSON]), snapshot)
            )
        }
    }

    private companion object {
        val SNAPSHOTS_JSON = stringPreferencesKey("snapshots_json")
    }
}

private val snapshotGson = Gson()
private val snapshotListType = object : TypeToken<List<DeviceSnapshot>>() {}.type

/** The map key for a device id. Ids are compared without regard to case or outer spaces. */
internal fun deviceSnapshotKey(deviceId: String): String = deviceId.trim().lowercase()

/**
 * Update rule: a reading replaces the stored one only when it is at least as recent. An
 * older reading that arrives late, for example from a retry, never overwrites newer data.
 */
internal fun mergeDeviceSnapshot(
    current: Map<String, DeviceSnapshot>,
    incoming: DeviceSnapshot
): Map<String, DeviceSnapshot> {
    val key = deviceSnapshotKey(incoming.deviceId)
    val existing = current[key]
    if (existing != null && incoming.lastSeenAtEpochMillis < existing.lastSeenAtEpochMillis) {
        return current
    }
    return current + (key to incoming.copy(deviceId = key).withValidMetrics())
}

internal fun encodeDeviceSnapshots(snapshots: Map<String, DeviceSnapshot>): String =
    snapshotGson.toJson(snapshots.values.map { it.withValidMetrics() }, snapshotListType)

/** Legacy snapshots have no validity metadata, so their zero CPU/GPU readings are ambiguous. */
internal fun DeviceSnapshot.withValidMetrics(): DeviceSnapshot {
    fun valid(name: String): Boolean = metricValidity?.get(name)?.validity?.let { it == "valid" } ?: true
    return copy(
        temperatureC = temperatureC.takeIf { valid("temperatureC") },
        storagePercent = storagePercent.takeIf { valid("storagePercent") && valid("storageTotalBytes") },
        storageAvailableBytes = storageAvailableBytes.takeIf { valid("storageAvailableBytes") && valid("storageTotalBytes") },
        storageTotalBytes = storageTotalBytes.takeIf { valid("storageTotalBytes") },
        cpuPercent = cpuPercent.takeIf { valid("cpuPercent") },
        gpuPercent = gpuPercent.takeIf { valid("gpuPercent") },
        memoryUsedMb = memoryUsedMb.takeIf { valid("ramUsedMb") && valid("ramTotalMb") },
        memoryTotalMb = memoryTotalMb.takeIf { valid("ramTotalMb") }
    )
}

/** Unreadable or blank storage yields an empty map, so one bad write cannot hide every device. */
internal fun decodeDeviceSnapshots(raw: String?): Map<String, DeviceSnapshot> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        snapshotGson.fromJson<List<DeviceSnapshot>>(raw, snapshotListType).orEmpty()
            .filter { it.deviceId.isNotBlank() }
            .map { it.withValidMetrics() }
            .associateBy { deviceSnapshotKey(it.deviceId) }
    }.getOrDefault(emptyMap())
}
