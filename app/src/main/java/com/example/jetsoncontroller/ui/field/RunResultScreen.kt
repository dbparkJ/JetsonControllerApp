package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.jetsoncontroller.model.PipelineOutputManifest
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.ConnectionStepper
import com.example.jetsoncontroller.ui.components.DeviceContextHeader
import com.example.jetsoncontroller.ui.components.GeoBottomActionBar
import com.example.jetsoncontroller.ui.components.GeoDataRow
import com.example.jetsoncontroller.ui.components.GeoIdentifier
import com.example.jetsoncontroller.ui.components.GeoPrimaryAction
import com.example.jetsoncontroller.ui.components.GeoRowDivider
import com.example.jetsoncontroller.ui.components.GeoSection
import com.example.jetsoncontroller.ui.components.GeoSectionHeader
import com.example.jetsoncontroller.ui.components.GeoStateBlock
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.OutlinedButton

/**
 * 결과 요약 — 이번 재편에서 새로 생긴 두 번째 화면입니다.
 *
 * 이전에는 수집이 끝나면 실행 이력 목록으로 돌아갔고, "이번 조사가 제대로 남았나" 라는
 * 질문에 답하는 화면이 없었습니다. 목록의 상태 글자 하나로 실행 종료·저장 확인·서버 수신
 * 세 가지가 뭉뚱그려졌습니다.
 *
 * 이 화면의 전부는 그 셋을 갈라 놓는 것입니다:
 *
 *   실행 종료   프로세스가 끝났는가          ← 장치가 보고
 *   장치 저장   파일이 실제로 남았는가       ← manifest 조회 결과
 *   서버 수신   서버가 실제로 받았는가       ← 전송·검증 결과
 *
 * 하나가 확인됐다고 다음이 확인된 것이 아니며, 세 칸에 각각의 상태가 들어갑니다.
 * 기대 조건 검사를 통과했다는 것도 '조사 품질이 보증됐다' 는 뜻이 아니므로 그렇게 적습니다.
 */
