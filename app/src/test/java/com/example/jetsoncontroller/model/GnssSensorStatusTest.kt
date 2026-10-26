package com.example.jetsoncontroller.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GnssSensorStatusTest {
    private val gson = Gson()

    private fun parse(fields: String): GnssSensorStatus =
        gson.fromJson("""{"gnssSensor":{$fields}}""", JetsonStatus::class.java).gnssSensor

    @Test
    fun `current diagnostics parse and round trip alongside legacy GNSS fields`() {
        for (selection in listOf("client", "caster")) {
            val gnss = parse("""
                "fixType":"rtk_fixed","latitude":37.5,"ntripSelection":"$selection",
                "ntripError":"  재연결 중\n ","rtcmLastAtEpochMillis":1700000000000,
                "rtcmMessageTypes":[1077,1005,1004,1005],"rtcmBaseStationId":123,
                "rtcmSignals":["GPS 5Q","GAL 7Q","GPS 1C","GPS 5Q","SBS ?12"],
                "rtcmBaseDistanceM":7400.5,"ntripLastGgaSentAtEpochMillis":1699999999000
            """.trimIndent())
            assertEquals(selection, gnss.ntripSelection)
            assertEquals("재연결 중", gnss.ntripError)
            assertEquals(1_700_000_000_000L, gnss.rtcmLastAtEpochMillis)
            assertEquals(listOf(1004, 1005, 1077), gnss.rtcmMessageTypes)
            assertEquals(listOf("GAL 7Q", "GPS 1C", "GPS 5Q", "SBS ?12"), gnss.rtcmSignals)
            assertEquals(123, gnss.rtcmBaseStationId)
            assertEquals(7400.5, gnss.rtcmBaseDistanceM!!, 0.0)
            assertEquals(1_699_999_999_000L, gnss.ntripLastGgaSentAtEpochMillis)
            assertEquals("rtk_fixed", gnss.fixType)
            assertEquals(37.5, gnss.latitude!!, 0.0)
            assertEquals(gnss, gson.fromJson(gson.toJson(gnss), GnssSensorStatus::class.java))
        }
    }

    @Test
    fun `old releases and explicit null diagnostics retain safe defaults`() {
        val fields = listOf("ntripSelection", "ntripError", "rtcmLastAtEpochMillis", "rtcmMessageTypes", "rtcmSignals",
            "rtcmBaseStationId", "rtcmBaseDistanceM", "ntripLastGgaSentAtEpochMillis")
        for (gnss in listOf(parse(""), parse(fields.joinToString(",") { "\"$it\":null" }),
            gson.fromJson("{}", JetsonStatus::class.java).gnssSensor)) {
            assertNull(gnss.ntripSelection)
            assertNull(gnss.ntripError)
            assertNull(gnss.rtcmLastAtEpochMillis)
            assertEquals(emptyList<Int>(), gnss.rtcmMessageTypes)
            assertEquals(emptyList<String>(), gnss.rtcmSignals)
            assertNull(gnss.rtcmBaseStationId)
            assertNull(gnss.rtcmBaseDistanceM)
            assertNull(gnss.ntripLastGgaSentAtEpochMillis)
            assertFalse(gnss.ntripConnected)
        }
    }

    @Test
    fun `malformed diagnostics never break status parsing or coerce strings booleans and fractions`() {
        for (value in listOf("true", "{}", "[]", "\"123\"", "123.5", "1e100")) {
            val gnss = parse("""
                "ntripSelection":$value,"ntripError":$value,
                "rtcmLastAtEpochMillis":$value,"rtcmMessageTypes":$value,
                "rtcmBaseStationId":$value,"ntripLastGgaSentAtEpochMillis":$value
            """.trimIndent())
            assertNull(gnss.ntripSelection)
            if (value != "\"123\"") assertNull(gnss.ntripError)
            assertNull(gnss.rtcmLastAtEpochMillis)
            assertNull(gnss.rtcmBaseStationId)
            assertNull(gnss.ntripLastGgaSentAtEpochMillis)
        }
        for (value in listOf("null", "true", "{}", "\"1005\"", "1005")) {
            assertEquals(emptyList<Int>(), parse("\"rtcmMessageTypes\":$value").rtcmMessageTypes)
        }
        for (value in listOf("true", "{}", "[]", "\"7400\"", "-1", "1e999", "\"NaN\"")) {
            assertNull(parse("\"rtcmBaseDistanceM\":$value").rtcmBaseDistanceM)
        }
        assertNull(parse("\"ntripSelection\":\"auto\"").ntripSelection)
        assertNull(parse("\"ntripSelection\":\" caster \"").ntripSelection)
        for (value in listOf(-1, 4096)) assertNull(parse("\"rtcmBaseStationId\":$value").rtcmBaseStationId)
    }

    @Test
    fun `RTCM list and scalar limits match bridge contract`() {
        assertEquals(listOf(1, 1005, 4095),
            parse(""""rtcmMessageTypes":[4095,1,1005,1005,0,4096,-1,true,"1077",1077.5,null,{}]""").rtcmMessageTypes)
        val many = (4095 downTo 1).joinToString(",")
        assertEquals((1..32).toList(), parse("\"rtcmMessageTypes\":[$many]").rtcmMessageTypes)
        for (id in listOf(0, 4095)) assertEquals(id, parse("\"rtcmBaseStationId\":$id").rtcmBaseStationId)
        assertEquals(0.0, parse(""""rtcmBaseDistanceM":0""").rtcmBaseDistanceM!!, 0.0)
        assertEquals(256, parse("\"ntripError\":\"${"x".repeat(300)}\"").ntripError!!.length)
    }

    @Test
    fun `RTCM signals accept all constellations valid bands and unknown signal IDs`() {
        val signals = listOf("GPS 1C", "GPS 5Q", "GLO 2P", "GAL 7Q", "BDS 5P", "QZS 5X",
            "SBS ?12", "IRN 9A", "SBS ?0", "SBS ?00", "SBS ?99")
        val gnss = parse("\"rtcmSignals\":${gson.toJson(signals.reversed() + signals.take(2))}")
        assertEquals(signals.sorted(), gnss.rtcmSignals)
        assertEquals(gnss, gson.fromJson(gson.toJson(gnss), GnssSensorStatus::class.java))
    }

    @Test
    fun `RTCM signals reject non lists and malformed entries without coercion`() {
        for (value in listOf("null", "true", "{}", "5", "\"GPS 5Q\"", "[]")) {
            assertEquals(emptyList<String>(), parse("\"rtcmSignals\":$value").rtcmSignals)
        }
        val invalid = listOf(null, true, 5, 5.0, emptyMap<String, String>(), emptyList<String>(),
            "", "gps 1C", "XYZ 1C", "GPS 0C", "GPS 1c", "GPS 10C", "GPS 1CC", "GPS １C",
            "GPS  1C", "GPS\t1C", " GPS 1C", "GPS 1C ", "GPS 1C\n", "GPS ?", "GPS ?100",
            "GPS ?-1", "GPS ?１２")
        assertEquals(listOf("GPS 1C", "SBS ?12"),
            parse("\"rtcmSignals\":${gson.toJson(invalid + listOf("GPS 1C", "SBS ?12"))}").rtcmSignals)
    }

    @Test
    fun `RTCM signals cap sorted distinct entries at 48`() {
        val signals = (1..9).flatMap { band -> ('A'..'Z').map { code -> "GPS $band$code" } }
        assertEquals(signals.sorted().take(48),
            parse("\"rtcmSignals\":${gson.toJson(signals.reversed() + signals.reversed())}").rtcmSignals)
    }
}
