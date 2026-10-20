package com.example.jetsoncontroller.data.zone

import com.example.jetsoncontroller.data.server.ServerTicket
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneValidationTest {
    @Test
    fun `old nullable ticket and null JSON have no geometry`() {
        assertEquals(ZoneGeometryState.NO_GEOMETRY, ZoneContract.fromTicket(ServerTicket("row", "구간", revision = "row-r1")).state)
        assertEquals(ZoneGeometryState.NO_GEOMETRY, ZoneContract.parse(null).state)
    }

    @Test
    fun `contract v1 sends features as an array and a numeric revision`() {
        val json = """{"schemaVersion":1,"features":[
            {"type":"Feature","properties":{"role":"ROUTE","label":"북측"},
             "geometry":{"type":"LineString","coordinates":[[127.0,37.0],[127.001,37.001]]}}],
            "range":{"bufferM":30,"polygon":$polygon,"bbox":[126,36,128,38],"lengthM":null,"areaM2":3400},
            "revision":7}"""
        val parsed = ZoneValidation.parse(json)
        assertEquals(ZoneGeometryState.VALID, parsed.state)
        val zone = requireNotNull(parsed.contract)
        assertEquals("7", zone.revision)
        assertEquals(60.0, zone.widthM!!, 0.0)
        assertNull(zone.lengthM)
        assertEquals(1, zone.features.size)
    }

    @Test
    fun `draft v1 range is parsed from nullable ticket fields and buffer is per side`() {
        val ticket = ServerTicket("row", "구간", revision = "row-r1", features = JsonParser.parseString(features),
            range = JsonParser.parseString("""{"bufferM":10,"polygon":$polygon,"bbox":[126,36,128,38],"lengthM":1200,"areaM2":350}"""),
            geometryRevision = "zone-r3", geometryHash = "server-hash")
        val parsed = ZoneContract.fromTicket(ticket)
        val zone = requireNotNull(parsed.contract)
        assertEquals(ZoneGeometryState.VALID, parsed.state)
        assertEquals(1, zone.schemaVersion)
        assertEquals(10.0, zone.bufferM!!, 0.0)
        assertEquals(20.0, zone.widthM!!, 0.0)
        assertEquals(1200.0, zone.lengthM!!, 0.0)
        assertEquals(350.0, zone.areaM2!!, 0.0)
        assertEquals(listOf(126.0, 36.0, 128.0, 38.0), zone.bbox)
        assertEquals("zone-r3", zone.revision)
        assertEquals("server-hash", zone.hash)
    }

    @Test
    fun `flat phase 2b fields and geometry envelope adapters retain revision`() {
        val flat = ZoneContract.fromTicket(ServerTicket("row", "구간", revision = "row-r1",
            features = JsonParser.parseString(features), range = JsonParser.parseString(polygon),
            bufferM = 4.0, bbox = listOf(126.0, 36.0, 128.0, 38.0), lengthM = 5.0, areaM2 = 6.0)).contract!!
        assertEquals("row-r1", flat.revision)
        assertEquals(8.0, flat.widthM!!, 0.0)
        for (inFeatures in listOf(true, false)) {
            val envelope = JsonParser.parseString(draft())
            val result = ZoneContract.fromTicket(ServerTicket("row", "구간", revision = "row-r1",
                features = envelope.takeIf { inFeatures }, range = envelope.takeUnless { inFeatures }))
            assertEquals("geometry-r1", result.contract?.revision)
            assertEquals(ZoneGeometryState.VALID, result.state)
        }
    }

    @Test
    fun `missing range keeps features without inventing a buffer`() {
        for (json in listOf(
            """{"schemaVersion":1,"features":$features,"revision":"r"}""",
            """{"schemaVersion":1,"features":$features,"range":null,"revision":"r"}""",
            """{"schemaVersion":1,"features":$features,"range":{"bufferM":10,"polygon":null},"revision":"r"}"""
        )) {
            val parsed = ZoneValidation.parse(json)
            assertEquals(ZoneGeometryState.RANGE_NOT_COMPUTED, parsed.state)
            assertNull(parsed.contract?.range)
            assertEquals("구역 범위가 아직 계산되지 않았습니다.", parsed.message)
        }
    }

    @Test
    fun `all supported feature types preserve map JSON including properties`() {
        val collection = """{"type":"FeatureCollection","features":[
            {"type":"Feature","id":"p","properties":{"name":"지점"},"geometry":{"type":"Point","coordinates":[127,37,10]}},
            {"type":"Feature","geometry":{"type":"LineString","coordinates":[[127,37],[128,38]]}},
            {"type":"Feature","geometry":$polygon},
            {"type":"Feature","geometry":{"type":"MultiPolygon","coordinates":[$rings,$rings]}}
        ]}"""
        val zone = ZoneValidation.parse(draft(featureCollection = collection)).contract!!
        assertEquals(ZoneGeometryType.entries.toList(), zone.features.map { it.geometry.type })
        assertEquals("지점", JsonParser.parseString(zone.featuresGeoJson).asJsonObject
            .getAsJsonArray("features")[0].asJsonObject.getAsJsonObject("properties").get("name").asString)
        assertEquals(ZonePosition(127.0, 37.0), zone.features.first().geometry.positions.single())
    }

    @Test
    fun `holes and MultiPolygon ranges validate regardless of winding`() {
        val hole = "[[126.5,36.5],[126.5,37.5],[127.5,37.5],[127.5,36.5],[126.5,36.5]]"
        val withHole = "[$outer,$hole]"
        val result = ZoneValidation.parse(draft("""{"type":"MultiPolygon","coordinates":[$withHole,$rings]}"""))
        assertEquals(ZoneGeometryState.VALID, result.state)
        assertEquals(2, result.contract?.range?.polygons?.size)
        assertEquals(2, result.contract?.range?.polygons?.first()?.rings?.size)
    }

    @Test
    fun `unclosed rings and short rings fail in ranges and original features`() {
        for ((ring, code) in listOf(
            "[[126,36],[128,36],[128,38],[126,38]]" to ZoneValidationCode.RING_NOT_CLOSED,
            "[[126,36],[128,36],[126,36]]" to ZoneValidationCode.RING_TOO_SHORT
        )) {
            val invalidPolygon = """{"type":"Polygon","coordinates":[$ring]}"""
            assertInvalid(draft(invalidPolygon), code)
            assertInvalid(draft(featureCollection = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":$invalidPolygon}]}"""), code)
        }
    }

    @Test
    fun `invalid hole ring is rejected too`() {
        assertInvalid(draft("""{"type":"Polygon","coordinates":[$outer,[[127,37],[127.1,37],[127,37]]]}"""), ZoneValidationCode.RING_TOO_SHORT)
    }

    @Test
    fun `swapped coordinates are reported without auto correction`() {
        assertInvalid(draft(featureCollection = pointFeatures("[37,127]")), ZoneValidationCode.SWAPPED_COORDINATES)
        assertInvalid(draft(featureCollection = pointFeatures("[37,-91]")), ZoneValidationCode.SWAPPED_COORDINATES)
    }

    @Test
    fun `coordinate limits are inclusive and invalid or nonnumeric values fail`() {
        for (point in listOf("[-180,-90]", "[180,90]")) {
            assertEquals(ZoneGeometryState.VALID, ZoneValidation.parse(draft(featureCollection = pointFeatures(point))).state)
        }
        for (point in listOf("[181,37]", "[-181,37]", "[1e400,37]", "[null,37]", "[\"127\",37]", "[127]")) {
            assertEquals(ZoneGeometryState.INVALID, ZoneValidation.parse(draft(featureCollection = pointFeatures(point))).state)
        }
    }

    @Test
    fun `empty or unsupported geometry and malformed contract fail closed`() {
        for (shape in listOf(
            """{"type":"Polygon","coordinates":[]}""",
            """{"type":"MultiPolygon","coordinates":[]}""",
            """{"type":"MultiPolygon","coordinates":[[]]}""",
            """{"type":"GeometryCollection","geometries":[]}""",
            """{"type":"Point","coordinates":[127,37]}"""
        )) assertEquals(ZoneGeometryState.INVALID, ZoneValidation.parse(draft(shape)).state)
        for (json in listOf("{", "[]", "{}", draft().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            draft(featureCollection = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":null}]}"""),
            draft(featureCollection = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[127,37]]}}]}"""))) {
            val result = ZoneValidation.parse(json)
            assertEquals(ZoneGeometryState.INVALID, result.state)
            assertNull(result.contract)
            assertTrue(result.issues.isNotEmpty())
            assertEquals("수집 구역 정보를 읽을 수 없습니다.", result.message)
        }
    }

    @Test
    fun `invalid measures and bounding boxes fail while legitimate zero survives`() {
        for (name in listOf("bufferM", "lengthM", "areaM2")) {
            assertEquals(ZoneGeometryState.INVALID, ZoneValidation.parse(draft().replace("\"$name\":10", "\"$name\":-1")).state)
            assertEquals(ZoneGeometryState.VALID, ZoneValidation.parse(draft().replace("\"$name\":10", "\"$name\":0")).state)
        }
        for (bbox in listOf("[128,36,126,38]", "[126,38,128,36]", "[126,36,181,38]", "[126,36,128,91]", "[126,36,128]")) {
            assertInvalid(draft().replace("[126,36,128,38]", bbox), ZoneValidationCode.INVALID_BBOX)
        }
    }

    @Test
    fun `phone trusts upstream topology rather than adding self intersection checks`() {
        val bowTie = """{"type":"Polygon","coordinates":[[[126,36],[128,38],[128,36],[126,38],[126,36]]]}"""
        assertEquals(ZoneGeometryState.VALID, ZoneValidation.parse(draft(bowTie)).state)
    }

    @Test
    fun `canonical geometry hash ignores object order numeric spelling and revision`() {
        val first = ZoneValidation.parse(draft()).contract!!
        val reordered = JsonParser.parseString(draft()).asJsonObject
        reordered.addProperty("revision", "geometry-r2")
        reordered.getAsJsonObject("range").addProperty("bufferM", 10.0)
        val second = ZoneValidation.parse(reordered).contract!!
        assertEquals(64, first.hash.length)
        assertEquals(first.hash, second.hash)
        reordered.getAsJsonObject("range").addProperty("bufferM", 11)
        assertNotEquals(first.hash, ZoneValidation.parse(reordered).contract!!.hash)
        assertEquals(first, ZoneValidation.parse(first.canonicalJson).contract)
    }

    @Test
    fun `geometry revision must be present as a number or nonblank text`() {
        for (replacement in listOf("null", "\"\"", "\" \"", "-1", "true")) {
            assertInvalid(draft().replace("\"geometry-r1\"", replacement), ZoneValidationCode.INVALID_TYPE)
        }
        assertInvalid(draft().replace(",\"revision\":\"geometry-r1\"", ""), ZoneValidationCode.INVALID_TYPE)
        // Contract v1 counts revisions as numbers; the draft's opaque text is still read as is.
        assertEquals("123", ZoneValidation.parse(draft().replace("\"geometry-r1\"", "123")).contract?.revision)
        assertEquals("opaque:revision/다음", ZoneValidation.parse(draft().replace("geometry-r1", "opaque:revision/다음")).contract?.revision)
    }

    private fun assertInvalid(json: String, code: ZoneValidationCode) {
        val result = ZoneValidation.parse(json)
        assertEquals(ZoneGeometryState.INVALID, result.state)
        assertEquals(code, result.issues.single().code)
    }

    private fun pointFeatures(coordinates: String) =
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":$coordinates}}]}"""

    private fun draft(shape: String = polygon, featureCollection: String = features) =
        """{"schemaVersion":1,"features":$featureCollection,"range":{"bufferM":10,"polygon":$shape,"bbox":[126,36,128,38],"lengthM":10,"areaM2":10},"revision":"geometry-r1"}"""

    private val features = """{"type":"FeatureCollection","features":[]}"""
    private val outer = "[[126,36],[128,36],[128,38],[126,38],[126,36]]"
    private val rings = "[$outer]"
    private val polygon = """{"type":"Polygon","coordinates":$rings}"""
}
