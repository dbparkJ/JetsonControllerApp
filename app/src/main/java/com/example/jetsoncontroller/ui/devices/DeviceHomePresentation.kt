package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.model.JetsonStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The one action the navy band of the current device offers. */
internal enum class DeviceNextAction { RECONNECT, VIEW_RUN, START_COLLECTION, NONE }

/** Offline comes first: the only useful action then is to reach the device again. */
internal fun deviceNextAction(hasDevice: Boolean, online: Boolean, runActive: Boolean): DeviceNextAction =
    when {
        !hasDevice -> DeviceNextAction.NONE
        !online -> DeviceNextAction.RECONNECT
        runActive -> DeviceNextAction.VIEW_RUN
        else -> DeviceNextAction.START_COLLECTION
    }

/**
 * Reboot and power-off are available only when the device supports them, is reachable,
 * no other operation runs, and no collection is in progress (B3). A running collection is
 * an explicit blocker, not only a side effect of the other checks.
 */
internal fun devicePowerActionEnabled(
    powerCommandsEnabled: Boolean,
    online: Boolean,
    operationInProgress: Boolean,
    runActive: Boolean
): Boolean = powerCommandsEnabled && online && !operationInProgress && !runActive

/** The single item the 확인할 일 row may show. */
internal data class DeviceAttention(val title: String, val detail: String)

/** A stored collection that has no matching server receipt yet asks for a transfer. */
internal fun unsentCollectionAttention(deviceName: String, pendingTransfer: Boolean): DeviceAttention? =
    if (!pendingTransfer) null else DeviceAttention(
        title = "서버로 보내지 않은 수집 1건",
        detail = "$deviceName · 전송 탭에서 보낼 수 있습니다."
    )

/** "방금", "N분 전", "N시간 전", and the local date and time for anything older. */
internal fun formatSeenAt(epochMillis: Long, nowEpochMillis: Long): String {
    val elapsedSeconds = (nowEpochMillis - epochMillis).coerceAtLeast(0L) / 1_000L
    return when {
        elapsedSeconds < 60L -> "방금"
        elapsedSeconds < 3_600L -> "${elapsedSeconds / 60L}분 전"
        elapsedSeconds < 86_400L -> "${elapsedSeconds / 3_600L}시간 전"
        else -> SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(epochMillis))
    }
}

/** Freshness of live values, for example "5초 전 갱신". Null when the age is unknown. */
internal fun formatFreshness(ageSeconds: Long?): String? = when {
    ageSeconds == null -> null
    ageSeconds < 60L -> "${ageSeconds}초 전 갱신"
    else -> "${ageSeconds / 60L}분 전 갱신"
}

/** Only a fresh reading from a device that is online may refresh its stored snapshot. */
internal fun shouldRecordSnapshot(online: Boolean, statusIsCurrent: Boolean, observedAt: Long?): Boolean =
    online && statusIsCurrent && observedAt != null

/**
 * Builds the snapshot for one fresh status. Zero or empty values mean the device did not
 * report them, so they stay unknown instead of being saved as real readings.
 */
internal fun snapshotFromStatus(
    deviceId: String,
    deviceName: String,
    status: JetsonStatus,
    observedAtEpochMillis: Long,
    pendingTransferCount: Int?,
    runningLabel: String?
): DeviceSnapshot {
    val storageKnown = status.storageTotalBytes > 0L
    val memoryKnown = status.ramTotalMb > 0
    return DeviceSnapshot(
        deviceId = deviceId,
        deviceName = deviceName,
        lastSeenAtEpochMillis = observedAtEpochMillis,
        temperatureC = status.temperatureC.takeIf { it > 0f },
        storagePercent = status.storagePercent.takeIf { storageKnown },
        storageAvailableBytes = status.storageAvailableBytes.takeIf { storageKnown },
        storageTotalBytes = status.storageTotalBytes.takeIf { storageKnown },
        cpuPercent = status.cpuPercent,
        gpuPercent = status.gpuPercent,
        memoryUsedMb = status.ramUsedMb.takeIf { memoryKnown },
        memoryTotalMb = status.ramTotalMb.takeIf { memoryKnown },
        runningLabel = runningLabel,
        pendingTransferCount = pendingTransferCount
    )
}

/** Display text for the device figures. Unknown values say so instead of showing zero. */
internal data class DeviceFigures(
    val temperature: String,
    val storageFree: String,
    val storageFreeDetail: String?,
    val cpuGpu: String,
    val memory: String,
    val memoryDetail: String?,
    /** Set when the values come from the stored snapshot rather than from a live status. */
    val sourceNote: String?
)

/**
 * Figures for one device. A current live status wins; otherwise the stored snapshot is used
 * and labelled as the last known value, so an offline device still shows its numbers.
 */
internal fun deviceFigures(live: JetsonStatus?, snapshot: DeviceSnapshot?): DeviceFigures {
    val storageFreeBytes = if (live != null) {
        live.storageAvailableBytes.takeIf { live.storageTotalBytes > 0L }
    } else {
        snapshot?.storageAvailableBytes
    }
    val storageUsedPercent = if (live != null) {
        live.storagePercent.takeIf { live.storageTotalBytes > 0L }
    } else {
        snapshot?.storagePercent
    }
    val temperature = if (live != null) live.temperatureC.takeIf { it > 0f } else snapshot?.temperatureC
    val cpu = if (live != null) live.cpuPercent else snapshot?.cpuPercent
    val gpu = if (live != null) live.gpuPercent else snapshot?.gpuPercent
    val memoryUsed = if (live != null) live.ramUsedMb.takeIf { live.ramTotalMb > 0 } else snapshot?.memoryUsedMb
    val memoryTotal = if (live != null) live.ramTotalMb.takeIf { it > 0 } else snapshot?.memoryTotalMb
    val sourceNote = if (live == null && snapshot != null) "마지막 값 기준" else null
    return DeviceFigures(
        temperature = temperature?.let { "${it.roundToInt()}°C" } ?: UNKNOWN_FIGURE,
        storageFree = storageUsedPercent?.let { "${(100 - it).coerceIn(0, 100)}%" } ?: UNKNOWN_FIGURE,
        storageFreeDetail = storageFreeBytes?.takeIf { it > 0L }?.let { formatStorage(it) },
        cpuGpu = if (cpu != null && gpu != null) "$cpu% · $gpu%" else UNKNOWN_FIGURE,
        memory = if (memoryUsed != null && memoryTotal != null && memoryTotal > 0) {
            formatMemory(memoryUsed)
        } else {
            UNKNOWN_FIGURE
        },
        memoryDetail = memoryTotal?.takeIf { it > 0 }?.let { "${formatMemory(it)} 중" },
        sourceNote = sourceNote
    )
}

private const val UNKNOWN_FIGURE = "확인 안 됨"

/** Decimal gigabytes, or terabytes from 1 TB, as the storage figures show them. */
internal fun formatStorage(bytes: Long): String =
    if (bytes >= 1_000_000_000_000L) {
        String.format(Locale.US, "%.1f TB", bytes / 1_000_000_000_000.0)
    } else {
        String.format(Locale.US, "%.0f GB", bytes / 1_000_000_000.0)
    }

/** Megabytes shown as gigabytes with one decimal. */
internal fun formatMemory(megabytes: Int): String =
    String.format(Locale.US, "%.1f GB", megabytes / 1_024.0)
