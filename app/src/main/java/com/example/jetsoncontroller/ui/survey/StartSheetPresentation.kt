package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.PositionQuality
import com.example.jetsoncontroller.ui.sensors.SensorActivity
import com.example.jetsoncontroller.ui.sensors.GnssReceptionState
import com.example.jetsoncontroller.ui.sensors.gnssReceptionState
import com.example.jetsoncontroller.ui.sensors.sensorPresentations

internal const val DraftPreflightUnavailableMessage =
    "새 프로젝트와 구간의 시작 전 점검을 지원하지 않습니다. 관리자에게 문의하세요."

internal data class PreparationTelemetry(
    val deviceId: String? = null,
    val status: JetsonStatus = JetsonStatus(),
    val connectionLabel: String = "연결됨",
    val correctionRelay: String? = null
)

internal data class PreparationReadiness(val label: String, val value: String, val tone: Tone)

internal fun preparationStatusWord(row: PreparationReadiness): String = when {
    row.label == "저장 공간" -> "점검 전"
    row.value == "위치 미확인" -> "미확인"
    row.value.length <= 6 -> row.value
    row.tone == Tone.SUCCESS -> "확인됨"
    row.tone == Tone.PENDING -> "확인 중"
    row.tone == Tone.WARNING || row.tone == Tone.ERROR -> "확인 필요"
    else -> "미확인"
}

internal fun optionalStartChecks(state: SurveyRunUiState): List<GeoStartCheck> =
    if (state.operation in setOf("create_context", "preflight")) emptyList() else state.preflight?.checks?.sensors.orEmpty()
        .filter { it.requirement != "REQUIRED" && !it.state.isPassedCheck() }.map {
            GeoStartCheck(when (it.sensor.lowercase()) {
                "camera" -> "카메라"
                "gnss" -> "위치 품질"
                "imu" -> "관성 센서"
                else -> "센서"
            }, GeoStartPhase.FAILED, "선택 센서 · 미확인", "선택 센서의 수신 상태를 확인하세요.")
        }

internal fun preparationReadiness(state: SurveyRunUiState, now: Long): List<PreparationReadiness> {
    val telemetry = state.preparationTelemetry.takeIf { it.deviceId.equals(state.deviceId, true) }
    val status = telemetry?.status ?: JetsonStatus()
    val fresh = state.online && status.collectedAtEpochMillis?.let { now - it in 0L..15_000L } == true
    val sensors = sensorPresentations(status, fresh, now)
    val storage = status.storageAvailableBytes.takeIf {
        fresh && it >= 0 && (status.metricValidity["storageAvailableBytes"]?.validity?.let { validity ->
            validity == "valid"
        } ?: (status.storageTotalBytes > 0))
    }
    val reception = gnssReceptionState(true, status.gnssSensor.fixType, status.gnssSensor.rtkStatus)
    val position = when {
        sensors.gnss.activity != SensorActivity.ACTIVE -> PositionQuality.UNKNOWN
        reception == GnssReceptionState.RTK_FIXED -> PositionQuality.PRECISE
        status.gnssSensor.latitude != null && status.gnssSensor.longitude != null -> PositionQuality.APPROXIMATE
        else -> PositionQuality.NONE
    }
    return listOf(
        PreparationReadiness("연결", if (state.online) telemetry?.connectionLabel ?: "연결됨" else "오프라인",
            if (state.online) Tone.SUCCESS else Tone.UNKNOWN),
        PreparationReadiness("저장 공간", storage?.let { "${Formats.size(it)} 남음" } ?: "미확인",
            if (storage != null) Tone.INFO else Tone.UNKNOWN),
        PreparationReadiness("위치 품질", position.word, position.tone),
        PreparationReadiness("카메라", sensors.camera.badge,
            when (sensors.camera.activity) {
                SensorActivity.ACTIVE -> Tone.SUCCESS
                SensorActivity.CONNECTED -> Tone.INFO
                SensorActivity.STALE, SensorActivity.DISCONNECTED -> Tone.WARNING
                else -> Tone.UNKNOWN
            })
    ) + listOfNotNull(telemetry?.correctionRelay?.let {
        PreparationReadiness("보정 정보 중계", if (state.online) it else "미확인", Tone.INFO)
    })
}

internal fun PreparationSelection?.preparationLabel(existing: String?): String = when (this) {
    is PreparationSelection.CreateNew -> label
    is PreparationSelection.CreateFromTicket -> label
    else -> existing ?: "미확인"
}

