package com.example.jetsoncontroller.ui.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.dev.*
import com.example.jetsoncontroller.ui.model.Formats
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
    DevPage("연결 진단 기록", onBack, footer = {
        GeoButtonActions(listOf(
            GeoButtonAction("지금 시점 표시", {
                ConnectionDiagnostics.record("user_marker", mapOf("requestId" to ConnectionDiagnostics.newId()), incident = true)
                ConnectionDiagnostics.recordPowerState(context)
                message = "시점 표시를 요청했습니다. 위의 직접 표시한 시각에서 저장 여부를 확인하세요."
            }, style = GeoButtonStyle.Secondary, enabled = health.initialized && !exporting),
            GeoButtonAction("진단 ZIP 저장", { exportLauncher.launch("jetson-app-diagnostics-${System.currentTimeMillis()}.zip") },
                enabled = health.initialized && !exporting, busy = exporting, busyLabel = "진단 ZIP 저장 중")
        ))
    }) {
        DevText(if (health.initialized) "실시간" else "기록 기능을 시작하지 못했습니다.")
        DevText("연결이 끊기면 이 화면에서 시점을 표시하고 기록을 저장하세요.")
        GeoSection("기록 상태", first = true, modifier = Modifier.padding(top = 24.dp)) {
            GeoFactRow("마지막 저장", diagnosticTime(health.lastWrittenUtcMs))
            GeoFactRow("장애 기록 보존", diagnosticTime(health.lastIncidentUtcMs))
            GeoFactRow("직접 표시한 시각", diagnosticTime(health.lastMarkerUtcMs))
            GeoFactRow("저장량", diagnosticSize(health.storedBytes, ConnectionDiagnostics.MAX_BYTES))
            GeoFactRow("저장 · 누락 · 쓰기 오류", "${health.writtenRecords} · ${health.droppedRecords} · ${health.writeFailures}", numeric = true)
            if (health.incidentCapped) DevFeedback("최근 장애 기록이 크기 한도에 도달해 일부 이후 이벤트가 생략됐습니다.", true)
        }
        GeoSection("기록 방식") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DevText("앱이 실행되는 동안 연결, 요청, 복구 이벤트를 휴대폰에 기록합니다.")
                DevText("연결 실패가 보이면 직전 기록과 이후 최대 60초를 따로 보관합니다.")
                DevText("키, 인증 원문, 명령 내용, 좌표, IP 주소는 기록하지 않습니다.")
                DevText("인터넷으로 자동 전송하지 않습니다.")
                DevText("기본 기록 최대 ${Formats.size(4L * 1024 * 1024)}, 최근 장애 3건 각 최대 ${Formats.size(1024L * 1024)}를 보관합니다. 기록·내보내기 시 7일 지난 기록과 크기 한도를 넘는 오래된 기록을 정리합니다. 자동 장애 보존은 최소 2분 간격으로 시작합니다. 내보낸 ZIP은 직접 보관·삭제해야 합니다.")
                DevText("강제 종료·OS 회수·전원 꺼짐·저장 오류에서는 마지막 일부 기록이 없을 수 있습니다. 로그 공백만으로 실제 링크 끊김을 확정할 수 없습니다. Android 시스템 전체 로그는 포함하지 않습니다.")
            }
        }
        DevFeedback(message)
        Spacer(Modifier.height(16.dp))
        DevText("장애가 표시되면 가능할 때 약 60초 뒤 ZIP을 저장하세요. 앱이 종료될 것 같으면 바로 저장해도 됩니다. 연결이 없어도 이 화면을 열 수 있습니다.", secondary = true)
    }
}
