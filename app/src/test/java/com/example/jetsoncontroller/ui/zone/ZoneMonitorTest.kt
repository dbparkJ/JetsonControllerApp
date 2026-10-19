package com.example.jetsoncontroller.ui.zone

import com.example.jetsoncontroller.data.zone.ZonePolygon
import com.example.jetsoncontroller.data.zone.ZonePosition
import com.example.jetsoncontroller.data.zone.ZoneRange
import com.example.jetsoncontroller.model.GnssSensorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneMonitorTest {
    private val range = ZoneRange(listOf(ZonePolygon(listOf(square(-100.0, 100.0)))))

    @Test
    fun `initialization waits for two distinct agreeing fresh samples`() {
        assertEquals(ZoneState.CHECKING_LOCATION, ZoneMonitorState().state)
        val first = evaluate(0.0, 1_000)
        assertEquals(ZoneState.CHECKING_LOCATION, first.monitor.state)
        assertEquals(1, first.monitor.pendingCount)
        val second = evaluate(0.0, 2_000, first.monitor)
        assertEquals(ZoneState.INSIDE, second.monitor.state)
        assertEquals(1_000L, second.transition?.firstSampleTimeEpochMillis)
        assertFalse(second.transition!!.exitWarning)
    }

    @Test
    fun `inside outside and near candidates confirm only after two samples`() {
        for ((x, expected) in listOf(0.0 to ZoneState.INSIDE, 130.0 to ZoneState.OUTSIDE, 99.8 to ZoneState.NEAR_BOUNDARY)) {
            val first = evaluate(x, 1_000)
            val result = evaluate(x, 2_000, first.monitor)
            assertEquals(expected, result.monitor.state)
            assertEquals(expected == ZoneState.OUTSIDE, result.warningVisible(runActive = true))
        }
    }

    @Test
    fun `signed distance measures the closest segment and vertex in metres`() {
        assertEquals(-100.0, ZoneMonitor.signedDistanceM(range, 0.0, 0.0), 0.0001)
        assertEquals(30.0, ZoneMonitor.signedDistanceM(range, 0.0, degrees(130.0)), 0.0001)
        assertEquals(50.0, ZoneMonitor.signedDistanceM(range, degrees(140.0), degrees(130.0)), 0.001)
    }

    @Test
    fun `hole is outside with nearest hole edge counted as a boundary`() {
        val withHole = ZoneRange(listOf(ZonePolygon(listOf(square(-100.0, 100.0), square(-20.0, 20.0)))))
        assertEquals(20.0, ZoneMonitor.signedDistanceM(withHole, 0.0, 0.0), 0.0001)
        assertEquals(-10.0, ZoneMonitor.signedDistanceM(withHole, 0.0, degrees(30.0)), 0.0001)
        assertEquals(0.0, ZoneMonitor.signedDistanceM(withHole, 0.0, degrees(20.0)), 0.0)
        var monitor = ZoneMonitor.evaluate(withHole, sample(0.0, 1_000), 1_000).monitor
        monitor = ZoneMonitor.evaluate(withHole, sample(0.0, 2_000), 2_000, monitor).monitor
        assertEquals(ZoneState.OUTSIDE, monitor.state)
    }

    @Test
    fun `MultiPolygon includes either island excludes the gap and honors holes`() {
        val multi = ZoneRange(listOf(
            ZonePolygon(listOf(square(-100.0, 100.0), square(-20.0, 20.0))),
            ZonePolygon(listOf(square(200.0, 300.0)))
        ))
        assertTrue(ZoneMonitor.signedDistanceM(multi, 0.0, degrees(50.0)) < 0)
        assertTrue(ZoneMonitor.signedDistanceM(multi, 0.0, degrees(250.0)) < 0)
        assertEquals(50.0, ZoneMonitor.signedDistanceM(multi, 0.0, degrees(150.0)), 0.0001)
        assertTrue(ZoneMonitor.signedDistanceM(multi, 0.0, 0.0) > 0)
        val first = ZoneMonitor.evaluate(multi, sample(250.0, 1_000), 1_000)
        assertEquals(ZoneState.INSIDE, ZoneMonitor.evaluate(multi, sample(250.0, 2_000), 2_000, first.monitor).monitor.state)
    }

    @Test
    fun `boundary vertices and reversed winding all use the near state`() {
        for (ring in listOf(square(-100.0, 100.0), square(-100.0, 100.0).reversed())) {
            val shape = ZoneRange(listOf(ZonePolygon(listOf(ring))))
            assertEquals(0.0, ZoneMonitor.signedDistanceM(shape, 0.0, degrees(100.0)), 0.0)
            assertEquals(0.0, ZoneMonitor.signedDistanceM(shape, degrees(100.0), degrees(100.0)), 0.0)
            val first = ZoneMonitor.evaluate(shape, sample(100.0, 1_000), 1_000)
            val second = ZoneMonitor.evaluate(shape, sample(100.0, 2_000), 2_000, first.monitor)
            assertEquals(ZoneState.NEAR_BOUNDARY, second.monitor.state)
            assertFalse(second.transition!!.exitWarning)
        }
    }

    @Test
    fun `local projection at nonzero latitude retains metre distances`() {
        val latitude = 37.0
        val lonScale = kotlin.math.cos(Math.toRadians(latitude))
        val ring = square(-100.0, 100.0).map {
            ZonePosition(127.0 + it.longitude / lonScale, latitude + it.latitude)
        }
        assertEquals(-100.0, ZoneMonitor.signedDistanceM(ZoneRange(listOf(ZonePolygon(listOf(ring)))), latitude, 127.0), 0.001)
    }

    @Test
    fun `swapped invalid missing and nonfinite sample coordinates become unknown`() {
        for (invalid in listOf(
            sample(0.0, 3_000).copy(latitude = 127.0, longitude = 37.0),
            sample(0.0, 3_000).copy(longitude = 181.0),
            sample(0.0, 3_000).copy(latitude = Double.NaN),
            sample(0.0, 3_000).copy(longitude = Double.POSITIVE_INFINITY),
            sample(0.0, 3_000).copy(latitude = null)
        )) {
            val result = ZoneMonitor.evaluate(range, invalid, 3_000, confirmedInside())
            assertEquals(ZoneState.UNKNOWN, result.monitor.state)
            assertEquals(ZoneObservationIssue.INVALID_POSITION, result.monitor.issue)
            assertFalse(result.warningVisible(true))
        }
    }

    @Test
    fun `uncertainty takes maximum of reported accuracy and category floor`() {
        for ((category, floor) in listOf(ZoneFixCategory.RTK_FIXED to 0.5, ZoneFixCategory.RTK_FLOAT to 2.0,
            ZoneFixCategory.STANDALONE to 5.0)) {
            val fix = sample(0.0, 1_000).copy(fixCategory = category, hdop = 10.0)
            assertEquals(floor, ZoneMonitor.uncertainty(fix.copy(reportedAccuracyM = 0.1))!!.metres, 0.0)
            assertEquals(20.0, ZoneMonitor.uncertainty(fix.copy(reportedAccuracyM = 20.0))!!.metres, 0.0)
            assertFalse(ZoneMonitor.uncertainty(fix.copy(reportedAccuracyM = 20.0))!!.estimated)
        }
        assertEquals(30.0, ZoneMonitor.uncertainty(sample(0.0, 1_000).copy(fixCategory = ZoneFixCategory.STANDALONE, hdop = 10.0))!!.metres, 0.0)
        assertTrue(ZoneMonitor.uncertainty(sample(0.0, 1_000))!!.estimated)
        assertEquals("추정", ZoneMonitor.uncertainty(sample(0.0, 1_000))!!.qualifier)
        assertNull(ZoneMonitor.uncertainty(sample(0.0, 1_000).copy(fixCategory = ZoneFixCategory.NONE)))
    }

    @Test
    fun `missing invalid accuracy and HDOP fall back to a finite estimate`() {
        for (invalid in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val uncertainty = ZoneMonitor.uncertainty(sample(0.0, 1_000).copy(
                fixCategory = ZoneFixCategory.STANDALONE, hdop = invalid, reportedAccuracyM = invalid))!!
            assertEquals(5.0, uncertainty.metres, 0.0)
            assertTrue(uncertainty.estimated)
        }
    }

    @Test
    fun `reported accuracy expands near boundary band`() {
        val first = ZoneMonitor.evaluate(range, sample(108.0, 1_000).copy(reportedAccuracyM = 10.0), 1_000)
        val second = ZoneMonitor.evaluate(range, sample(108.0, 2_000).copy(reportedAccuracyM = 10.0), 2_000, first.monitor)
        assertEquals(ZoneState.NEAR_BOUNDARY, second.monitor.state)
        assertFalse(second.transition!!.rejectHaptic)
    }

    @Test
    fun `equality at positive and negative uncertainty belongs outside and inside`() {
        val distance = ZoneMonitor.signedDistanceM(range, 0.0, degrees(110.0))
        val first = ZoneMonitor.evaluate(range, sample(110.0, 1_000).copy(reportedAccuracyM = distance), 1_000)
        assertEquals(ZoneState.OUTSIDE, first.monitor.pendingCandidate)
        val insideDistance = -ZoneMonitor.signedDistanceM(range, 0.0, degrees(90.0))
        assertEquals(ZoneState.INSIDE, ZoneMonitor.evaluate(range,
            sample(90.0, 1_000).copy(reportedAccuracyM = insideDistance), 1_000).monitor.pendingCandidate)
    }

    @Test
    fun `duplicate and out of order samples never advance counters or issue another haptic`() {
        val first = evaluate(130.0, 1_000)
        for (time in listOf(1_000L, 900L)) {
            val repeated = ZoneMonitor.evaluate(range, sample(130.0, time), 2_000, first.monitor, runActive = true)
            assertEquals(first.monitor, repeated.monitor)
            assertFalse(repeated.acceptedSample)
            assertNull(repeated.transition)
        }
        val second = evaluate(130.0, 2_000, first.monitor)
        assertTrue(second.transition!!.rejectHaptic)
        val repeatConfirmed = evaluate(130.0, 2_000, second.monitor)
        assertNull(repeatConfirmed.transition)
        assertEquals(ZoneState.OUTSIDE, repeatConfirmed.monitor.state)
    }

    @Test
    fun `fresh status repeating stale sample becomes unknown immediately`() {
        val previous = confirmedInside()
        val atLimit = ZoneMonitor.evaluate(range, sample(0.0, 2_000), 12_000, previous)
        assertEquals(ZoneState.INSIDE, atLimit.monitor.state)
        val stale = ZoneMonitor.evaluate(range, sample(0.0, 2_000), 12_001, previous)
        assertEquals(ZoneState.UNKNOWN, stale.monitor.state)
        assertEquals(ZoneObservationIssue.STALE_SAMPLE, stale.monitor.issue)
        assertEquals(0, stale.monitor.pendingCount)
    }

    @Test
    fun `stream freshness uses elapsed status age and never a phone epoch clock`() {
        val previous = confirmedInside()
        val fresh = ZoneMonitor.evaluate(range, sample(0.0, 2_000), 2_000, previous, statusAgeMillis = 10_000)
        assertEquals(ZoneState.INSIDE, fresh.monitor.state)
        val stale = ZoneMonitor.evaluate(range, sample(0.0, 2_000), 2_000, previous, statusAgeMillis = 10_001)
        assertEquals(ZoneObservationIssue.STALE_STREAM, stale.monitor.issue)
        assertEquals(ZoneState.UNKNOWN, stale.monitor.state)
    }

    @Test
    fun `no fix and invalid times clear outside immediately and recovery initializes anew`() {
        val outside = evaluate(130.0, 2_000, evaluate(130.0, 1_000).monitor).monitor
        for (fix in listOf(null, sample(130.0, 3_000).copy(fixCategory = ZoneFixCategory.NONE),
            sample(130.0, 3_000).copy(sampleTimeEpochMillis = null), sample(130.0, 4_000))) {
            val lost = ZoneMonitor.evaluate(range, fix, 3_000, outside, runActive = true)
            assertEquals(ZoneState.UNKNOWN, lost.monitor.state)
            assertFalse(lost.warningVisible(true))
            val first = evaluate(130.0, 5_000, lost.monitor)
            assertEquals(ZoneState.CHECKING_LOCATION, first.monitor.state)
            assertEquals(ZoneState.OUTSIDE, evaluate(130.0, 6_000, first.monitor).monitor.state)
        }
    }

    @Test
    fun `hysteresis resets when a candidate changes or returns to the displayed state`() {
        var state = confirmedInside()
        state = evaluate(130.0, 3_000, state).monitor
        assertEquals(ZoneState.INSIDE, state.state)
        state = evaluate(99.8, 4_000, state).monitor
        assertEquals(1, state.pendingCount)
        assertEquals(ZoneState.NEAR_BOUNDARY, state.pendingCandidate)
        state = evaluate(0.0, 5_000, state).monitor
        assertEquals(0, state.pendingCount)
        state = evaluate(130.0, 6_000, state).monitor
        assertEquals(ZoneState.INSIDE, state.state)
        val outside = evaluate(140.0, 7_000, state)
        assertEquals(ZoneState.OUTSIDE, outside.monitor.state)
        assertTrue(outside.transition!!.exitWarning)
        val returnFirst = evaluate(0.0, 8_000, outside.monitor)
        assertTrue(returnFirst.warningVisible(true))
        val returned = evaluate(0.0, 9_000, returnFirst.monitor)
        assertFalse(returned.warningVisible(true))
        assertFalse(returned.transition!!.rejectHaptic)
        assertEquals(8_000L, returned.transition.firstSampleTimeEpochMillis)
    }

    @Test
    fun `starting outside warns once during run and never before the run`() {
        val first = evaluate(130.0, 1_000)
        assertNull(first.transition)
        val run = evaluate(130.0, 2_000, first.monitor)
        assertTrue(run.transition!!.rejectHaptic)
        val preview = ZoneMonitor.evaluate(range, sample(130.0, 2_000), 2_000, first.monitor, runActive = false)
        assertEquals(ZoneState.OUTSIDE, preview.monitor.state)
        assertFalse(preview.transition!!.exitWarning)
        assertFalse(preview.warningVisible(false))
        assertNull(evaluate(130.0, 3_000, run.monitor).transition)
    }

    @Test
    fun `near return clears the warning after two samples without another message or haptic`() {
        val outside = evaluate(130.0, 2_000, evaluate(130.0, 1_000).monitor)
        val first = evaluate(100.0, 3_000, outside.monitor)
        assertTrue(first.warningVisible(true))
        val second = evaluate(100.0, 4_000, first.monitor)
        assertEquals(ZoneState.NEAR_BOUNDARY, second.monitor.state)
        assertFalse(second.warningVisible(true))
        assertFalse(second.transition!!.rejectHaptic)
    }

    @Test
    fun `warning and return latency are one to two actual intervals at 1 2 and 5 seconds`() {
        for (interval in listOf(1_000L, 2_000L, 5_000L)) {
            for (crossing in listOf(2 * interval + 1, 3 * interval - 1)) {
                var state = evaluate(0.0, 2 * interval, evaluate(0.0, interval).monitor).monitor
                val outsideFirst = evaluate(130.0, 3 * interval, state)
                assertFalse(outsideFirst.warningVisible(true))
                // The 1 s status polls may repeat this GNSS sample many times.
                for (poll in (3 * interval + 1_000) until (4 * interval) step 1_000) {
                    val duplicate = ZoneMonitor.evaluate(range, sample(130.0, 3 * interval), poll, outsideFirst.monitor, runActive = true)
                    assertFalse(duplicate.warningVisible(true))
                }
                val confirmed = evaluate(130.0, 4 * interval, outsideFirst.monitor)
                assertTrue(confirmed.transition!!.exitWarning)
                assertTrue(confirmed.transition.confirmedAtEpochMillis - crossing in interval..(2 * interval))
                state = evaluate(0.0, 5 * interval, confirmed.monitor).monitor
                val returned = evaluate(0.0, 6 * interval, state)
                val returnCrossing = crossing + 2 * interval
                assertTrue(returned.transition!!.confirmedAtEpochMillis - returnCrossing in interval..(2 * interval))
                assertFalse(returned.warningVisible(true))
            }
        }
    }

    @Test
    fun `status adapter uses device sample timestamp categories HDOP and optional phase 2 accuracy`() {
        for ((status, category) in listOf(
            GnssSensorStatus(fixType = "rtk_fixed") to ZoneFixCategory.RTK_FIXED,
            GnssSensorStatus(rtkStatus = "float") to ZoneFixCategory.RTK_FLOAT,
            GnssSensorStatus(fixQuality = 1) to ZoneFixCategory.STANDALONE,
            GnssSensorStatus(fixType = "single") to ZoneFixCategory.STANDALONE,
            GnssSensorStatus(fixQuality = 0, rtkStatus = "fixed") to ZoneFixCategory.NONE,
            GnssSensorStatus() to ZoneFixCategory.NONE
        )) {
            val adapted = ZoneSample.fromStatus(status.copy(lastSampleAtEpochMillis = 123, hdop = 2.5), reportedAccuracyM = 7.0)
            assertEquals(category, adapted.fixCategory)
            assertEquals(123L, adapted.sampleTimeEpochMillis)
            assertEquals(2.5, adapted.hdop!!, 0.0)
            assertEquals(7.0, adapted.reportedAccuracyM!!, 0.0)
        }
        assertEquals(ZoneObservationIssue.NO_RANGE, ZoneMonitor.evaluate(null, sample(0.0, 1_000), 1_000).monitor.issue)
    }

    private fun confirmedInside() = evaluate(0.0, 2_000, evaluate(0.0, 1_000).monitor).monitor

    private fun evaluate(x: Double, time: Long, previous: ZoneMonitorState = ZoneMonitorState()) =
        ZoneMonitor.evaluate(range, sample(x, time), time, previous, runActive = true)

    private fun sample(x: Double, time: Long) = ZoneSample(0.0, degrees(x), ZoneFixCategory.RTK_FIXED, time)

    private fun square(left: Double, right: Double) = listOf(
        ZonePosition(degrees(left), degrees(-100.0)), ZonePosition(degrees(right), degrees(-100.0)),
        ZonePosition(degrees(right), degrees(100.0)), ZonePosition(degrees(left), degrees(100.0)),
        ZonePosition(degrees(left), degrees(-100.0))
    )

    private fun degrees(metres: Double) = Math.toDegrees(metres / 6_371_008.8)
}
