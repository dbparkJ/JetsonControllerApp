package com.example.jetsoncontroller.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunTelemetryModelTest {
    private val gson = Gson()

    @Test
    fun `zero collected bytes remains observed instead of becoming unknown`() {
        val telemetry = gson.fromJson(
            """{
                "schemaVersion":1,
                "runId":"capture/run-1",
                "outputId":"output-1",
                "sourceRevision":"abc",
                "observedAtEpochMillis":10000,
                "bytesObservedAtEpochMillis":9000,
                "durationMillis":6000,
                "collectedBytes":0,
                "collectedFileCount":0,
                "collectionBytesState":"OBSERVED"
            }""".trimIndent(),
            RunTelemetry::class.java
        )

        assertEquals(0L, telemetry.collectedBytes)
        assertEquals(0, telemetry.collectedFileCount)
        assertEquals("OBSERVED", telemetry.collectionBytesState)

        val unavailable = gson.fromJson(
            """{
                "schemaVersion":1,
                "runId":"capture/run-1",
                "outputId":"output-1",
                "sourceRevision":"abc",
                "observedAtEpochMillis":10000,
                "bytesObservedAtEpochMillis":10000,
                "collectionBytesState":"UNAVAILABLE"
            }""".trimIndent(),
            RunTelemetry::class.java
        )
        assertNull(unavailable.collectedBytes)
        assertNull(unavailable.collectedFileCount)
    }

    @Test
    fun `location precision preserves server measured categories and ratios`() {
        val quality = gson.fromJson(
            """{
                "schemaVersion":1,
                "sampleState":"OBSERVED",
                "locationPrecision":[
                    {"fixState":"FIXED","durationMillis":4000,"ratio":0.666667},
                    {"fixState":"FLOAT","durationMillis":2000,"ratio":0.333333}
                ]
            }""".trimIndent(),
            RunQuality::class.java
        )

        assertEquals(listOf("FIXED", "FLOAT"), quality.locationPrecision.map { it.fixState })
        assertEquals(listOf(4000L, 2000L), quality.locationPrecision.map { it.durationMillis })
        assertEquals(0.666667, quality.locationPrecision.first().ratio, 0.000001)
    }
}
