package com.example.jetsoncontroller.ui.sensors

import androidx.compose.runtime.Composable
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.RunQuality
import com.example.jetsoncontroller.ui.field.LocationQualityBand
import com.example.jetsoncontroller.ui.field.locationQualityBand
import com.example.jetsoncontroller.ui.field.validRoutePoint
import com.example.jetsoncontroller.ui.map.*
import java.util.Locale

/** Compatibility entry for GNSS_MAP callers; all rendering now lives in ui/map. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun GnssMapScreen(
    gnss: GnssSensorStatus,
    telemetryFresh: Boolean,
    deviceOnline: Boolean = true,
    route: List<com.example.jetsoncontroller.model.RoutePoint> = emptyList(),
    quality: RunQuality? = null,
    routeLabel: String? = null,
    returnLabel: String = "이전 화면으로",
    onBack: () -> Unit
) {
    GeoMapScreen(if (route.isEmpty()) GeoMapMode.LOCATION else GeoMapMode.ROUTE,
        GeoMapData(trail = route, quality = quality, device = mapDevice(gnss),
            deviceStale = !deviceOnline || !telemetryFresh, lastPositionAtMillis = gnss.lastSampleAtEpochMillis), onBack, gnss = gnss)
}

@Suppress("UNUSED_PARAMETER")
internal fun mapUsesSidePanel(smallestScreenWidthDp: Int, widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
    com.example.jetsoncontroller.ui.map.mapUsesSidePanel(widthDp, heightDp)

internal data class QualityRouteStroke(
    val points: List<com.example.jetsoncontroller.model.RoutePoint>,
    val band: LocationQualityBand
)

internal fun qualityRouteStrokes(route: List<com.example.jetsoncontroller.model.RoutePoint>): List<QualityRouteStroke> {
    val strokes = mutableListOf<QualityRouteStroke>()
    var current: MutableList<com.example.jetsoncontroller.model.RoutePoint>? = null
    var currentBand: LocationQualityBand? = null
    route.zipWithNext().forEach { (start, end) ->
        if (start.segment != end.segment || !validRoutePoint(start) || !validRoutePoint(end)) {
            current = null
            currentBand = null
            return@forEach
        }
        val band = worseLocationBand(locationQualityBand(start.fixState, start.sensorState), locationQualityBand(end.fixState, end.sensorState))
        if (band == LocationQualityBand.NONE) {
            current = null
            currentBand = null
            return@forEach
        }
        if (current == null || currentBand != band || current?.lastOrNull() != start) {
            current = mutableListOf(start, end).also { strokes += QualityRouteStroke(it, band) }
            currentBand = band
        } else current?.add(end)
    }
    return strokes
}

private fun worseLocationBand(first: LocationQualityBand, second: LocationQualityBand): LocationQualityBand {
    val order = listOf(LocationQualityBand.PRECISE, LocationQualityBand.APPROXIMATE,
        LocationQualityBand.LEGACY_OTHER, LocationQualityBand.UNKNOWN, LocationQualityBand.NONE)
    return if (order.indexOf(first) >= order.indexOf(second)) first else second
}

internal fun gnssCoordinateText(gnss: GnssSensorStatus, isCurrent: Boolean = true): String =
    if (gnss.hasValidLocation()) {
        (if (isCurrent) "" else "마지막 위치 · ") + String.format(Locale.US, "%.7f, %.7f", gnss.latitude, gnss.longitude)
    } else "유효한 위치 수신 대기 중"

internal fun mobileCoordinateText(fix: com.example.jetsoncontroller.data.location.MobileLocationFix?, isCurrent: Boolean = true): String =
    if (fix?.hasValidCoordinates() == true) {
        (if (isCurrent) "" else "마지막 위치 · ") + String.format(Locale.US, "%.7f, %.7f", fix.latitude, fix.longitude)
    } else "유효한 위치 수신 대기 중"
