package com.example.jetsoncontroller.ui.zone

import com.example.jetsoncontroller.data.zone.ZoneContract
import com.example.jetsoncontroller.data.zone.ZoneValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhoneObservedZoneEventsTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val key = ZoneRunKey("GEO-X-1", "run-1")

    @Test
    fun `events use first confirmed streak samples for both ends and retain first sample maximum`() {
        val source = source()
        source.startRun(key, contract(), 1_000)
        observe(source, 0.0, 1_000)
        observe(source, 0.0, 2_000)
        assertNull(observe(source, 160.0, 3_000).transition)
        assertTrue(observe(source, 130.0, 4_000).transition!!.rejectHaptic)
        observe(source, 140.0, 5_000)
        observe(source, 0.0, 6_000)
        assertFalse(observe(source, 0.0, 7_000).transition!!.rejectHaptic)
        val events = source.read(key)!!
        val excursion = events.excursions.single()
        assertEquals(3_000L, excursion.startedAtEpochMillis)
        assertEquals(6_000L, excursion.endedAtEpochMillis)
        assertEquals(60.0, excursion.maxDistanceM, 0.001)
        assertEquals(ZoneExcursionEnd.RETURNED, excursion.endReason)
        assertEquals(3_000L, events.outsideDurationMillis(7_000))
        assertEquals("관찰한 시간 기준", events.observationQualifier)
    }

    @Test
    fun `a lone outside sample does not create an excursion`() {
        val source = source()
        source.startRun(key, contract(), 1_000)
        observe(source, 0.0, 1_000)
        observe(source, 0.0, 2_000)
        observe(source, 130.0, 3_000)
        observe(source, 0.0, 4_000)
        assertTrue(source.read(key)!!.excursions.isEmpty())
    }

    @Test
    fun `background gap closes excursion at last sample and resumes with two new samples`() {
        val file = temporaryFolder.newFile("background.json")
        val source = PhoneObservedZoneEvents(file)
        source.startRun(key, contract(), 1_000)
        observe(source, 130.0, 1_000)
        observe(source, 140.0, 2_000)
        source.pause(key, 2_500)
        var events = source.read(key)!!
        assertEquals(ZoneState.UNKNOWN, events.monitor.state)
        assertEquals(2_000L, events.excursions.single().endedAtEpochMillis)
        assertEquals(ZoneExcursionEnd.OBSERVATION_GAP, events.excursions.single().endReason)
        assertEquals(2_500L, events.gaps.last().startedAtEpochMillis)
        assertEquals(ZoneGapReason.BACKGROUND, events.gaps.last().reason)
        // A repeated last fix on resume neither fills the gap nor initializes the state.
        source.observe(key, sample(140.0, 2_000), 3_000)
        assertNull(source.read(key)!!.gaps.last().endedAtEpochMillis)
        assertEquals(ZoneState.CHECKING_LOCATION, observe(source, 140.0, 4_000).monitor.state)
        assertTrue(observe(source, 140.0, 5_000).transition!!.exitWarning)
        events = source.read(key)!!
        assertEquals(4_000L, events.gaps.last().endedAtEpochMillis)
        assertEquals(1_500L, events.gapDurationMillis(5_000))
        assertEquals(2, events.excursions.size)
        assertEquals(4_000L, events.excursions.last().startedAtEpochMillis)
        assertTrue(file.readText().contains("BACKGROUND"))
    }

    @Test
    fun `process recreation persists a gap even when lifecycle stop never ran`() {
        val file = temporaryFolder.newFile("restart.json")
        var source = PhoneObservedZoneEvents(file)
        source.startRun(key, contract(), 1_000)
        observe(source, 140.0, 1_000)
        observe(source, 130.0, 2_000)
        source = PhoneObservedZoneEvents(file)
        val restored = source.read(key)!!
        assertEquals(2_000L, restored.excursions.single().endedAtEpochMillis)
        assertEquals(ZoneGapReason.PROCESS_RESTART, restored.gaps.last().reason)
        assertEquals(2_000L, restored.gaps.last().startedAtEpochMillis)
        assertNull(restored.gaps.last().endedAtEpochMillis)
        assertEquals(ZoneState.UNKNOWN, restored.monitor.state)
        // Repeated recreation must not add overlapping gaps.
        source = PhoneObservedZoneEvents(file)
        assertEquals(restored.gaps, source.read(key)!!.gaps)
        observe(source, 0.0, 6_000)
        assertEquals(6_000L, source.read(key)!!.gaps.last().endedAtEpochMillis)
        assertEquals(ZoneState.INSIDE, observe(source, 0.0, 7_000).monitor.state)
    }

    @Test
    fun `no fix stale sample and stream timeout each record a gap and remove warning`() {
        for (reason in listOf(ZoneGapReason.NO_FIX, ZoneGapReason.STALE_SAMPLE, ZoneGapReason.STALE_STREAM)) {
            val source = source()
            source.startRun(key, contract(), 1_000)
            observe(source, 140.0, 1_000)
            observe(source, 140.0, 2_000)
            when (reason) {
                ZoneGapReason.NO_FIX -> source.observe(key, sample(140.0, 3_000).copy(fixCategory = ZoneFixCategory.NONE), 3_000)
                ZoneGapReason.STALE_SAMPLE -> source.observe(key, sample(140.0, 2_000), 12_001)
                else -> {
                    source.streamStale(key, 12_000, 10_000)
                    assertEquals(ZoneState.OUTSIDE, source.read(key)!!.monitor.state)
                    source.streamStale(key, 12_001, 10_001)
                }
            }
            val events = source.read(key)!!
            assertEquals(ZoneState.UNKNOWN, events.monitor.state)
            assertEquals(reason, events.gaps.last().reason)
            assertEquals(if (reason == ZoneGapReason.NO_FIX) 3_000L else 12_000L, events.gaps.last().startedAtEpochMillis)
            assertEquals(2_000L, events.excursions.single().endedAtEpochMillis)
            observe(source, 0.0, 20_000)
            assertEquals(20_000L, source.read(key)!!.gaps.last().endedAtEpochMillis)
        }
    }

    @Test
    fun `initial lack of observations is a persisted gap and overlapping losses are merged`() {
        val source = source()
        source.startRun(key, contract(), 1_000)
        source.observe(key, null, 2_000)
        source.pause(key, 3_000)
        assertEquals(1, source.read(key)!!.gaps.size)
        assertEquals(1_000L, source.read(key)!!.gaps.single().startedAtEpochMillis)
        observe(source, 0.0, 5_000)
        assertEquals(4_000L, source.read(key)!!.gapDurationMillis(6_000))
        assertEquals(ZoneState.CHECKING_LOCATION, source.read(key)!!.monitor.state)
    }

    @Test
    fun `gaps and bound geometry survive completed run recreation and remain run scoped`() {
        val file = temporaryFolder.newFile("completed.json")
        val source = PhoneObservedZoneEvents(file)
        val otherRun = key.copy(runId = "run-2")
        val otherDevice = key.copy(deviceId = "GEO-X-2")
        source.startRun(key, contract(), 1_000)
        source.startRun(otherRun, contract("other-revision"), 1_000)
        source.startRun(otherDevice, contract("device-revision"), 1_000)
        observe(source, 130.0, 1_000)
        observe(source, 130.0, 2_000)
        source.pause(key, 3_000)
        source.endRun(key, 7_000)
        val expected = source.read(key)!!
        val restored: ZoneEventsSource = PhoneObservedZoneEvents(file)
        assertEquals(expected, restored.read(key))
        assertEquals(4_000L, restored.read(key)!!.gapDurationMillis(99_000))
        assertEquals(1, restored.read(key)!!.excursions.size)
        assertTrue(restored.read(otherRun)!!.excursions.isEmpty())
        assertEquals("other-revision", restored.read(otherRun)!!.binding.geometryRevision)
        assertEquals("device-revision", restored.read(otherDevice)!!.binding.geometryRevision)
        assertNull(restored.read(key.copy(runId = "missing")))
    }

    @Test
    fun `board edit after start cannot replace geometry revision hash or monitor range`() {
        val source = source()
        val original = source.startRun(key, contract("r1"), 1_000)
        val edited = contract("r2", left = 1_000.0, right = 2_000.0)
        assertEquals(original, source.startRun(key, edited, 2_000))
        assertEquals("r1", source.read(key)!!.binding.geometryRevision)
        assertEquals(original.geometryHash, source.read(key)!!.binding.contract().hash)
        observe(source, 0.0, 1_000)
        assertEquals(ZoneState.INSIDE, observe(source, 0.0, 2_000).monitor.state)
    }

    @Test
    fun `gap closes open excursion without counting all background time as outside`() {
        val source = source()
        source.startRun(key, contract(), 1_000)
        observe(source, 150.0, 1_000)
        observe(source, 150.0, 2_000)
        source.pause(key, 3_000)
        source.endRun(key, 63_000)
        val events = source.read(key)!!
        assertEquals(1_000L, events.outsideDurationMillis(63_000))
        assertEquals(60_000L, events.gapDurationMillis(63_000))
    }

    @Test
    fun `device source uses same result interface without phone observation qualifier`() {
        val source = source()
        source.startRun(key, contract(), 1_000)
        val snapshot = source.read(key)!!.copy(scope = ZoneObservationScope.DEVICE)
        val deviceSource = object : ZoneEventsSource {
            override fun read(key: ZoneRunKey): ZoneRunEvents? = snapshot.takeIf { it.binding.key == key }
        }
        assertNull(deviceSource.read(key)!!.observationQualifier)
        assertEquals(ZoneObservationScope.DEVICE, deviceSource.read(key)!!.scope)
    }

    @Test
    fun `completed runs reject further observation`() {
        val source = source()
        source.startRun(key, contract(), 1_000)
        source.endRun(key, 2_000)
        assertTrue(runCatching { observe(source, 130.0, 3_000) }.isFailure)
    }

    private fun source() = PhoneObservedZoneEvents(temporaryFolder.newFile())

    private fun observe(source: PhoneObservedZoneEvents, x: Double, time: Long) = source.observe(key, sample(x, time), time)

    private fun sample(x: Double, time: Long) = ZoneSample(0.0, degrees(x), ZoneFixCategory.RTK_FIXED, time)

    private fun contract(revision: String = "r1", left: Double = -100.0, right: Double = 100.0): ZoneContract {
        val a = degrees(left)
        val b = degrees(right)
        val y = degrees(100.0)
        return requireNotNull(ZoneValidation.parse("""{"schemaVersion":1,
            "features":{"type":"FeatureCollection","features":[]},
            "range":{"bufferM":10,"polygon":{"type":"Polygon","coordinates":[[[$a,${-y}],[$b,${-y}],[$b,$y],[$a,$y],[$a,${-y}]]]}},
            "revision":"$revision"}""").contract)
    }

    private fun degrees(metres: Double) = Math.toDegrees(metres / 6_371_008.8)
}
