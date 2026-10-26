package com.example.jetsoncontroller.ui.sensors

internal const val CAMERA_FRAME_MIN_STALE_MS = 3_000L
internal const val CAMERA_FRAME_INTERVAL_MULTIPLIER = 3L
internal const val CAMERA_FRAME_INITIAL_INTERVAL_MS = 1_000L
internal const val CAMERA_FRAME_RECOVERY_INTERVAL_MULTIPLIER = 2L
internal const val CAMERA_FRAME_RECOVERY_COUNT = 2
internal const val CAMERA_FRAME_CHECK_INTERVAL_MS = 250L
internal const val CAMERA_PREVIEW_MAX_EDGE = 1_280

/** All timestamps are phone elapsedRealtime; neither device time nor wall time is used. */
data class CameraFrameFreshness(
    val receivedAt: Long? = null,
    val intervalMillis: Long = CAMERA_FRAME_INITIAL_INTERVAL_MS,
    val delayed: Boolean = false,
    val recoveryCount: Int = 0
) {
    val staleAfterMillis: Long
        get() = maxOf(CAMERA_FRAME_MIN_STALE_MS, intervalMillis * CAMERA_FRAME_INTERVAL_MULTIPLIER)

    fun checked(now: Long): CameraFrameFreshness =
        if (receivedAt != null && (now < receivedAt || now - receivedAt > staleAfterMillis)) {
            copy(delayed = true, recoveryCount = 0)
        } else this

    fun received(now: Long): CameraFrameFreshness {
        val previous = receivedAt ?: return copy(receivedAt = now)
        // A repeated response/time is not another frame and cannot recover a stalled stream.
        if (now <= previous) return this
        val gap = now - previous
        val stalled = checked(now).delayed
        val timely = gap <= maxOf(CAMERA_FRAME_INITIAL_INTERVAL_MS,
            intervalMillis * CAMERA_FRAME_RECOVERY_INTERVAL_MULTIPLIER)
        val recovered = if (stalled && timely) recoveryCount + 1 else 1
        return copy(
            receivedAt = now,
            // Do not teach the estimator that an outage is the normal frame interval.
            intervalMillis = if (gap <= staleAfterMillis) (intervalMillis * 3 + gap) / 4 else intervalMillis,
            delayed = stalled && recovered < CAMERA_FRAME_RECOVERY_COUNT,
            recoveryCount = if (stalled) recovered.coerceAtMost(CAMERA_FRAME_RECOVERY_COUNT) else 0
        )
    }
}

internal fun cameraPreviewSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > CAMERA_PREVIEW_MAX_EDGE) sample *= 2
    return sample
}
