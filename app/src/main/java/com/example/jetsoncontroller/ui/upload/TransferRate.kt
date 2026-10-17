package com.example.jetsoncontroller.ui.upload

/** One observation of an upload's transferred bytes, taken on a poll of that job. */
data class TransferSample(
    val jobId: String,
    val bytesTransferred: Long,
    val observedAtMillis: Long
)

/**
 * Speed and remaining time shown for an upload. Each value is null when it cannot be computed
 * from observations, and the screen then omits it instead of showing a guess.
 */
internal data class TransferRate(
    val bytesPerSecond: Long?,
    val remainingSeconds: Long?
)

internal const val TRANSFER_RATE_WINDOW_MILLIS = 30_000L
internal const val TRANSFER_RATE_MIN_SPAN_MILLIS = 3_000L
internal const val TRANSFER_SAMPLE_LIMIT = 40

/**
 * Average speed over the most recent window of samples. Needs at least
 * [TRANSFER_RATE_MIN_SPAN_MILLIS] between the oldest and newest sample in the window, and bytes
 * must have moved forward; a stalled or too-short window yields null rather than zero.
 */
internal fun measuredBytesPerSecond(samples: List<TransferSample>): Long? {
    if (samples.size < 2) return null
    val ordered = samples.sortedBy { it.observedAtMillis }
    val newest = ordered.last()
    val window = ordered.filter { newest.observedAtMillis - it.observedAtMillis <= TRANSFER_RATE_WINDOW_MILLIS }
    val oldest = window.first()
    val spanMillis = newest.observedAtMillis - oldest.observedAtMillis
    val movedBytes = newest.bytesTransferred - oldest.bytesTransferred
    if (spanMillis < TRANSFER_RATE_MIN_SPAN_MILLIS || movedBytes <= 0L) return null
    return movedBytes * 1_000L / spanMillis
}

/**
 * Speed and remaining time for the active job. Values measured from [samples] win; the backend
 * figures for the job are used only when the phone has not yet seen enough progress to measure.
 * Remaining time needs a known total and a positive speed.
 */
internal fun estimateTransferRate(
    samples: List<TransferSample>,
    bytesTotal: Long?,
    bytesTransferred: Long?,
    backendBytesPerSecond: Long? = null,
    backendEtaSeconds: Long? = null
): TransferRate {
    val speed = measuredBytesPerSecond(samples) ?: backendBytesPerSecond?.takeIf { it > 0L }
    val computedRemaining = if (
        speed != null && bytesTotal != null && bytesTransferred != null &&
        bytesTotal > 0L && bytesTransferred in 0L..bytesTotal
    ) {
        ((bytesTotal - bytesTransferred) / speed).coerceAtLeast(0L)
    } else {
        null
    }
    return TransferRate(
        bytesPerSecond = speed,
        remainingSeconds = computedRemaining ?: backendEtaSeconds?.takeIf { it >= 0L }
    )
}

/**
 * Adds [sample] to the window of the job it belongs to. Samples of any other job are dropped, and
 * the window keeps only the newest [TRANSFER_SAMPLE_LIMIT] observations.
 */
internal fun appendTransferSample(
    samples: List<TransferSample>,
    sample: TransferSample
): List<TransferSample> {
    val sameJob = samples.filter { it.jobId == sample.jobId }
    val last = sameJob.lastOrNull()
    val kept = if (last != null && last.observedAtMillis == sample.observedAtMillis) sameJob.dropLast(1) else sameJob
    return (kept + sample).takeLast(TRANSFER_SAMPLE_LIMIT)
}

/** The samples observed for one job, oldest first. */
internal fun transferSamplesFor(jobId: String, samples: List<TransferSample>): List<TransferSample> =
    samples.filter { it.jobId == jobId }.sortedBy { it.observedAtMillis }

/** Rounds up to whole minutes for the summary line: "약 19분", "약 1시간 5분", "1분 미만". */
internal fun approximateRemainingLabel(remainingSeconds: Long): String {
    val seconds = remainingSeconds.coerceAtLeast(0L)
    if (seconds < 60L) return "1분 미만"
    val totalMinutes = (seconds + 59L) / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours == 0L -> "약 ${minutes}분"
        minutes == 0L -> "약 ${hours}시간"
        else -> "약 ${hours}시간 ${minutes}분"
    }
}

/** Rate for display, for example "6.4 MB/s". */
internal fun transferRateLabel(bytesPerSecond: Long): String = "${formatTransferSize(bytesPerSecond)}/s"

private fun formatTransferSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
}
