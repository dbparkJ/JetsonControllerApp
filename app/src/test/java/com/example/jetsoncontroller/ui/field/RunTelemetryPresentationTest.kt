package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.RunTelemetry
import com.example.jetsoncontroller.ui.components.geo.figurePresentation
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RunTelemetryPresentationTest {
    @Test fun `duration advances only to freshness boundary`() {
        assertEquals(25_000L, smoothedDurationMillis(10_000L, 100L, 30_100L, true))
        assertEquals(10_000L, smoothedDurationMillis(10_000L, 100L, 30_100L, false))
        assertEquals(null, smoothedDurationMillis(null, 100L, 200L, true))
    }

    @Test fun `zero bytes remains distinct from unavailable`() {
        assertEquals("0\u00a0B", runBytesLabel(0L))
        assertEquals("미확인", runBytesLabel(null))
        assertEquals("1.2\u00a0GB", runBytesLabel(1_200_000_000L))
        assertEquals("00:12:34", runDurationLabel(754_000L))
        assertEquals("미확인", runDurationLabel(null))
    }

    @Test fun `status receipt and cached byte age stay distinct without comparing device clock`() {
        val telemetry = RunTelemetry(
            runId = "run", outputId = "output", sourceRevision = "source",
            observedAtEpochMillis = 20_000L, bytesObservedAtEpochMillis = 15_000L,
            collectionBytesState = "OBSERVED"
        )
        assertEquals("2초 전 · 용량 7초 전", telemetryObservationLabel(telemetry, 2_000L))
    }

    @Test fun `four P4 figures preserve true zero and default zone remains unknown`() {
        val run = runStopFixture()
        val figures = activeRunFigures(run, 754_000L, null, ActiveRunState.COLLECTING, 0L, 0)
        assertEquals(listOf("경과 시간", "수집 용량", "정밀 위치", "구역 밖 기록"), figures.map { it.label })
        assertEquals(listOf("00:12:34", "1.2\u00a0GB", "98%", "0회"), figures.map { figurePresentation(it).value })
        assertEquals("미확인", figurePresentation(activeRunFigures(run, 0L, null,
            ActiveRunState.COLLECTING, 0L, null).last()).value)
        assertEquals(null, RunZonePresentation().badge)
        assertEquals(null, RunZonePresentation().strip)
        assertEquals(null, RunZonePresentation().outsideCount)
    }

    @Test fun `pending start never displays retained metrics as confirmed`() {
        val figures = activeRunFigures(runStopFixture(), 754_000L, null, ActiveRunState.START_CHECKING, 0L, 0)
        assertTrue(figures.all { figurePresentation(it).unknown })
    }

    @Test fun `offline unknown and aged telemetry retain last value captions`() {
        val run = runStopFixture()
        listOf(ActiveRunState.DISCONNECTED, ActiveRunState.UNKNOWN).forEach { state ->
            val figures = activeRunFigures(run, 754_000L, null, state, 0L, 1)
            assertTrue(figures.all { figurePresentation(it).detail == "마지막 값 기준" })
        }
        assertTrue(activeRunFigures(run, 754_000L, null, ActiveRunState.COLLECTING, 16_000L, 1).all { it.stale })
    }

    @Test fun `identity mismatch and absent precision samples remain unknown`() {
        val run = runStopFixture()
        val wrongPacket = run.copy(telemetry = run.telemetry!!.copy(runId = "other"), quality = run.quality!!.copy(rtkObservedDurationMillis = 0L))
        val figures = activeRunFigures(wrongPacket, 754_000L, null, ActiveRunState.COLLECTING, 0L, null)
        assertTrue(figures.all { figurePresentation(it).unknown })
    }
}
