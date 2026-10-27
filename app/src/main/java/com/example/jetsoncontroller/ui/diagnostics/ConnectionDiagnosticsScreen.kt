package com.example.jetsoncontroller.ui.diagnostics

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane
import com.example.jetsoncontroller.ui.dev.LocalDevOngoingStrip
import com.example.jetsoncontroller.ui.devices.DeviceSurface
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectionDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val health by ConnectionDiagnostics.health.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            exporting = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("Cannot open export destination")
                        output.use { ConnectionDiagnostics.export(it) }
                    }
                    message = "진단 ZIP을 저장했습니다. 끊긴 시각과 함께 앱 ZIP과 장치 로그를 전달해 주세요."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = "진단 ZIP을 저장하지 못했습니다. 저장 공간과 선택한 위치를 확인하고 다시 시도해 주세요."
                } finally { exporting = false }
            }
        }
    }
    val enabled = health.initialized && !exporting
    DiagnosticsPage(onBack,
        primaryAction = GeoButtonAction("진단 ZIP 저장",
            { exportLauncher.launch("jetson-app-diagnostics-${System.currentTimeMillis()}.zip") },
            enabled = enabled, busy = exporting, busyLabel = "진단 ZIP 저장 중",
            disabledReason = if (!health.initialized) "기록 기능을 시작하지 못했습니다." else "진단 ZIP 저장 중입니다."),
        secondaryAction = GeoButtonAction("지금 시점 표시", {
            ConnectionDiagnostics.record("user_marker", mapOf("requestId" to ConnectionDiagnostics.newId()), incident = true)
            ConnectionDiagnostics.recordPowerState(context)
            message = "시점 표시를 요청했습니다. 위의 직접 표시한 시각에서 저장 여부를 확인하세요."
        }, style = GeoButtonStyle.Secondary, enabled = enabled,
            disabledReason = if (!health.initialized) "기록 기능을 시작하지 못했습니다." else "진단 ZIP 저장 중입니다.")
    ) {
        GeoBadge(if (health.initialized) "실시간" else "기록 기능을 시작하지 못했습니다.",
            if (health.initialized) Tone.SUCCESS else Tone.ERROR, variant = GeoBadgeVariant.INLINE)
        Text("연결이 끊기면 이 화면에서 시점을 표시하고 기록을 저장하세요.",
            style = LocalGeoType.current.body, color = LocalGeoColors.current.text)
        GeoGroupedSection("기록 상태") {
            DeviceSurface(contentPadding = PaddingValues(horizontal = 16.dp)) {
                GeoPropertyList(listOf("마지막 저장", "장애 기록 보존", "직접 표시한 시각", "저장량", "저장 · 누락 · 쓰기 오류")) {
                    GeoPropertyRow("마지막 저장", diagnosticTime(health.lastWrittenUtcMs))
                    GeoPropertyRow("장애 기록 보존", diagnosticTime(health.lastIncidentUtcMs))
                    GeoPropertyRow("직접 표시한 시각", diagnosticTime(health.lastMarkerUtcMs))
                    GeoPropertyRow("저장량", diagnosticSize(health.storedBytes, ConnectionDiagnostics.MAX_BYTES))
                    GeoPropertyRow("저장 · 누락 · 쓰기 오류",
                        "${health.writtenRecords} · ${health.droppedRecords} · ${health.writeFailures}", numeric = true)
                }
                if (health.incidentCapped) GeoInlineNote("최근 장애 기록이 크기 한도에 도달해 일부 이후 이벤트가 생략됐습니다.",
                    tone = Tone.WARNING)
            }
        }
        GeoGroupedSection("기록 방식") {
            DeviceSurface(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        "앱이 실행되는 동안 연결, 요청, 복구 이벤트를 휴대폰에 기록합니다.",
                        "연결 실패가 보이면 직전 기록과 이후 최대 60초를 따로 보관합니다.",
                        "키, 인증 원문, 명령 내용, 좌표, IP 주소는 기록하지 않습니다.",
                        "인터넷으로 자동 전송하지 않습니다.",
                        "기본 기록 최대 ${Formats.size(4L * 1024 * 1024)}, 최근 장애 3건 각 최대 ${Formats.size(1024L * 1024)}를 보관합니다. 기록·내보내기 시 7일 지난 기록과 크기 한도를 넘는 오래된 기록을 정리합니다. 자동 장애 보존은 최소 2분 간격으로 시작합니다. 내보낸 ZIP은 직접 보관·삭제해야 합니다.",
                        "강제 종료·OS 회수·전원 꺼짐·저장 오류에서는 마지막 일부 기록이 없을 수 있습니다. 로그 공백만으로 실제 링크 끊김을 확정할 수 없습니다. Android 시스템 전체 로그는 포함하지 않습니다."
                    ).forEach { Text(it, style = LocalGeoType.current.body, color = LocalGeoColors.current.text) }
                }
            }
        }
        message?.let { GeoInlineNote(it) }
        Text("장애가 표시되면 가능할 때 약 60초 뒤 ZIP을 저장하세요. 앱이 종료될 것 같으면 바로 저장해도 됩니다. 연결이 없어도 이 화면을 열 수 있습니다.",
            style = LocalGeoType.current.supporting, color = LocalGeoColors.current.textSecondary)
    }
}

@Composable
private fun DiagnosticsPage(
    onBack: () -> Unit,
    primaryAction: GeoButtonAction,
    secondaryAction: GeoButtonAction,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    GeoLargeTitleScaffold("연결 진단 기록", onBack = onBack,
        primaryAction = primaryAction, secondaryAction = secondaryAction) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LocalDevOngoingStrip.current()
            Column(Modifier.padding(horizontal = if (LocalGeoPane.current) 0.dp else 24.dp)
                .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
