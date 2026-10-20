package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.GeoReceiptItem
import com.example.jetsoncontroller.ui.components.geo.GeoReceiptState
import com.example.jetsoncontroller.ui.model.Formats
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class ResultAction(val label: String) {
    SEND("서버로 보내기"), TRANSFER("전송 보기"), VERIFY("수신 확인"), REPORT("완료 보고"), NEXT("다음 수집")
}

internal data class ResultChain(
    val receipt: GeoReceiptState,
    val action: ResultAction,
    val enabled: Boolean,
    val explanation: String,
    val remedy: String? = null,
    val jobId: String? = null
) {
    val actionLabel: String get() = if (action == ResultAction.REPORT && receipt.report.word == "보고 실패") "다시 보고" else action.label
}

internal data class ResultZoneObservation(
    val excursions: Int,
    val outsideMillis: Long,
    val unobservedMillis: Long
)

/** Run context and stored source must both match before a transfer belongs to this result. */
internal fun resultJobMatches(run: PipelineRun, job: UploadJob): Boolean {
    val expected = run.uploadContext
    val actual = job.context ?: return false
    return actual.runId == run.runId && actual.deviceId.equals(run.deviceId, true) &&
        actual.pipelineId == run.pipelineId && actual.surveyProjectId == expected.surveyProjectId &&
        actual.surveySectionId == expected.surveySectionId && actual.sourceRevision == run.sourceRevision &&
        actual.configSha256 == run.configRevision && actual.outputId == run.output.outputId &&
        job.rootId == run.output.rootId && job.relativePath.trim('/') == run.output.path.trim('/')
}

internal fun resultVerification(job: UploadJob): UploadVerification? {
    val receipt = job.verification ?: return null
    val source = listOf(job.sourceName, job.folderName, job.relativePath.trimEnd('/').substringAfterLast('/'))
        .firstOrNull { !it.isNullOrBlank() }
    return receipt.takeIf {
        it.jobId == job.id && it.targetId == job.targetId &&
            !job.remoteSessionId.isNullOrBlank() && it.remoteSessionId == job.remoteSessionId &&
            !source.isNullOrBlank() && it.sourceName == source
    }
}

