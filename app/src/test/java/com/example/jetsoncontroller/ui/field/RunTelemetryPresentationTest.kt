package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.RunTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

class RunTelemetryPresentationTest {
    @Test fun `duration advances only to freshness boundary`() {
        assertEquals(25_000L, smoothedDurationMillis(10_000L, 100L, 30_100L, true))
        assertEquals(10_000L, smoothedDurationMillis(10_000L, 100L, 30_100L, false))
        assertEquals(null, smoothedDurationMillis(null, 100L, 200L, true))
    }

    @Test fun `zero bytes remains distinct from unavailable`() {
        assertEquals("0 B", runBytesLabel(0L))
        assertEquals("확인 중", runBytesLabel(null))
    }

    @Test fun `status receipt and cached byte age stay distinct without comparing device clock`() {
        val telemetry = RunTelemetry(
            runId = "run", outputId = "output", sourceRevision = "source",
            observedAtEpochMillis = 20_000L, bytesObservedAtEpochMillis = 15_000L,
            collectionBytesState = "OBSERVED"
        )
        assertEquals("2초 전 · 용량 7초 전", telemetryObservationLabel(telemetry, 2_000L))
    }
}
