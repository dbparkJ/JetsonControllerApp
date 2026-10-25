package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.ui.field.resultJobMatches
import com.example.jetsoncontroller.ui.field.runSourceReceipt
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.components.geo.GeoReceiptItem
import com.example.jetsoncontroller.ui.components.geo.GeoReceiptState
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.transfers.matchingTransferVerification
import com.example.jetsoncontroller.ui.transfers.transferDetailActions

internal fun uploadSourceReceipt(run: PipelineRun?, job: UploadJob?, deviceId: String): GeoReceiptState? =
    run?.takeIf { job != null && it.deviceId.equals(deviceId, true) && resultJobMatches(it, job) }
        ?.let(::runSourceReceipt)

/** A collection context identifies the source; it is not evidence that collection and storage finished. */
internal fun uploadReceiptState(
    job: UploadJob,
    verification: UploadVerification?,
    sourceReceipt: GeoReceiptState? = null,
    linkedToTicket: Boolean = false,
    ticketTitle: String? = null,
    reported: Boolean = false,
    reportPending: Boolean = false,
    reportFailed: Boolean = false,
    reportMessage: String? = null
): GeoReceiptState {
    val receipt = matchingTransferVerification(job, verification)
    val actions = transferDetailActions(job, receipt, linkedToTicket, reported)
    val received = job.state == UploadJobState.COMPLETED && receipt?.matched == true
    val serverMeta = when {
        job.state == UploadJobState.FAILED -> "연결이 끊겨 멈췄습니다."
        receipt?.matched == false -> "서버에 받은 내용이 보낸 내용과 일치하지 않습니다."
        received -> "내용 일치 · ${Formats.itemCount((receipt?.filesTotal ?: job.filesTotal)?.toLong())} · ${Formats.size(receipt?.bytesTotal ?: job.bytesTotal)}"
        job.state == UploadJobState.COMPLETED -> "보내기는 끝났고 서버 수신을 확인하고 있습니다."
        job.state == UploadJobState.CANCELLED -> "전송을 취소했습니다."
        else -> "보내기가 끝나면 서버 수신을 확인합니다."
    }
    return GeoReceiptState(
        collection = sourceReceipt?.collection ?: GeoReceiptItem(),
        storage = sourceReceipt?.storage ?: GeoReceiptItem(),
        server = GeoReceiptItem(actions.word, actions.tone, serverMeta, received),
        report = GeoReceiptItem(
            when { reported -> "보고됨"; reportPending -> "보고 대기"; reportFailed -> "보고 실패"; else -> "보고 전" },
            when { reported -> Tone.SUCCESS; reportPending -> Tone.PENDING; reportFailed -> Tone.WARNING; else -> Tone.UNKNOWN },
            reportMessage ?: if (linkedToTicket && received) "${ticketTitle ?: "수집 구간"} · 완료를 보고하세요."
                else "서버 수신을 확인한 뒤 보고할 수 있습니다.", reported)
    )
}
