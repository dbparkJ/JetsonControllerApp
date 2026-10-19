package com.example.jetsoncontroller.data.zone

import com.example.jetsoncontroller.data.server.ServerTicket
import com.google.gson.JsonElement

data class ZonePosition(val longitude: Double, val latitude: Double)

data class ZonePolygon(val rings: List<List<ZonePosition>>)

data class ZoneRange(val polygons: List<ZonePolygon>)

enum class ZoneGeometryType { POINT, LINE_STRING, POLYGON, MULTI_POLYGON }

data class ZoneGeometry(
    val type: ZoneGeometryType,
    val positions: List<ZonePosition> = emptyList(),
    val polygons: List<ZonePolygon> = emptyList()
)

data class ZoneFeature(val geometry: ZoneGeometry)

/** Parsed v1 geometry. The JSON retains feature ids/properties for map rendering and run binding. */
data class ZoneContract(
    val schemaVersion: Int,
    val features: List<ZoneFeature>,
    val range: ZoneRange?,
    val bufferM: Double?,
    val bbox: List<Double>?,
    val lengthM: Double?,
    val areaM2: Double?,
    val revision: String,
    val hash: String,
    val canonicalJson: String,
    val featuresGeoJson: String,
    val rangeGeoJson: String?
) {
    val widthM: Double? get() = bufferM?.times(2)

    companion object {
        fun fromTicket(ticket: ServerTicket): ZoneValidationResult = ZoneValidation.fromTicket(ticket)

        fun parse(json: JsonElement?): ZoneValidationResult = ZoneValidation.parse(json)
    }
}
