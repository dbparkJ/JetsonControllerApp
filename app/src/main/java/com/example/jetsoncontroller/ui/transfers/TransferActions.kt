package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.model.UploadContext
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.ui.components.StatusTone

/** A confirmation keeps this source even if polling, navigation or the current device changes. */
data class TransferActionTarget(
    val deviceId: String,
    val jobId: String,
    val rootId: String,
    val path: String,
    val targetId: String,
    val remoteSessionId: String?,
    val sourceIdentity: String?,
    val context: UploadContext?,
    val trashId: String?
) {
    fun matches(deviceId: String?, job: UploadJob): Boolean =
        this.deviceId.equals(deviceId, true) && job.id == jobId && job.rootId == rootId &&
            job.relativePath == path && job.targetId == targetId &&
            job.remoteSessionId == remoteSessionId && job.sourceIdentity == sourceIdentity &&
            job.context == context && job.sourceTrashId == trashId
}

enum class TransferMutation { CANCEL, RETRY, VERIFY, CLEAN_UP, RESTORE, DELETE_RECORD }

fun transferActionTarget(deviceId: String, job: UploadJob) = TransferActionTarget(
    deviceId, job.id, job.rootId, job.relativePath, job.targetId, job.remoteSessionId,
    job.sourceIdentity, job.context, job.sourceTrashId
)

/** Phase 1 identity rule: every identity supplied by the receipt must agree with the job. */
internal fun matchingTransferVerification(
    job: UploadJob,
    verification: UploadVerification? = job.verification
): UploadVerification? = verification?.takeIf {
    it.jobId == job.id && (it.targetId == null || it.targetId == job.targetId) &&
        (it.remoteSessionId == null || it.remoteSessionId == job.remoteSessionId) &&
        (it.sourceName == null || it.sourceName == sourceIdentityName(job))
}

private fun sourceIdentityName(job: UploadJob): String? =
    listOf(job.sourceName, job.folderName, job.relativePath.substringAfterLast('/'))
        .firstOrNull { !it.isNullOrBlank() }

internal fun transferSourceName(job: UploadJob): String = sourceIdentityName(job) ?: "수집 자료"

internal fun transferTargetLabel(target: UploadTarget): String =
    if (target.id == "operations" && target.label == "Operations upload server") "운영 서버" else target.label

internal enum class TransferDetailAction(val label: String) {
    CANCEL("전송 취소"), RETRY("다시 보내기"), VERIFY("수신 확인"),
    REPORT("완료 보고"), RESULT("수집 결과"), SERVER("서버에서 보기"), NONE("")
}

internal data class TransferDetailActions(
    val primary: TransferDetailAction,
    val word: String,
    val tone: StatusTone,
    val canCleanUp: Boolean = false,
    val canRestore: Boolean = false
)

internal fun transferDetailActions(
    job: UploadJob,
    verification: UploadVerification? = job.verification,
    linkedToTicket: Boolean = false,
    reported: Boolean = false
): TransferDetailActions {
    val receipt = matchingTransferVerification(job, verification)
    val (action, word, tone) = when (job.state) {
        UploadJobState.QUEUED -> Triple(TransferDetailAction.CANCEL, "대기 중", StatusTone.UNKNOWN)
        UploadJobState.SCANNING -> Triple(TransferDetailAction.CANCEL, "파일 확인 중", StatusTone.PENDING)
        UploadJobState.UPLOADING -> Triple(TransferDetailAction.CANCEL, "보내는 중", StatusTone.INFO)
        UploadJobState.FAILED -> Triple(TransferDetailAction.RETRY, "실패", StatusTone.ERROR)
        UploadJobState.CANCELLED -> Triple(TransferDetailAction.NONE, "취소됨", StatusTone.UNKNOWN)
        UploadJobState.COMPLETED -> when (receipt?.matched) {
            false -> Triple(TransferDetailAction.RETRY, "내용 불일치", StatusTone.ERROR)
            null -> Triple(TransferDetailAction.VERIFY, "수신 확인 중", StatusTone.PENDING)
            true -> Triple(when {
                linkedToTicket && !reported -> TransferDetailAction.REPORT
                job.context != null -> TransferDetailAction.RESULT
                else -> TransferDetailAction.SERVER
            }, "수신 확인", StatusTone.SUCCESS)
        }
    }
    val completed = job.state == UploadJobState.COMPLETED
    return TransferDetailActions(
        action, word, tone,
        canCleanUp = completed && receipt?.matched == true && receipt.deletionAllowed &&
            job.deletionEligible && !job.sourceDeleted && !job.sourceRecoverable &&
            job.sourceDeletionState !in setOf("TRASHED", "PURGING", "PURGED", "UNKNOWN"),
        canRestore = completed && job.sourceRecoverable && job.sourceTrashId != null &&
            job.sourceDeletionState !in setOf("PURGING", "PURGED")
    )
}

internal fun transferActionAllowed(job: UploadJob, action: TransferDetailAction): Boolean =
    transferDetailActions(job).primary == action &&
        (action != TransferDetailAction.RETRY || (!job.sourceDeleted && !job.sourceRecoverable &&
            job.sourceDeletionState !in setOf("TRASHED", "PURGING", "PURGED", "UNKNOWN")))

internal fun transferMutationAllowed(
    target: TransferActionTarget,
    deviceId: String?,
    job: UploadJob,
    mutation: TransferMutation,
    controlAvailable: Boolean,
    sameNetwork: Boolean
): Boolean {
    if (!controlAvailable || !target.matches(deviceId, job)) return false
    if (!sameNetwork && mutation in setOf(TransferMutation.RETRY, TransferMutation.VERIFY, TransferMutation.CLEAN_UP)) return false
    return when (mutation) {
        TransferMutation.CANCEL -> transferActionAllowed(job, TransferDetailAction.CANCEL)
        TransferMutation.RETRY -> transferActionAllowed(job, TransferDetailAction.RETRY)
        TransferMutation.VERIFY -> job.state == UploadJobState.COMPLETED
        TransferMutation.CLEAN_UP -> transferDetailActions(job).canCleanUp
        TransferMutation.RESTORE -> transferDetailActions(job).canRestore
        TransferMutation.DELETE_RECORD -> job.state in setOf(UploadJobState.COMPLETED, UploadJobState.FAILED, UploadJobState.CANCELLED)
    }
}

internal const val TransferCancelBody = "보낸 일부 데이터는 서버에 남을 수 있고, 다시 보내면 처음부터 보냅니다."
internal const val TransferRecordDeleteBody = "목록에서만 지웁니다. 장치 원본과 서버의 데이터는 그대로입니다."

internal fun transferLogMatches(job: UploadJob, filter: Int): Boolean = when (filter) {
    1 -> job.state == UploadJobState.FAILED ||
        (job.state == UploadJobState.COMPLETED && matchingTransferVerification(job)?.matched == false)
    2 -> job.state == UploadJobState.COMPLETED && matchingTransferVerification(job)?.matched == true
    3 -> job.state == UploadJobState.CANCELLED
    else -> true
}
