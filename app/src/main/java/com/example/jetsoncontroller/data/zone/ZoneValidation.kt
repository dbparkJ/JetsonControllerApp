package com.example.jetsoncontroller.data.zone

import com.example.jetsoncontroller.data.server.ServerTicket
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest

enum class ZoneGeometryState { NO_GEOMETRY, RANGE_NOT_COMPUTED, VALID, INVALID }

enum class ZoneValidationCode {
    MALFORMED_JSON, UNSUPPORTED_VERSION, INVALID_TYPE, INVALID_COORDINATE,
    SWAPPED_COORDINATES, RING_TOO_SHORT, RING_NOT_CLOSED, INVALID_MEASURE, INVALID_BBOX
}

data class ZoneValidationIssue(val code: ZoneValidationCode, val path: String)

data class ZoneValidationResult(
    val state: ZoneGeometryState,
    val contract: ZoneContract? = null,
    val issues: List<ZoneValidationIssue> = emptyList()
) {
    val message: String? get() = when (state) {
        ZoneGeometryState.INVALID -> "수집 구역 정보를 읽을 수 없습니다."
        ZoneGeometryState.RANGE_NOT_COMPUTED -> "구역 범위가 아직 계산되지 않았습니다."
        else -> null
    }
}

object ZoneValidation {
    /** Accepts both 2b-B's flat fields and the draft's nested range object. */
    fun fromTicket(ticket: ServerTicket): ZoneValidationResult {
        ticket.geometry.present()?.let { return ZoneContract.parse(it) }
        val features = ticket.features.present()
        val range = ticket.range.present()
        if (features == null && range == null && ticket.bufferM == null && ticket.bbox == null &&
            ticket.lengthM == null && ticket.areaM2 == null && ticket.geometryRevision == null &&
            ticket.geometryHash == null
        ) return ZoneValidationResult(ZoneGeometryState.NO_GEOMETRY)

        val envelope = listOfNotNull(features, range).firstOrNull {
            it.isJsonObject && it.asJsonObject.has("schemaVersion") && it.asJsonObject.has("features")
        }?.asJsonObject?.deepCopy()
        val root = envelope ?: JsonObject().apply {
            addProperty("schemaVersion", 1)
            add("features", features ?: JsonParser.parseString("""{"type":"FeatureCollection","features":[]}"""))
            val nested = if (range?.isJsonObject == true && !range.asJsonObject.has("type")) {
                range.asJsonObject.deepCopy()
            } else JsonObject().apply { add("polygon", range ?: JsonNull.INSTANCE) }
            if (!nested.has("bufferM")) ticket.bufferM?.let { nested.addProperty("bufferM", it) }
            if (!nested.has("bbox")) ticket.bbox?.let { values ->
                nested.add("bbox", JsonArray().apply { values.forEach { add(it) } })
            }
            if (!nested.has("lengthM")) ticket.lengthM?.let { nested.addProperty("lengthM", it) }
            if (!nested.has("areaM2")) ticket.areaM2?.let { nested.addProperty("areaM2", it) }
            add("range", nested)
        }
        if (!root.has("revision")) (ticket.geometryRevision ?: ticket.revision)?.let {
            root.addProperty("revision", it)
        }
        return parse(root, ticket.geometryHash)
    }

    fun parse(json: String): ZoneValidationResult = try {
        parse(JsonParser.parseString(json))
    } catch (_: RuntimeException) {
        invalid(ZoneValidationCode.MALFORMED_JSON, "$")
    }

    fun parse(json: JsonElement?): ZoneValidationResult = parse(json, null)

