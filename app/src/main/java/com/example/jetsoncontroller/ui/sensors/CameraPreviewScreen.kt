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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.CameraSensorStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    state: CameraPreviewUiState,
    camera: CameraSensorStatus,
    telemetryFresh: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val active = telemetryFresh && camera.active
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
                    contentDescription = "Jetson 카메라 실시간 영상",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            val message = when {
                !active -> "카메라 센서 데이터 대기 중"
                state.error != null && state.frame == null -> state.error
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
                        if (active) "LIVE" else "OFFLINE",
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color(0xFF61D095) else Color(0xFFFF8A80)
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
