package com.example.jetsoncontroller.ui.dashboard

enum class StatusFreshness {
    CURRENT,
    STALE,
    UNKNOWN
}

internal fun statusFreshness(
    updatedAtEpochMillis: Long?,
    nowEpochMillis: Long,
    staleAfterMillis: Long = 15_000L
): StatusFreshness = when {
    updatedAtEpochMillis == null -> StatusFreshness.UNKNOWN
    nowEpochMillis - updatedAtEpochMillis > staleAfterMillis -> StatusFreshness.STALE
    else -> StatusFreshness.CURRENT
}

internal fun statusAgeSeconds(
    updatedAtEpochMillis: Long?,
    nowEpochMillis: Long
): Long? = updatedAtEpochMillis?.let {
    ((nowEpochMillis - it).coerceAtLeast(0L) / 1_000L)
}
