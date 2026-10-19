package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.data.server.*
import com.example.jetsoncontroller.ui.model.ServerJobState
import com.example.jetsoncontroller.ui.model.VocabularyEntry
import com.example.jetsoncontroller.ui.model.Tone
import java.util.Locale

internal const val UNKNOWN_SERVER_MUTATION =
    "처리 결과를 확인하지 못했습니다. 같은 작업을 반복하지 말고 목록을 다시 확인하세요."

internal data class ServerTrashTarget(val profileId: String, val job: ServerJob)

internal fun serverTrashTarget(state: DirectServerUiState, job: ServerJob): ServerTrashTarget? {
    val profile = state.selectedProfileId ?: return null
    return ServerTrashTarget(profile, job).takeIf { it.matches(state) }
}

internal fun ServerTrashTarget.matches(state: DirectServerUiState): Boolean =
    profileId == state.selectedProfileId && !state.isLoading && !state.isConnecting &&
        !state.isMutating && !state.mutationResultUnknown &&
        directServerRoleCanMutate(state.capabilities?.employee?.role) &&
        job.state == "COMPLETED" && state.jobs.any { it == job }

internal fun serverTrashCanRestore(job: ServerTrashJob): Boolean =
    job.state == "TRASHED" && job.restoreSupported != false

internal fun serverTrashStatus(job: ServerTrashJob): String = when {
    job.state == "PURGING" -> "삭제 처리 중"
    job.state == "RESTORING" -> "복원 중"
    serverTrashCanRestore(job) -> "복원 가능"
    else -> "복원할 수 없음"
}

internal fun serverTrashCount(shown: Int, total: Int): String =
    "전체 ${maxOf(shown, total)}개 중 ${shown}개를 표시합니다."

internal fun serverJobState(state: String): VocabularyEntry = when (state.uppercase(Locale.ROOT)) {
    "COMPLETED" -> ServerJobState.RECEIVED
    "UPLOADING" -> ServerJobState.SENDING
    "FAILED" -> ServerJobState.FAILED
    "TRASHED" -> ServerJobState.TRASH
    "PURGING" -> ServerJobState.DELETING
    else -> StorageWord("상태 미확인", Tone.UNKNOWN)
}

private data class StorageWord(override val word: String, override val tone: Tone) : VocabularyEntry

internal fun DirectServerUiState.withJobsPage(snapshot: ServerJobsSnapshot): DirectServerUiState = copy(
    jobs = (jobs + snapshot.response.jobs).associateBy { it.sessionId }.values.toList(),
    nextOffset = snapshot.response.nextOffset,
    refreshedAt = snapshot.response.refreshedAt,
    jobsSource = snapshot.source,
    cachedAtEpochMillis = snapshot.cachedAtEpochMillis,
    isLoading = false
)

internal data class ServerTrashEmptySnapshot(val profileId: String, val sessionIds: List<String>)

internal fun serverTrashEmptySnapshot(state: DirectServerUiState): ServerTrashEmptySnapshot? {
    val profileId = state.selectedProfileId ?: return null
    if (!state.trashEmptySupported) return null
    val ids = state.trash.filter(::isServerPurgeEligible).map(ServerTrashJob::sessionId).distinct().take(200)
    return ids.takeIf { it.isNotEmpty() }?.let { ServerTrashEmptySnapshot(profileId, it) }
}