@Composable
internal fun RunResultScreen(
    deviceName: String,
    connectionLabel: String,
    connectionTone: StatusTone,
    run: PipelineRun?,
    /** 서버 전송을 시작할 수 있는 연결 상태인가. 현재 제품은 LAN에서만 허용합니다. */
    uploadEnabled: Boolean,
    uploadDisabledReason: String,
    /**
     * 서버 수신 결과. `null` 은 "전송하지 않음" 이 아니라 **앱이 아직 모른다** 입니다.
     * 이 둘을 같은 문구로 뭉뚱그리면, 전송이 실패한 자료를 보내지 않은 자료로 착각하게 됩니다.
     */
    serverReceiptLabel: String?,
    online: Boolean,
    unreadCount: Int,
    onDevices: () -> Unit,
    onAlerts: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrepareUpload: () -> Unit,
    onOpenFiles: () -> Unit,
    onNewSurvey: () -> Unit
) {
    val c = LocalGeoColors.current
    val manifest = run?.output?.manifest
    val manifestState = run?.output?.manifestState.orEmpty().uppercase()

    // 실행 종료 · 장치 저장 · 서버 수신 — 각각 독립적으로 판정한다.
    val finishedTone = when {
        run == null -> StatusTone.UNKNOWN
        run.finishedAt == null -> StatusTone.PENDING
        run.exitCode != null && run.exitCode != 0 -> StatusTone.WARNING
        else -> StatusTone.SUCCESS
    }
    val storedTone = when (manifestState) {
        "READY", "COMPLETE", "VERIFIED" -> StatusTone.SUCCESS
        "PENDING", "RUNNING" -> StatusTone.PENDING
        "FAILED", "ERROR" -> StatusTone.WARNING
        else -> StatusTone.UNKNOWN
    }
    val serverTone = if (serverReceiptLabel == null) StatusTone.UNKNOWN else StatusTone.SUCCESS

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            DeviceContextHeader(
                title = "결과 요약",
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
                    run == null -> "확인할 실행을 찾지 못했습니다."
                    storedTone == StatusTone.PENDING -> "저장 결과를 확인하는 중입니다. 확인이 끝나기 전에는 전송을 시작하지 않습니다."
                    !uploadEnabled -> uploadDisabledReason
                    else -> "원본은 장치에 있고 서버에는 아직 없습니다."
                },
                readinessTone = when {
                    run == null -> StatusTone.UNKNOWN
                    storedTone == StatusTone.PENDING -> StatusTone.PENDING
                    !uploadEnabled -> StatusTone.WARNING
                    else -> StatusTone.INFO
                }
            ) {
                GeoPrimaryAction(
                    label = if (uploadEnabled) "전송 준비" else "전송 조건 확인",
                    onClick = onPrepareUpload,
                    enabled = run != null && storedTone != StatusTone.PENDING
                )
                OutlinedButton(
                    onClick = onNewSurvey,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = GeoSize.secondaryAction)
                ) { Text("새 구간 조사 시작") }
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
                    currentStep = 4
                )
            }

            if (run == null) {
                item {
                    GeoStateBlock(
                        title = "확인할 실행을 찾지 못했습니다",
                        impact = "이 장치에서 최근 종료된 실행을 받지 못했습니다. 실행 이력에서 직접 찾을 수 있습니다.",
                        tone = StatusTone.UNKNOWN,
                        primaryLabel = "다시 조회",
                        onPrimary = onRefresh,
                        secondaryLabel = "실행 이력 열기",
                        onSecondary = onBack
                    )
                }
                return@LazyColumn
            }

            // ---- 세 가지 사실 ----------------------------------------------------
            item {
                GeoSection {
                    GeoSectionHeader(
                        title = "${run.contextSnapshot.surveyProjectLabel} · ${run.contextSnapshot.surveySectionLabel}",
                        eyebrow = "이 셋은 서로 다른 사실입니다"
                    )
                    FactRow(
                        "실행 종료",
                        when (finishedTone) {
                            StatusTone.SUCCESS -> run.finishedAt.orEmpty().ifBlank { "확인됨" }
                            StatusTone.PENDING -> "종료 확인 중"
                            StatusTone.WARNING -> "비정상 종료 · exit ${run.exitCode}"
                            else -> "미확인"
                        },
                        finishedTone
                    )
                    GeoRowDivider()
                    FactRow(
                        "장치 저장",
                        when (storedTone) {
                            StatusTone.SUCCESS -> manifest?.let {
                                "파일 ${it.fileCount}개 · ${formatBytes(it.bytesTotal)}"
                            } ?: "확인됨"
                            StatusTone.PENDING -> "확인 중"
                            StatusTone.WARNING -> "저장 확인 실패"
                            else -> "미확인"
                        },
                        storedTone
                    )
                    GeoRowDivider()
                    FactRow(
                        "서버 수신",
                        serverReceiptLabel ?: "",
                        serverTone
                    )
                }
            }

            // ---- 기대 조건 검사 ---------------------------------------------------
            item {
                if (manifest == null) {
                    GeoSection(tone = StatusTone.UNKNOWN) {
                        GeoSectionHeader(title = "기대 조건 검사", eyebrow = "결과 없음")
                        Text(
                            "장치에서 결과 목록(manifest)을 아직 받지 못했습니다. 파일이 없다는 뜻이 아니라, " +
                                "무엇이 남았는지 앱이 모른다는 뜻입니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.muted
                        )
                    }
                } else {
                    ExpectationSection(manifest)
                }
            }

            // ---- 저장 위치 -------------------------------------------------------
            item {
                GeoSection {
                    GeoSectionHeader(
                        title = "저장 위치",
                        eyebrow = "장치 안의 실제 경로",
                        trailing = { OutlinedButton(onClick = onOpenFiles, shape = MaterialTheme.shapes.small) { Text("파일 열기") } }
                    )
                    GeoIdentifier("경로", "${run.output.rootId}/${run.output.path}")
                    GeoIdentifier("Run", run.runId)
                    GeoIdentifier("Output", run.output.outputId)
                    if (manifest?.truncated == true) {
                        AppBanner(
                            "결과 목록이 잘렸습니다. 표시된 파일 수와 용량은 전체가 아닐 수 있습니다.",
                            StatusTone.WARNING
                        )
                    }
                }
            }

            if (!online) {
                item {
                    AppBanner(
                        "장치 연결이 끊겼습니다. 표시된 결과는 마지막으로 확인한 값입니다.",
                        StatusTone.UNKNOWN,
                        actionLabel = "다시 연결",
                        onAction = onDevices
                    )
                }
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, tone: StatusTone) {
    GeoDataRow(
        label = label,
        value = value,
        valueUnavailable = tone == StatusTone.UNKNOWN,
        trailing = {
            StatusBadge(
                when (tone) {
                    StatusTone.SUCCESS -> "확인됨"
                    StatusTone.PENDING -> "처리 중"
                    StatusTone.WARNING -> "확인 필요"
                    StatusTone.ERROR -> "실패"
                    StatusTone.INFO -> "해당 없음"
                    StatusTone.UNKNOWN -> "미확인"
                },
                tone
            )
        }
    )
}

@Composable
private fun ExpectationSection(manifest: PipelineOutputManifest) {
    val c = LocalGeoColors.current
    val state = manifest.expectationState.uppercase()
    val tone = when (state) {
        "PASSED", "MET" -> StatusTone.SUCCESS
        "FAILED", "UNMET" -> StatusTone.WARNING
        "PENDING" -> StatusTone.PENDING
        else -> StatusTone.UNKNOWN
    }
    val expected = manifest.expected

    GeoSection(tone = tone) {
        GeoSectionHeader(
            title = "기대 조건 검사",
            eyebrow = "관리자가 정한 정책과 대조",
            trailing = {
                StatusBadge(
                    when (tone) {
                        StatusTone.SUCCESS -> "통과"
                        StatusTone.WARNING -> "미충족"
                        StatusTone.PENDING -> "검사 중"
                        else -> "미확인"
                    },
                    tone
                )
            }
        )
        if (expected.minFiles > 0) {
            GeoDataRow(
                label = "최소 파일 수",
                supporting = "기준 ${expected.minFiles}개",
                value = "${manifest.fileCount}개"
            )
        }
        if (expected.minBytes > 0) {
            GeoRowDivider()
            GeoDataRow(
                label = "최소 용량",
                supporting = "기준 ${formatBytes(expected.minBytes)}",
                value = formatBytes(manifest.bytesTotal)
            )
        }
        manifest.matchedPatterns.forEach { match ->
            GeoRowDivider()
            GeoDataRow(
                label = match.pattern,
                supporting = formatBytes(match.bytesTotal),
                value = "${match.fileCount}개"
            )
        }
        Text(
            "검사 통과는 정책이 정한 하한을 넘었다는 뜻이며, 조사 품질이 보증됐다는 뜻이 아닙니다.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted
        )
    }
}

/** 용량은 값이 없을 때 0으로 표시하지 않고 호출부에서 '미확인' 으로 갈라 놓습니다. */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
