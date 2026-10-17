package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.model.UploadJob

/** Cadence of the single-job read that keeps the active transfer current. */
internal const val ACTIVE_JOB_POLL_INTERVAL_MILLIS = 1_000L

/** Cadence of the full history read. It is the only read that returns the whole list. */
internal const val FULL_HISTORY_POLL_INTERVAL_MILLIS = 15_000L

/**
 * The one active job the single-job read should fetch on this tick, or null when there is none.
 * The current job wins; otherwise the first active job in the queue, so a transfer started from
 * another client still moves. While the current job's own read loop is running it is skipped, so
 * a job is never read twice in the same second.
 */
internal fun activeJobToPoll(
    currentJob: UploadJob?,
    queue: List<UploadJob>,
    currentJobPollRunning: Boolean
): String? {
    if (currentJobPollRunning) return null
    currentJob?.takeIf { isActiveUploadState(it.state) }?.let { return it.id }
    return queue.firstOrNull { isActiveUploadState(it.state) }?.id
}

/** True once the history read is due. The per-second tick never reads the full list on its own. */
internal fun historyRefreshDue(elapsedSinceHistoryMillis: Long): Boolean =
    elapsedSinceHistoryMillis >= FULL_HISTORY_POLL_INTERVAL_MILLIS

/**
 * Replaces the job in place when it is already listed, and prepends it otherwise. A poll of a
 * job that is not the current one must not reorder the queue.
 */
internal fun replaceQueuedJob(queue: List<UploadJob>, job: UploadJob): List<UploadJob> =
    if (queue.any { it.id == job.id }) queue.map { if (it.id == job.id) job else it }
    else listOf(job) + queue