internal fun resultChain(
    run: PipelineRun,
    jobs: List<UploadJob>,
    uploadEnabled: Boolean,
    uploadDisabledReason: String,
    connectionLabel: String,
    online: Boolean,
    ticketLinked: Boolean,
    reportEnabled: Boolean,
    reportPending: Boolean,
    reported: Boolean,
    reportMessage: String? = null,
    reportFailed: Boolean = false,
    localDeviceWord: String = "휴대폰"
): ResultChain {
    val cells = pipelineRunReceiptCells(run, Tone.UNKNOWN)
    val stored = runOutputStored(run)
    val exact = jobs.filter { resultJobMatches(run, it) }
    val verified = exact.firstOrNull {
        it.state == UploadJobState.COMPLETED && resultVerification(it)?.matched == true
    }
    val sending = exact.firstOrNull {
        it.state in setOf(UploadJobState.QUEUED, UploadJobState.SCANNING, UploadJobState.UPLOADING)
    }
    val sent = exact.firstOrNull {
        it.state == UploadJobState.COMPLETED && resultVerification(it)?.matched != false
    }
    val failed = exact.firstOrNull()
    val server = when {
        verified != null -> GeoReceiptItem("수신 확인", Tone.SUCCESS, "서버에서 이 수집의 수신을 확인했습니다.", true)
        sending != null -> GeoReceiptItem("전송 중", Tone.PENDING, "서버로 보내고 있습니다.")
        sent != null -> GeoReceiptItem("수신 미확인", Tone.PENDING, "서버 수신 내용을 확인하세요.")
        failed != null && resultVerification(failed)?.matched == false ->
            GeoReceiptItem("내용 불일치", Tone.WARNING, "서버 수신 내용이 일치하지 않습니다. 다시 보내세요.")
        failed != null -> GeoReceiptItem("확인 필요", Tone.WARNING, "전송 상태를 다시 확인하세요.")
        else -> GeoReceiptItem(meta = "이 ${localDeviceWord}에서 보낸 기록이 없습니다.")
    }
    val report = when {
        !ticketLinked -> GeoReceiptItem("해당 없음", Tone.UNKNOWN, "연결된 수집 구간이 없습니다.", true)
        reported -> GeoReceiptItem("보고 완료", Tone.SUCCESS, reportMessage, true)
        reportPending -> GeoReceiptItem("보고 대기", Tone.PENDING, reportMessage ?: "완료 보고를 보내고 있습니다.")
        reportFailed || reportMessage?.contains("실패") == true -> GeoReceiptItem("보고 실패", Tone.WARNING, reportMessage)
        else -> GeoReceiptItem("보고 전", Tone.UNKNOWN,
            reportMessage ?: "서버 수신을 확인한 뒤 보고할 수 있습니다.")
    }
    val manifest = run.output.manifest?.takeIf { stored }
    val receipt = GeoReceiptState(
        collection = GeoReceiptItem(cells[0].value, cells[0].tone,
            "${resultTime(run.finishedAt)} · ${resultDuration(run)}", !run.active && run.finishedAt != null),
        storage = GeoReceiptItem(cells[1].value, cells[1].tone,
            manifest?.let { "${String.format(Locale.ROOT, "%,d", it.fileCount)}개 · ${Formats.size(it.bytesTotal)}" }, stored),
        server = server, report = report
    )
    fun action(action: ResultAction, enabled: Boolean, explanation: String, remedy: String? = null, job: UploadJob? = null) =
        ResultChain(receipt, action, enabled, explanation, remedy, job?.id)
    return when {
        !stored -> action(ResultAction.SEND, false, "장치 저장 결과를 확인한 뒤 서버로 보낼 수 있습니다.", "다시 확인")
        verified != null && ticketLinked && !reported -> action(ResultAction.REPORT,
            reportEnabled && !reportPending, reportMessage ?: "GEO& Work에 완료를 보고하세요.", job = verified)
        verified != null -> action(ResultAction.NEXT, true, "서버 수신을 확인했습니다.", job = verified)
        sending != null -> action(ResultAction.TRANSFER, true, "서버로 보내고 있습니다.", job = sending)
        sent != null -> action(ResultAction.VERIFY, true, "서버 수신 내용을 확인하세요.", job = sent)
        !online -> action(ResultAction.SEND, false, "장치 연결이 끊겼습니다. 연결 방법을 확인하세요.", "연결 방법")
        !uploadEnabled && connectionLabel.contains("직접 연결") -> action(ResultAction.SEND, false,
            "같은 네트워크에서만 서버로 보낼 수 있습니다. 지금은 직접 연결입니다.", "장치 Wi-Fi 설정")
        !uploadEnabled -> action(ResultAction.SEND, false,
            uploadDisabledReason.ifBlank { "같은 네트워크에 연결한 뒤 서버로 보낼 수 있습니다." }, "연결 방법")
        else -> action(ResultAction.SEND, true, "지금 보낼 수 있습니다.")
    }
}

internal fun resultInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }
internal fun resultTime(value: String?): String = Formats.time(resultInstant(value))
internal fun resultDuration(run: PipelineRun): String = Formats.duration(
    verifiedRunTelemetry(run)?.durationMillis?.takeIf { it >= 0 }?.let(Duration::ofMillis)
        ?: elapsedResultDuration(run.startedAt, run.finishedAt)
)
internal fun elapsedResultDuration(start: String?, end: String?): Duration? {
    val from = resultInstant(start) ?: return null
    val to = resultInstant(end) ?: return null
    return Duration.between(from, to).takeUnless { it.isNegative }
}

internal fun historyDayLabel(start: String, now: Instant = Instant.now()): String {
    val date = resultInstant(start)?.atZone(ZoneId.of("Asia/Seoul"))?.toLocalDate() ?: return "날짜 미확인"
    val today = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
    return when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "M월 d일" else "yyyy년 M월 d일", Locale.KOREA))
    }
}
