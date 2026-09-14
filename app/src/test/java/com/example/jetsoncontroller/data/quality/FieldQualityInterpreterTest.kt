package com.example.jetsoncontroller.data.quality

import com.example.jetsoncontroller.model.QualityProblemInterval
import com.example.jetsoncontroller.model.RunQuality
import com.example.jetsoncontroller.model.TaskRoute
import com.example.jetsoncontroller.model.TaskRunsResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldQualityInterpreterTest {
    @Test
    fun `legacy run and route payloads keep nullable quality semantics`() {
        val gson = Gson()
        val runs = gson.fromJson(
            """{"runs":[{"id":"capture/run.log","pipelineId":"capture","label":"Road","logId":"run.log","startedAt":"2026-09-14T00:00:00Z"}]}""",
            TaskRunsResponse::class.java
        )
        val route = gson.fromJson(
            """{"points":[{"latitude":37.0,"longitude":127.0,"timestamp":10}]}""",
            TaskRoute::class.java
        )

        assertNull(runs.runs.single().quality)
        assertNull(route.quality)
        assertNull(route.points.single().fixState)
        assertEquals(QualitySampleAvailability.NOT_RECORDED,
            FieldQualityInterpreter.rtkMeasurement(null).availability)
    }

    @Test
    fun `quality payload keeps measured and unknown RTK durations separate`() {
        val route = Gson().fromJson(
            """{
                "points":[{"latitude":37.0,"longitude":127.0,"timestamp":10,"fixState":"FIXED","sensorState":"ACTIVE"}],
                "quality":{
                    "schemaVersion":1,
                    "sampleState":"OBSERVED",
                    "observedDurationMillis":6000,
                    "unknownDurationMillis":4000,
                    "rtkObservedDurationMillis":4000,
                    "rtkUnknownDurationMillis":6000,
                    "rtkFixDurationMillis":2000,
                    "rtkFixRatio":0.5,
                    "problemIntervals":[],
                    "sensors":[]
                }
            }""".trimIndent(),
            TaskRoute::class.java
        )

        val measurement = FieldQualityInterpreter.rtkMeasurement(route.quality)
        assertEquals(4_000L, measurement.observedDurationMillis)
        assertEquals(6_000L, measurement.unknownDurationMillis)
        assertEquals(0.5, measurement.fixRatio!!, 0.0)
        assertEquals("FIXED", route.points.single().fixState)
    }

    @Test
    fun `no GNSS samples remain distinct from a measured zero FIX ratio`() {
        val noSamples = FieldQualityInterpreter.rtkMeasurement(
            RunQuality(sampleState = "NO_SAMPLES", rtkUnknownDurationMillis = 2_000)
        )
        val measuredZero = FieldQualityInterpreter.rtkMeasurement(
            RunQuality(
                sampleState = "OBSERVED",
                rtkObservedDurationMillis = 2_000,
                rtkUnknownDurationMillis = 0,
                rtkFixDurationMillis = 0,
                rtkFixRatio = 0.0
            )
        )

        assertEquals(QualitySampleAvailability.NO_SAMPLES, noSamples.availability)
        assertNull(noSamples.fixRatio)
        assertEquals(QualitySampleAvailability.OBSERVED, measuredZero.availability)
        assertEquals(0.0, measuredZero.fixRatio!!, 0.0)
    }

    @Test
    fun `unknown RTK category remains distinct from no sample`() {
        val measurement = FieldQualityInterpreter.rtkMeasurement(
            RunQuality(sampleState = "UNKNOWN", rtkUnknownDurationMillis = 2_000)
        )

        assertEquals(QualitySampleAvailability.UNKNOWN, measurement.availability)
        assertNull(measurement.fixRatio)
    }

    @Test
    fun `route problem indexes are validated without inventing a location`() {
        val valid = QualityProblemInterval(
            kind = "RTK_NOT_FIXED",
            startedAtEpochMillis = 0,
            endedAtEpochMillis = 2_000,
            startRoutePointIndex = 1,
            endRoutePointIndex = 2
        )
        val unlocated = valid.copy(startRoutePointIndex = null, endRoutePointIndex = null)
        val outside = valid.copy(startRoutePointIndex = 4, endRoutePointIndex = 5)

        val result = FieldQualityInterpreter.locatedRouteProblems(
            RunQuality(problemIntervals = listOf(valid, unlocated, outside)),
            pointCount = 3
        )

        assertEquals(listOf(valid), result)
        assertTrue(FieldQualityInterpreter.locatedRouteProblems(null, 3).isEmpty())
    }
}
