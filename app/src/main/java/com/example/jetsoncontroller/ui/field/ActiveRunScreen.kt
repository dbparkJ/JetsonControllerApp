package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.ConnectionStepper
import com.example.jetsoncontroller.ui.components.DeviceContextHeader
import com.example.jetsoncontroller.ui.components.GeoBottomActionBar
import com.example.jetsoncontroller.ui.components.GeoDangerAction
import com.example.jetsoncontroller.ui.components.GeoDataRow
import com.example.jetsoncontroller.ui.components.GeoFreshnessLabel
import com.example.jetsoncontroller.ui.components.GeoIdentifier
import com.example.jetsoncontroller.ui.components.GeoRowDivider
import com.example.jetsoncontroller.ui.components.GeoSection
import com.example.jetsoncontroller.ui.components.GeoSectionHeader
import com.example.jetsoncontroller.ui.components.GeoSecondaryAction
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.GeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton

/**
 * 수집 중 — 이번 재편에서 새로 생긴 화면입니다.
 *
 * 이전 앱에는 이 화면이 없었습니다. 라우트 33개 어디에도 '진행 중인 수집' 이 없어서,
 * 시작을 누른 직원은 작업 목록으로 되돌아가 목록 항목의 상태 글자를 읽어야 했습니다.
 * 조사 중에 확인해야 할 유일한 질문 — "지금 이게 실제로 돌고 있나" — 에 답하는 화면이
 * 없었던 셈입니다.
 *
 * 설계 규칙 세 가지:
 *
 *  - 여기 보이는 값은 전부 장치가 보고한 값이고, 언제 받은 값인지 함께 적습니다.
 *    프리뷰가 움직인다는 사실은 원본이 저장되고 있다는 증거가 아닙니다.
 *  - 일시정지 버튼은 두지 않습니다. 현재 API가 일시정지를 지원하는지 확인되지 않았고,
 *    지원하지 않는 동작을 화면에 만들면 사용자는 앱을 신뢰하지 않게 됩니다.
 *  - 중지는 확인 단계를 거치고, 확인창은 "정말 실행할까요?" 가 아니라 어느 장치의 어떤
 *    실행이 끝나는지를 적습니다.
 */
