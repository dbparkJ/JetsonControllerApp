package com.example.jetsoncontroller.ui.map

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.RoutePoint
import com.example.jetsoncontroller.model.RunQuality
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.model.Formats
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.*

enum class GeoMapMode(val title: String, val parameter: String) {
    ZONE("수집 구역", "zone"), RUN("수집 중", "run"), ROUTE("수집 경로", "route"), LOCATION("장치 위치", "location");
    companion object {
        fun fromParameter(value: String?): GeoMapMode? = entries.firstOrNull { it.parameter == value }
    }
}

enum class GeoZoneState { INSIDE, OUTSIDE, NEAR, UNKNOWN, CHECKING }

data class GeoMapPosition(val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class GeoMapGap(val startedAtMillis: Long, val endedAtMillis: Long?, val before: GeoMapPosition? = null, val after: GeoMapPosition? = null)

/** Adapter seam for 6-C. No range means no zone judgement, even if an observation is supplied. */
interface GeoMapZoneOutput {
    val state: GeoZoneState
    val distanceM: Double?
    val gaps: List<GeoMapGap>
    val outsideCount: Int?
    val outsideMillis: Long?
    val unobservedMillis: Long?
    val phoneObserved: Boolean
}

data class GeoMapZoneSnapshot(
    override val state: GeoZoneState = GeoZoneState.UNKNOWN,
    override val distanceM: Double? = null,
    override val gaps: List<GeoMapGap> = emptyList(),
    override val outsideCount: Int? = null,
    override val outsideMillis: Long? = null,
    override val unobservedMillis: Long? = null,
    override val phoneObserved: Boolean = true
) : GeoMapZoneOutput

enum class GeoGeometryState { ABSENT, READY, RANGE_PENDING, INVALID }

data class GeoMapGeometry(
    val revision: String?, val hash: String?, val state: GeoGeometryState,
    val sourceJson: String = emptyMapCollection(),
    val positions: List<GeoMapPosition> = emptyList(),
    val lengthM: Double? = null, val bufferM: Double? = null,
    val areaCount: Int = 0, val pointCount: Int = 0,
    val invalidReason: String? = null,
    val contractJson: String? = null,
    val ticketRowId: String? = null
) {
    val hasRange: Boolean get() = state == GeoGeometryState.READY
    val summary: String get() = buildList {
        lengthM?.takeIf { it.isFinite() && it >= 0 }?.let { add("경로 ${Formats.distance(it)}") }
        bufferM?.takeIf { it.isFinite() && it >= 0 }?.let { add("폭 ${Formats.distance(2 * it)}") }
        if (areaCount > 0) add("영역 ${areaCount}곳")
        if (pointCount > 0) add("지점 ${pointCount}곳")
    }.joinToString(" · ")
}

/** Validation here is limited to safe display; the monitor owns spatial classification. */
fun mapGeometry(ticket: ServerTicket): GeoMapGeometry {
    val validation = com.example.jetsoncontroller.data.zone.ZoneContract.fromTicket(ticket)
    val contract = validation.contract
    if (ticket.geometry != null && !ticket.geometry.isJsonNull && contract == null) {
        return GeoMapGeometry(null, null, GeoGeometryState.INVALID, invalidReason = validation.issues.joinToString { "${it.code}: ${it.path}" })
    }
    val resolved = if (contract == null) ticket else ticket.copy(geometry = null, assignees = ticket.assignees.orEmpty(),
        features = com.google.gson.JsonParser.parseString(contract.featuresGeoJson),
        range = contract.rangeGeoJson?.let(com.google.gson.JsonParser::parseString),
        bufferM = contract.bufferM, lengthM = contract.lengthM, areaM2 = contract.areaM2,
        geometryRevision = contract.revision, geometryHash = contract.hash)
    return displayGeometry(resolved).copy(contractJson = contract?.canonicalJson, ticketRowId = ticket.rowId)
}

private fun displayGeometry(ticket: ServerTicket): GeoMapGeometry {
    if (ticket.features == null && ticket.range == null) return GeoMapGeometry(ticket.geometryRevision, ticket.geometryHash, GeoGeometryState.ABSENT)
    return runCatching {
        val features = mutableListOf<JsonObject>()
        val positions = mutableListOf<GeoMapPosition>()
        var areas = 0
        var points = 0
        fun geometry(value: JsonObject, range: Boolean) {
            val type = value.get("type")?.asString
            val coordinates = value.getAsJsonArray("coordinates") ?: error("좌표가 없습니다.")
            fun position(value: JsonElement): GeoMapPosition {
                val pair = value.asJsonArray
                require(pair.size() >= 2) { "좌표 쌍을 확인하세요." }
                val p = GeoMapPosition(pair[1].asDouble, pair[0].asDouble)
                require(p.valid) { "경도와 위도 순서 또는 범위를 확인하세요." }
                positions += p
                return p
            }
            fun line(value: JsonArray, ring: Boolean) {
                val p = value.map(::position)
                require(p.size >= if (ring) 4 else 2) { "좌표가 부족합니다." }
                require(!ring || p.first() == p.last()) { "닫히지 않은 고리입니다." }
            }
            fun polygon(value: JsonArray) {
                require(value.size() > 0) { "영역의 고리가 없습니다." }
                value.forEach { line(it.asJsonArray, true) }
            }
            val kind = when (type) {
                "Point" -> { require(!range); position(coordinates); points++; "point" }
                "LineString" -> { require(!range); line(coordinates, false); "route" }
                "Polygon" -> { polygon(coordinates); if (!range) areas++; if (range) "range" else "area" }
                "MultiPolygon" -> {
                    require(coordinates.size() > 0)
                    coordinates.forEach { polygon(it.asJsonArray) }
                    if (!range) areas += coordinates.size()
                    if (range) "range" else "area"
                }
                else -> error("지원하지 않는 도형입니다.")
            }
            features += mapFeature(value, kind)
        }
        ticket.features?.let {
            val collection = it.asJsonObject
            require(collection.get("type")?.asString == "FeatureCollection")
            collection.getAsJsonArray("features").forEach { feature ->
                val f = feature.asJsonObject
                require(f.get("type")?.asString == "Feature")
                geometry(f.getAsJsonObject("geometry"), false)
            }
        }
        ticket.range?.let {
            val r = it.asJsonObject
            geometry(if (r.get("type")?.asString == "Feature") r.getAsJsonObject("geometry") else r, true)
        }
        if (features.isEmpty()) return GeoMapGeometry(ticket.geometryRevision, ticket.geometryHash, GeoGeometryState.ABSENT)
        GeoMapGeometry(ticket.geometryRevision, ticket.geometryHash,
            if (ticket.range == null) GeoGeometryState.RANGE_PENDING else GeoGeometryState.READY,
            mapCollection(features), positions.toList(), ticket.lengthM, ticket.bufferM, areas, points)
    }.getOrElse {
        GeoMapGeometry(ticket.geometryRevision, ticket.geometryHash, GeoGeometryState.INVALID,
            invalidReason = it.message ?: "도형 형식을 확인하세요.")
    }
}

fun boundMapGeometry(geometry: GeoMapGeometry?, revision: String?, hash: String?): GeoMapGeometry? =
    geometry?.takeIf { !revision.isNullOrBlank() && !hash.isNullOrBlank() && it.revision == revision && it.hash == hash }

data class GeoMapData(
    val geometry: GeoMapGeometry? = null,
    val trail: List<RoutePoint> = emptyList(),
    val device: GeoMapPosition? = null,
    val deviceStale: Boolean = false,
    val uncertaintyM: Double? = null,
    val phone: GeoMapPosition? = null,
    val phoneAccuracyM: Double? = null,
    val zone: GeoMapZoneOutput = GeoMapZoneSnapshot(),
    val quality: RunQuality? = null,
    val geometryChanged: Boolean = false,
    val lastPositionAtMillis: Long? = null
)

fun mapDevice(gnss: GnssSensorStatus): GeoMapPosition? =
    if (gnss.fixType.uppercase(java.util.Locale.ROOT) in setOf("NONE", "NO_FIX")) null
    else if (gnss.latitude != null && gnss.longitude != null) GeoMapPosition(gnss.latitude, gnss.longitude).takeIf { it.valid } else null

internal fun mapCardHeight(mode: GeoMapMode, compactHeight: Boolean): Int = if (compactHeight) 160 else if (mode == GeoMapMode.RUN) 240 else 200
internal fun mapUsesSidePanel(width: Float, height: Float): Boolean = height < 480 && width > height && width >= 600
internal fun mapCardActive(visible: Boolean, resumed: Boolean, fullMapOpen: Boolean): Boolean = visible && resumed && !fullMapOpen

internal fun mapDeviceStale(gnss: GnssSensorStatus, online: Boolean, telemetryFresh: Boolean, observedAtMillis: Long?): Boolean {
    val sample = gnss.lastSampleAtEpochMillis ?: return true
    return !online || !telemetryFresh || observedAtMillis == null || observedAtMillis - sample !in 0L..10_000L || !(gnss.active || gnss.connected)
}

internal fun zoneBadge(mode: GeoMapMode, data: GeoMapData): Pair<String, Tone>? {
    if (data.geometry?.hasRange != true || mode != GeoMapMode.RUN) return null
    return when (if (data.deviceStale) GeoZoneState.UNKNOWN else data.zone.state) {
        GeoZoneState.INSIDE -> "구역 안" to Tone.SUCCESS
        GeoZoneState.NEAR -> "경계 근처" to Tone.INFO
        GeoZoneState.OUTSIDE -> if (mode == GeoMapMode.RUN) ("구역 밖" + (data.zone.distanceM?.let { " · ${Formats.distance(it)}" } ?: "")) to Tone.WARNING else null
        GeoZoneState.CHECKING -> if (mode == GeoMapMode.RUN) "위치 확인 중" to Tone.UNKNOWN else null
        GeoZoneState.UNKNOWN -> if (mode == GeoMapMode.RUN) "위치 미확인" to Tone.UNKNOWN else null
    }
}

internal fun mapPositionSentence(data: GeoMapData): String = when {
    data.geometry?.state == GeoGeometryState.INVALID -> "수집 구역 정보를 읽을 수 없습니다."
    data.geometry?.state == GeoGeometryState.RANGE_PENDING -> "구역 범위가 아직 계산되지 않았습니다."
    data.device == null || data.deviceStale -> "장치 위치를 확인할 수 없습니다."
    data.geometry?.hasRange != true -> "장치 위치를 확인할 수 없습니다."
    data.zone.state == GeoZoneState.INSIDE -> "장치가 구역 안에 있습니다."
    data.zone.state == GeoZoneState.NEAR -> "장치가 구역 경계 근처에 있습니다."
    data.zone.state == GeoZoneState.OUTSIDE && data.zone.distanceM != null -> "장치가 구역에서 ${Formats.distance(data.zone.distanceM)} 떨어져 있습니다."
    else -> "장치 위치를 확인할 수 없습니다."
}

internal fun mapResultCaption(zone: GeoMapZoneOutput): String = buildList {
    zone.outsideCount?.let { add("구역 밖 기록 ${it}회") }
    zone.outsideMillis?.let { add(mapObservationDuration(it)) }
    zone.unobservedMillis?.let { add("관찰하지 못한 시간 ${mapObservationDuration(it)}") }
}.joinToString(" · ")

internal fun initialMapPositions(data: GeoMapData): List<GeoMapPosition> {
    val zone = data.geometry?.positions.orEmpty()
    val trail = data.trail.map { GeoMapPosition(it.latitude, it.longitude) }.filter { it.valid }
    val points = zone.ifEmpty { trail }
    val device = data.device?.takeIf { it.valid }
    if (points.isEmpty()) return listOfNotNull(device)
    val center = GeoMapPosition((points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2,
        (points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2)
    return if (device != null && mapDistance(center, device) <= 2_000) points + device else points
}

internal fun mapDistance(a: GeoMapPosition, b: GeoMapPosition): Double {
    val lat = Math.toRadians((a.latitude + b.latitude) / 2)
    return hypot(Math.toRadians(a.latitude - b.latitude), Math.toRadians(a.longitude - b.longitude) * cos(lat)) * 6_371_000
}

internal fun emptyMapCollection(): String = "{\"type\":\"FeatureCollection\",\"features\":[]}"
internal fun mapCollection(features: List<JsonObject>): String = JsonObject().apply {
    addProperty("type", "FeatureCollection")
    add("features", JsonArray().apply { features.forEach { add(it) } })
}.toString()

internal fun mapFeature(geometry: JsonObject, kind: String): JsonObject = JsonObject().apply {
    addProperty("type", "Feature")
    add("geometry", geometry)
    add("properties", JsonObject().apply { addProperty("kind", kind) })
}

internal fun mapPoint(position: GeoMapPosition, kind: String): JsonObject = mapFeature(JsonObject().apply {
    addProperty("type", "Point")
    add("coordinates", JsonArray().apply { add(position.longitude); add(position.latitude) })
}, kind)