/** Opening S8 never treats an earlier successful check as the new check's response. */
internal fun startSheetState(
    state: SurveyRunUiState,
    deviceName: String,
    ticketTitle: String? = null,
    zoneLine: String = "위치 미확인",
    nowEpochMillis: Long = System.currentTimeMillis(),
    nowElapsedMillis: Long = System.nanoTime() / 1_000_000L,
    onStorage: (() -> Unit)? = null,
    onConnection: (() -> Unit)? = null,
    onSensors: (() -> Unit)? = null,
    preparationReady: Boolean = true
): GeoStartSheetState {
    val checking = state.operation in setOf("create_context", "preflight")
    val checked = state.preflight.takeUnless { checking }
    val valid = preparationReady && state.canStart && state.startTargetForSheet()?.let {
        startRevalidationError(it, state, nowEpochMillis, nowElapsedMillis) == null
    } == true
    fun row(label: String, passed: Boolean, value: String?, reason: String,
            remedy: GeoOverlayAction? = null): GeoStartCheck = GeoStartCheck(
        label, if (checking) GeoStartPhase.CHECKING else if (passed) GeoStartPhase.PASSED else GeoStartPhase.FAILED,
        value, if (passed || checking) null else reason, remedy
    )
    val checks = mutableListOf(
        row("연결", state.online && state.controlAvailable,
            if (state.online && state.controlAvailable) state.preparationTelemetry.connectionLabel else "오프라인",
            "연결되면 시작할 수 있습니다.", onConnection?.let { GeoOverlayAction("연결 방법", it) }),
        row("장치 시간", checked?.checks?.time?.state.isPassedCheck(),
            if (checked?.checks?.time?.state.isPassedCheck()) "맞음" else "미확인", "장치 시간을 다시 확인하세요."),
        row("저장 공간", checked?.checks?.storage?.state.isPassedCheck(),
            checked?.checks?.storage?.availableBytes?.let { "${Formats.size(it)} 남음" },
            if (checked?.checks?.storage?.let { it.availableBytes != null && it.availableBytes < it.requiredBytes } == true)
                "저장 공간이 수집 설정의 최소값보다 적습니다."
            else "저장 공간을 확인하지 못했습니다.", GeoOverlayAction("장치 파일", onStorage ?: {},
                enabled = onStorage != null, disabledReason = "장치 탭에서 장치 파일을 여세요."))
    )
    val sensorChecks = checked?.checks?.sensors ?: listOf(
        PreflightSensorCheck("camera", "REQUIRED"), PreflightSensorCheck("gnss", "REQUIRED")
    )
    sensorChecks.forEach { sensor ->
        val passed = sensor.state.isPassedCheck()
        val label = when (sensor.sensor.lowercase()) {
            "camera" -> "카메라"
            "gnss" -> "위치 품질"
            "imu" -> "관성 센서"
            else -> "센서"
        }
        // Optional warnings are rendered separately, outside S8's required-check gate.
        if (sensor.requirement == "REQUIRED" || passed || checking) {
            val value = if (!passed) "미확인" else if (sensor.sensor == "gnss") {
                preparationReadiness(state, nowEpochMillis).first { it.label == "위치 품질" }.value
                    .takeUnless { it == PositionQuality.UNKNOWN.word } ?: "수신 중"
            } else "수신 중"
            checks += row(label, passed, value,
                "센서 수신 상태를 확인하세요.", onSensors?.let { GeoOverlayAction("장치 상세", it) })
        }
    }
    if (!checking && !valid) {
        checks += row("시작 전 점검", false, "다시 확인", state.error ?: state.startTargetForSheet()?.let {
            startRevalidationError(it, state, nowEpochMillis, nowElapsedMillis)
        } ?: "점검을 통과해야 시작할 수 있습니다.")
    }
    val draft = state.preparationDraft
    return GeoStartSheetState(
        sectionName = ticketTitle ?: draft?.section.preparationLabel(state.selectedSection?.label),
        projectName = draft?.project.preparationLabel(state.selectedProject?.label),
        deviceName = deviceName, zoneLine = zoneLine,
        phase = if (checking) GeoStartPhase.CHECKING else if (valid) GeoStartPhase.PASSED else GeoStartPhase.FAILED,
        checks = checks, ticketLinked = draft?.ticketRowId != null || ticketTitle != null,
        ticketChanged = state.error == TicketChangedMessage, insideZone = zoneLine == "구역 안"
    )
}

private fun String?.isPassedCheck() = this?.uppercase() in setOf("PASSED", "READY", "AVAILABLE", "CURRENT", "ACTIVE")

internal fun SurveyRunUiState.startTargetForSheet(): StartTarget? = preflight?.let {
    policy?.let { policy -> StartTarget(preparationDraft ?: manualDraft(), it.preflightId, policy.revision) }
}
