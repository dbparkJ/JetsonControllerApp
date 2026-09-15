package com.example.jetsoncontroller.ui.sensors

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.CameraSensorStatus
import com.example.jetsoncontroller.ui.components.AppBanner
import com.example.jetsoncontroller.ui.components.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    state: CameraPreviewUiState,
    camera: CameraSensorStatus,
    telemetryFresh: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    captureBusy: Boolean = false, captureMessage: String? = null,
    onCapture: (Boolean, Boolean) -> Unit = { _, _ -> },
    onDismissCaptureMessage: (String) -> Unit = {}
) {
    val active = telemetryFresh && camera.active
    val live = cameraFrameIsLive(active, state.frame != null, state.updatedAtEpochMillis,
        state.checkedAtEpochMillis, state.error != null)
    var chooseCapture by remember { mutableStateOf(false) }
    var saveDevice by remember { mutableStateOf(true) }
    var saveMobile by remember { mutableStateOf(true) }
    if (chooseCapture) AlertDialog(onDismissRequest = { chooseCapture = false },
        title = { Text("캡처 저장 위치") }, text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(saveDevice, { saveDevice = it }); Text("장치에 저장") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(saveMobile, { saveMobile = it }); Text("모바일 갤러리에 저장") }
            }
        }, confirmButton = { TextButton(onClick = { chooseCapture = false; onCapture(saveDevice, saveMobile) }, enabled = (saveDevice || saveMobile) && live && !captureBusy) { Text("캡처") } },
        dismissButton = { TextButton(onClick = { chooseCapture = false }) { Text("취소") } })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카메라 프리뷰") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { chooseCapture = true }, enabled = live && !captureBusy) { Icon(Icons.Default.PhotoCamera, "캡처 저장") }
                    IconButton(onClick = onRefresh, enabled = active && !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            state.frame?.let { frame ->
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = if (live) "Jetson 카메라 최근 수신 영상" else "Jetson 카메라 마지막 수신 영상",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            captureMessage?.let { shown ->
                AppBanner(
                    message = shown,
                    tone = StatusTone.INFO,
                    onDismiss = { onDismissCaptureMessage(shown) },
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                )
            }
            if (captureBusy) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            val message = when {
                !active -> "카메라 센서 데이터 대기 중"
                state.error != null -> state.error
                else -> null
            }
            message?.let {
                Text(
                    it,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (live) "LIVE" else if (state.frame != null) "지연 · 마지막 수신" else "프레임 대기",
                        fontWeight = FontWeight.Bold,
                        color = if (live) com.example.jetsoncontroller.ui.theme.CobaltDark.success else com.example.jetsoncontroller.ui.theme.CobaltDark.warning
                    )
                    Text(
                        if (camera.frameWidth != null && camera.frameHeight != null) {
                            "${camera.frameWidth} × ${camera.frameHeight}"
                        } else {
                            "해상도 확인 중"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
