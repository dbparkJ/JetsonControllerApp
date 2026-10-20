package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.data.devices.withValidMetrics
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.components.geo.GeoFigure
import com.example.jetsoncontroller.ui.components.geo.GeoFigurePresentation
import com.example.jetsoncontroller.ui.components.geo.figurePresentation
import com.example.jetsoncontroller.ui.dashboard.DashboardHealth
import com.example.jetsoncontroller.ui.dashboard.DashboardHealthIssue
import com.example.jetsoncontroller.ui.dashboard.metricIsValid
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.model.Formats

internal fun sameDeviceTarget(target: String?, selected: String?): Boolean =
    !target.isNullOrBlank() && target.equals(selected, true)

internal fun devicePowerTargetAllowed(target: String?, selected: String?, connected: String?,
    dashboard: DashboardUiState, collectionBlocked: Boolean): Boolean =
    sameDeviceTarget(target, selected) && sameDeviceTarget(target, connected) &&
        dashboard.fullControlAvailable && powerUnavailableReason(dashboard, collectionBlocked).isEmpty()

internal fun deviceTransportLabel(type: TransportType?): String = when (type) {
    TransportType.LAN -> "같은 네트워크"
    TransportType.WIFI_DIRECT -> "직접 연결"
    TransportType.BLE -> "기본 연결"
    null -> "미확인"
}

internal fun deviceFigurePresentation(figure: GeoFigure): GeoFigurePresentation {
    val shown = figurePresentation(figure)
    return if (figure.label == "메모리" && figure.stale && !shown.unknown) {
        shown.copy(detail = figure.detail, staleDetail = shown.detail)
    } else shown
}

/** Missing live metrics never borrow an older number; cached numbers are explicitly stale. */
internal fun deviceFigureItems(live: JetsonStatus?, snapshot: DeviceSnapshot?): List<GeoFigure> {
    val stored = snapshot?.withValidMetrics()
    val old = deviceFigures(live, snapshot)
    val stale = live == null && stored != null
    fun known(value: String): String? = value.takeUnless { it == "미확인" }
    val bytes = if (live != null) live.storageAvailableBytes.takeIf {
        live.storageTotalBytes > 0 && live.metricIsValid("storageTotalBytes") && live.metricIsValid("storageAvailableBytes")
    } else stored?.storageAvailableBytes
    val usedMb = if (live != null) live.ramUsedMb.takeIf {
        live.ramTotalMb > 0 && live.metricIsValid("ramUsedMb") && live.metricIsValid("ramTotalMb")
    } else stored?.memoryUsedMb
    val totalMb = if (live != null) live.ramTotalMb.takeIf {
        it > 0 && live.metricIsValid("ramTotalMb")
    } else stored?.memoryTotalMb
    val temperature = if (live != null) live.temperatureC.takeIf {
        live.metricIsValid("temperatureC") && (it > 0 || live.metricValidity["temperatureC"]?.validity == "valid")
    } else stored?.temperatureC
    val memory = if (usedMb != null && totalMb != null) Formats.memory(usedMb * 1024L, totalMb * 1024L) else null
    return listOf(
        GeoFigure("온도", temperature?.let { Formats.temperature(it.toDouble()) }, stale = stale,
            exceededThreshold = temperature?.takeIf { it >= 80f }?.let { "80°C" }),
        GeoFigure("저장 여유", bytes?.let(Formats::size), stale = stale,
            detail = known(old.storageFree)?.let { "$it 남음" }),
        GeoFigure("CPU · GPU", known(old.cpuGpu), stale = stale),
        GeoFigure("메모리", memory?.substringBefore(" / "), stale = stale,
            detail = memory?.substringAfter(" / ")?.let { "전체 $it" })
    )
}

internal data class DeviceHealthItem(
    val key: String,
    val title: String,
    val detail: String,
    val kind: DashboardHealthIssue? = null
)

internal fun deviceHealthItems(deviceName: String, health: DashboardHealth): List<DeviceHealthItem> =
    health.issueKinds.zip(health.issues).map { (kind, message) ->
        DeviceHealthItem(
            key = health.issueKeys.filter { it == kind.name || it.startsWith("${kind.name}:") }.sorted().joinToString("|"),
            title = "$deviceName ${message.removePrefix("장치 ").replace(Regex("실패한 업로드가 (\\d+)개 있습니다\\."), "전송 $1건이 실패했습니다.")}",
            detail = "장치 상태를 확인하세요.", kind = kind
        )
    }
