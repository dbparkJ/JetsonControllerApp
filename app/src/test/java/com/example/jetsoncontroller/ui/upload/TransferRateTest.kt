package com.example.jetsoncontroller.ui.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferRateTest {

    @Test
    fun `speed is not computed from fewer than two observations or a window under three seconds`() {
        assertNull(measuredBytesPerSecond(emptyList()))
        assertNull(measuredBytesPerSecond(listOf(sample(0L, 0L))))
        assertNull(measuredBytesPerSecond(listOf(sample(0L, 0L), sample(2_000L, 1_000_000L))))
    }

    @Test
    fun `a stalled transfer has no speed rather than zero`() {
        val stalled = listOf(sample(0L, 500L), sample(10_000L, 500L))

        assertNull(measuredBytesPerSecond(stalled))
    }

    @Test
    fun `speed is the average over the observed window`() {
        val samples = listOf(sample(0L, 0L), sample(4_000L, 4L * MIB), sample(8_000L, 12L * MIB))

        assertEquals(1_572_864L, measuredBytesPerSecond(samples))
    }

    @Test
    fun `old observations outside the thirty second window do not drag the speed down`() {
        val samples = listOf(
            sample(0L, 0L),
            sample(40_000L, 10L * MIB),
            sample(44_000L, 14L * MIB)
        )

        assertEquals(1_048_576L, measuredBytesPerSecond(samples))
    }

    @Test
    fun `backend figures fill in only when nothing was measured`() {
        val fromBackend = estimateTransferRate(
            samples = emptyList(),
            bytesTotal = 10L * MIB,
            bytesTransferred = 2L * MIB,
            backendBytesPerSecond = 2L * MIB,
            backendEtaSeconds = 4L
        )
        assertEquals(2L * MIB, fromBackend.bytesPerSecond)
        assertEquals(4L, fromBackend.remainingSeconds)

        val measured = estimateTransferRate(
            samples = listOf(sample(0L, 0L), sample(8_000L, 8L * MIB)),
            bytesTotal = 10L * MIB,
            bytesTransferred = 8L * MIB,
            backendBytesPerSecond = 99L,
            backendEtaSeconds = 99L
        )
        assertEquals(MIB, measured.bytesPerSecond)
        assertEquals(2L, measured.remainingSeconds)
    }

    @Test
    fun `remaining time needs a known total and a speed`() {
        val unknownTotal = estimateTransferRate(
            samples = listOf(sample(0L, 0L), sample(8_000L, 8L * MIB)),
            bytesTotal = null,
            bytesTransferred = 8L * MIB
        )
        assertNull(unknownTotal.remainingSeconds)

        val noSpeed = estimateTransferRate(
            samples = emptyList(),
            bytesTotal = 10L * MIB,
            bytesTransferred = 2L * MIB
        )
        assertNull(noSpeed.remainingSeconds)
        assertNull(noSpeed.bytesPerSecond)
    }

    @Test
    fun `remaining time is rounded up to whole minutes for the summary`() {
        assertEquals("1분 미만", approximateRemainingLabel(30L))
        assertEquals("약 2분", approximateRemainingLabel(61L))
        assertEquals("약 19분", approximateRemainingLabel(19L * 60L))
        assertEquals("약 1시간", approximateRemainingLabel(3_600L))
        assertEquals("약 1시간 5분", approximateRemainingLabel(3_900L))
    }

    @Test
    fun `a window keeps only the job it was started for and is capped`() {
        val foreign = appendTransferSample(listOf(sample(0L, 0L)), TransferSample("other", 9L, 500L))
        assertEquals(listOf("other"), foreign.map { it.jobId })

        var samples = listOf(sample(0L, 0L))
        repeat(TRANSFER_SAMPLE_LIMIT + 10) { index ->
            samples = appendTransferSample(samples, sample(1_000L * (index + 1), index.toLong()))
        }
        assertEquals(TRANSFER_SAMPLE_LIMIT, samples.size)
        assertEquals(50_000L, samples.last().observedAtMillis)
    }

    @Test
    fun `rate labels use the transfer unit`() {
        assertEquals("1.0 MB/s", transferRateLabel(MIB))
        assertEquals("512 B/s", transferRateLabel(512L))
    }

    private fun sample(atMillis: Long, bytes: Long) = TransferSample(
        jobId = "job-1",
        bytesTransferred = bytes,
        observedAtMillis = atMillis
    )

    private companion object {
        const val MIB = 1_048_576L
    }
}