@Composable
internal fun ActiveRunScreen(
    deviceName: String,
    connectionLabel: String,
    connectionTone: StatusTone,
    run: PipelineRun?,
    /** 장비가 준 경과 시간. 앱이 임의로 계산하지 않습니다. */
    elapsedLabel: String?,
    /** 마지막으로 장치 응답을 받은 시각. */
    lastObservedLabel: String?,
    sensorSummary: String?,
    storageSummary: String?,
    stopInProgress: Boolean,
    online: Boolean,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onCamera: () -> Unit,
    onMap: () -> Unit
) {
    val c = LocalGeoColors.current
    var confirmStop by remember(run?.runId) { mutableStateOf(false) }

    if (confirmStop && run != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = c.surface,
            title = { Text("이 수집을 중지할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                    Text("장치: $deviceName")
                    Text("조사: ${run.contextSnapshot.surveyProjectLabel} · ${run.contextSnapshot.surveySectionLabel}")
                    GeoIdentifier("Run", run.runId)
                    Text(
                        "중지하면 이 실행이 끝납니다. 명령이 접수된 것과 실행이 실제로 종료된 것은 다르므로, " +
                            "종료 확인까지 기다린 뒤 저장 결과를 확인하게 됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; onStop() }) { Text("수집 중지") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("계속 수집") } }
        )
    }

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            DeviceContextHeader(
                title = "수집 중",
                deviceName = deviceName,
                connectionLabel = connectionLabel,
                connectionTone = connectionTone,
                onDevices = onDevices,
                unreadCount = unreadCount,
                onAlerts = onAlerts
            )
        },
        bottomBar = {
            GeoBottomActionBar(
                readiness = when {
                    stopInProgress -> "중지 명령을 보냈습니다. 실행이 실제로 끝났는지 확인하는 중입니다."
                    !online -> "장치 연결이 끊겨 중지 명령을 보낼 수 없습니다. 수집은 계속되고 있을 수 있습니다."
                    else -> "중지하면 이 실행이 끝나고 저장 결과 확인 단계로 넘어갑니다."
                },
                readinessTone = when {
                    stopInProgress -> StatusTone.PENDING
                    !online -> StatusTone.UNKNOWN
                    else -> StatusTone.INFO
                }
            ) {
                GeoDangerAction(
                    label = if (stopInProgress) "중지 요청 처리 중" else "수집 중지",
                    target = "대상: $deviceName · ${run?.contextSnapshot?.surveySectionLabel ?: "현재 실행"}",
                    onClick = { confirmStop = true },
                    enabled = online && !stopInProgress && run != null
                )
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = GeoSpace.gutter, end = GeoSpace.gutter,
                top = GeoSpace.md, bottom = GeoSpace.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.lg)
        ) {
            item {
                ConnectionStepper(
                    labels = listOf("연결", "준비", "점검", "수집", "결과"),
                    currentStep = 3
                )
            }

            if (run == null) {
                item {
                    AppBanner(
                        "이 장치에서 진행 중인 실행을 찾지 못했습니다. 연결이 끊긴 사이에 종료됐을 수도 있고, " +
                            "아직 목록을 받지 못했을 수도 있습니다.",
                        StatusTone.UNKNOWN,
                        actionLabel = "실행 상태 다시 조회",
                        onAction = onRefresh
                    )
                }
            }

            if (!online) {
                item {
                    AppBanner(
                        "장치 연결이 끊겼습니다. 마지막으로 확인한 상태를 표시하고 있으며, " +
                            "Jetson의 수집은 계속되고 있을 수 있습니다.",
                        StatusTone.UNKNOWN,
                        actionLabel = "다시 연결",
                        onAction = onDevices
                    )
                }
            }

            if (stopInProgress) {
                item {
                    AppBanner(
                        "중지 명령이 접수됐습니다. 실행이 실제로 끝났는지는 아직 확인되지 않았습니다.",
                        StatusTone.PENDING
                    )
                }
            }

            // ---- 경과 ---------------------------------------------------------
            item {
                GeoSection(tone = if (stopInProgress) StatusTone.PENDING else StatusTone.SUCCESS) {
                    GeoSectionHeader(
                        title = run?.let {
                            "${it.contextSnapshot.surveyProjectLabel} · ${it.contextSnapshot.surveySectionLabel}"
                        } ?: "실행 정보 미확인",
                        eyebrow = "현재 실행",
                        trailing = {
                            StatusBadge(
                                if (stopInProgress) "중지 요청 중" else run?.state ?: "미확인",
                                if (stopInProgress) StatusTone.PENDING
                                else if (run == null) StatusTone.UNKNOWN else StatusTone.SUCCESS
                            )
                        }
                    )
                    Text(
                        elapsedLabel ?: "경과 시간 미제공",
                        style = if (elapsedLabel != null) GeoType.numericLarge else MaterialTheme.typography.titleMedium,
                        color = if (elapsedLabel != null) c.ink else c.unknown
                    )
                    run?.startedAt?.let {
                        Text("시작 $it", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    lastObservedLabel?.let {
                        GeoFreshnessLabel("마지막 확인 · $it", stale = !online)
                    }
                }
            }

            // ---- 실행에 고정된 범위 ----------------------------------------------
            if (run != null) {
                item {
                    GeoSection {
                        GeoSectionHeader(
                            title = "실행에 고정된 범위",
                            eyebrow = "끝날 때까지 바꿀 수 없습니다"
                        )
                        GeoIdentifier("Run", run.runId)
                        GeoIdentifier("결과 경로", "${run.output.rootId}/${run.output.path}")
                        GeoIdentifier("정책", "v${run.policySnapshot.policyVersion} · ${run.policySnapshot.revision}")
                        GeoIdentifier("점검", run.preflightSnapshot.preflightId)
                    }
                }
            }

            // ---- 장비가 보고한 값 ------------------------------------------------
            item {
                GeoSection {
                    GeoSectionHeader(
                        title = "장비가 보고한 값",
                        eyebrow = "앱이 계산한 값이 아닙니다",
                        trailing = { TextButton(onClick = onRefresh) { Text("새로고침") } }
                    )
                    GeoDataRow(
                        label = "센서",
                        supporting = "카메라 · GNSS · IMU",
                        value = sensorSummary ?: "",
                        valueUnavailable = sensorSummary == null
                    )
                    GeoRowDivider()
                    GeoDataRow(
                        label = "장치 저장",
                        supporting = "이 실행이 쓰고 있는 용량",
                        value = storageSummary ?: "",
                        valueUnavailable = storageSummary == null
                    )
                }
            }

            // ---- 현장 확인 -------------------------------------------------------
            item {
                GeoSection {
                    GeoSectionHeader(
                        title = "현장 확인",
                        eyebrow = "보이는 것과 저장되는 것은 다릅니다"
                    )
                    Text(
                        "프리뷰가 움직인다는 사실은 원본이 녹화되고 있다는 증거가 아닙니다. " +
                            "저장 여부는 종료 후 결과 요약에서 확인합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)
                    ) {
                        GeoSecondaryAction("카메라 프리뷰", onCamera, enabled = online)
                        GeoSecondaryAction("지도에서 보기", onMap, enabled = online)
                    }
                }
            }
        }
    }
}
