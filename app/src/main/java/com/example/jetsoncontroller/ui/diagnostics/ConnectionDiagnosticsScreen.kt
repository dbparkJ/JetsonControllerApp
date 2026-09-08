package com.example.jetsoncontroller.ui.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
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
                    message = "진단 ZIP을 저장했습니다. 끊긴 시각과 함께 앱 ZIP과 Jetson 로그를 전달해 주세요."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = "진단 ZIP을 저장하지 못했습니다. 저장 공간과 선택한 위치를 확인하고 다시 시도해 주세요."
                } finally { exporting = false }
            }
        }
    }
    fun time(value: Long?): String = value?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it))
    } ?: "아직 없음"

    Scaffold(topBar = {
        TopAppBar(title = { Text("연결 진단 기록") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("연결이 끊기면 이 화면에서 시점을 표시하고 기록을 저장해 주세요.", style = MaterialTheme.typography.titleMedium)
            Text("앱 실행 중 연결·요청·복구 이벤트를 휴대폰 내부에 자동 기록합니다. 연결 실패가 관측되면 직전 기록과 이후 최대 60초를 별도 보존합니다. 인터넷으로 자동 전송하지 않습니다.")
            Text(if (health.initialized) "기록 기능 켜짐" else "기록 기능을 시작하지 못했습니다.")
            Text("마지막 저장: ${time(health.lastWrittenUtcMs)}\n마지막 장애 보존 시작: ${time(health.lastIncidentUtcMs)}\n이번 실행에서 직접 표시한 시각: ${time(health.lastMarkerUtcMs)}\n저장량: ${health.storedBytes / 1024} KiB / 최대 7 MiB\n이번 실행의 저장 ${health.writtenRecords}건 · 누락 ${health.droppedRecords}건 · 쓰기 오류 ${health.writeFailures}건")
            if (health.incidentCapped) {
                Text("최근 장애 기록이 크기 한도에 도달해 일부 이후 이벤트가 생략됐습니다.", color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = {
                ConnectionDiagnostics.record("user_marker", mapOf("requestId" to ConnectionDiagnostics.newId()), incident = true)
                ConnectionDiagnostics.recordPowerState(context)
                message = "시점 표시를 요청했습니다. 위의 직접 표시한 시각에서 저장 여부를 확인하세요."
            }, modifier = Modifier.fillMaxWidth(), enabled = health.initialized && !exporting) {
                Text("지금 끊김 시점 표시")
            }
            OutlinedButton(onClick = {
                exportLauncher.launch("jetson-app-diagnostics-${System.currentTimeMillis()}.zip")
            }, modifier = Modifier.fillMaxWidth(), enabled = health.initialized && !exporting) {
                Text(if (exporting) "진단 ZIP 저장 중…" else "앱 진단 ZIP 저장")
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("장애가 표시되면 가능할 때 약 60초 뒤 ZIP을 저장해 주세요. 앱이 종료될 것 같으면 바로 저장해도 됩니다. 연결이 없어도 이 화면을 열 수 있습니다.")
            Text("기본 기록 최대 4 MiB, 최근 장애 3건 각 최대 1 MiB를 보관합니다. 기록·내보내기 시 7일 지난 기록과 크기 한도를 넘는 오래된 기록을 정리합니다. 자동 장애 보존은 최소 2분 간격으로 시작하며 연속 실패는 기본 기록에도 남습니다. 내보낸 ZIP은 직접 보관·삭제해야 합니다.", style = MaterialTheme.typography.bodySmall)
            Text("키·인증 원문·명령 내용·좌표·IP/MAC 원문은 기록하지 않습니다. 강제 종료·OS 회수·전원 꺼짐·저장 오류에서는 마지막 일부 기록이 없을 수 있습니다. 로그 공백만으로 실제 링크 끊김을 확정할 수 없습니다. Android 시스템 전체 로그는 포함하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