    private fun parse(json: JsonElement?, suppliedHash: String?): ZoneValidationResult {
        if (json.present() == null) return ZoneValidationResult(ZoneGeometryState.NO_GEOMETRY)
        return try {
            val root = json!!.obj("$")
            val version = root.get("schemaVersion").number("schemaVersion")
            checkValue(version == 1.0, ZoneValidationCode.UNSUPPORTED_VERSION, "schemaVersion")
            val collection = root.get("features").obj("features")
            checkValue(collection.get("type")?.asString == "FeatureCollection", ZoneValidationCode.INVALID_TYPE, "features.type")
            val features = collection.get("features").array("features.features").mapIndexed { index, element ->
                val path = "features.features[$index]"
                val feature = element.obj(path)
                checkValue(feature.get("type")?.asString == "Feature", ZoneValidationCode.INVALID_TYPE, "$path.type")
                ZoneFeature(geometry(feature.get("geometry"), "$path.geometry"))
            }
            val rangeObject = root.get("range").present()?.obj("range")
            val polygonJson = rangeObject?.get("polygon").present()
            val range = polygonJson?.let {
                val geometry = geometry(it, "range.polygon")
                checkValue(geometry.type in setOf(ZoneGeometryType.POLYGON, ZoneGeometryType.MULTI_POLYGON),
                    ZoneValidationCode.INVALID_TYPE, "range.polygon.type")
                ZoneRange(geometry.polygons)
            }
            val buffer = measure(rangeObject, "bufferM")
            val length = measure(rangeObject, "lengthM")
            val area = measure(rangeObject, "areaM2")
            val bbox = rangeObject?.get("bbox").present()?.let { value ->
                val values = value.array("range.bbox").mapIndexed { index, item -> item.number("range.bbox[$index]") }
                checkValue(values.size == 4 && values[0] in -180.0..180.0 && values[2] in -180.0..180.0 &&
                    values[1] in -90.0..90.0 && values[3] in -90.0..90.0 &&
                    values[0] <= values[2] && values[1] <= values[3], ZoneValidationCode.INVALID_BBOX, "range.bbox")
                values
            }
            val revisionValue = root.get("revision").present()
            checkValue(revisionValue?.isJsonPrimitive == true && revisionValue.asJsonPrimitive.isString &&
                revisionValue.asString.isNotBlank(), ZoneValidationCode.INVALID_TYPE, "revision")
            val revision = revisionValue!!.asString
            val canonical = canonicalJson(root)
            val geometryJson = root.deepCopy().apply { remove("revision"); remove("hash") }
            val hash = suppliedHash?.takeIf { it.isNotBlank() } ?: MessageDigest.getInstance("SHA-256")
                .digest(canonicalJson(geometryJson).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            ZoneValidationResult(
                if (range == null) ZoneGeometryState.RANGE_NOT_COMPUTED else ZoneGeometryState.VALID,
                ZoneContract(1, features, range, buffer, bbox, length, area, revision, hash,
                    canonical, canonicalJson(collection), polygonJson?.let(::canonicalJson))
            )
        } catch (failure: ValidationFailure) {
            ZoneValidationResult(ZoneGeometryState.INVALID, issues = listOf(failure.issue))
        } catch (_: RuntimeException) {
            invalid(ZoneValidationCode.MALFORMED_JSON, "$")
        }
    }

    private fun geometry(element: JsonElement?, path: String): ZoneGeometry {
        val obj = element.obj(path)
        val coordinates = obj.get("coordinates")
        return when (obj.get("type")?.asString) {
            "Point" -> ZoneGeometry(ZoneGeometryType.POINT, listOf(position(coordinates, "$path.coordinates")))
            "LineString" -> {
                val points = coordinates.array("$path.coordinates").mapIndexed { index, point ->
                    position(point, "$path.coordinates[$index]")
                }
                checkValue(points.size >= 2, ZoneValidationCode.INVALID_COORDINATE, "$path.coordinates")
                ZoneGeometry(ZoneGeometryType.LINE_STRING, points)
            }
            "Polygon" -> ZoneGeometry(ZoneGeometryType.POLYGON, polygons = listOf(polygon(coordinates, "$path.coordinates")))
            "MultiPolygon" -> {
                val polygons = coordinates.array("$path.coordinates").mapIndexed { index, rings ->
                    polygon(rings, "$path.coordinates[$index]")
                }
                checkValue(polygons.isNotEmpty(), ZoneValidationCode.INVALID_COORDINATE, "$path.coordinates")
                ZoneGeometry(ZoneGeometryType.MULTI_POLYGON, polygons = polygons)
            }
            else -> throw ValidationFailure(ZoneValidationIssue(ZoneValidationCode.INVALID_TYPE, "$path.type"))
        }
    }

    private fun polygon(element: JsonElement?, path: String): ZonePolygon {
        val rings = element.array(path).mapIndexed { index, ring ->
            val ringPath = "$path[$index]"
            val positions = ring.array(ringPath).mapIndexed { pointIndex, point -> position(point, "$ringPath[$pointIndex]") }
            checkValue(positions.size >= 4, ZoneValidationCode.RING_TOO_SHORT, ringPath)
            checkValue(positions.first() == positions.last(), ZoneValidationCode.RING_NOT_CLOSED, ringPath)
            positions
        }
        checkValue(rings.isNotEmpty(), ZoneValidationCode.INVALID_COORDINATE, path)
        return ZonePolygon(rings)
    }

    private fun position(element: JsonElement?, path: String): ZonePosition {
        val values = element.array(path)
        checkValue(values.size() >= 2, ZoneValidationCode.INVALID_COORDINATE, path)
        val lon = values[0].number("$path[0]")
        val lat = values[1].number("$path[1]")
        checkValue(lat in -90.0..90.0, ZoneValidationCode.SWAPPED_COORDINATES, path)
        checkValue(lon in -180.0..180.0, ZoneValidationCode.INVALID_COORDINATE, path)
        return ZonePosition(lon, lat)
    }

    private fun measure(obj: JsonObject?, name: String): Double? = obj?.get(name).present()?.let {
        val value = it.number("range.$name")
        checkValue(value >= 0 && (name != "bufferM" || (value * 2).isFinite()),
            ZoneValidationCode.INVALID_MEASURE, "range.$name")
        value
    }

    private fun JsonElement?.obj(path: String): JsonObject {
        checkValue(this?.isJsonObject == true, ZoneValidationCode.INVALID_TYPE, path)
        return this!!.asJsonObject
    }

    private fun JsonElement?.array(path: String): JsonArray {
        checkValue(this?.isJsonArray == true, ZoneValidationCode.INVALID_TYPE, path)
        return this!!.asJsonArray
    }

    private fun JsonElement?.number(path: String): Double {
        checkValue(this?.isJsonPrimitive == true && this.asJsonPrimitive.isNumber,
            ZoneValidationCode.INVALID_COORDINATE, path)
        return this!!.asDouble.also { checkValue(it.isFinite(), ZoneValidationCode.INVALID_COORDINATE, path) }
    }

    private fun checkValue(valid: Boolean, code: ZoneValidationCode, path: String) {
        if (!valid) throw ValidationFailure(ZoneValidationIssue(code, path))
    }

    private fun invalid(code: ZoneValidationCode, path: String) =
        ZoneValidationResult(ZoneGeometryState.INVALID, issues = listOf(ZoneValidationIssue(code, path)))

    private class ValidationFailure(val issue: ZoneValidationIssue) : RuntimeException()
}

private fun JsonElement?.present(): JsonElement? = this?.takeUnless { it.isJsonNull }

private fun canonicalJson(element: JsonElement): String = when {
    element.isJsonObject -> element.asJsonObject.entrySet().sortedBy { it.key }.joinToString(",", "{", "}") {
        "${com.google.gson.JsonPrimitive(it.key)}:${canonicalJson(it.value)}"
    }
    element.isJsonArray -> element.asJsonArray.joinToString(",", "[", "]", transform = ::canonicalJson)
    element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> element.asBigDecimal.stripTrailingZeros().toPlainString()
    else -> element.toString()
}
