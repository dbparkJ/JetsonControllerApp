package com.example.jetsoncontroller.ui.sensors

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.CameraSensorStatus
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.devices.DeviceTitleBar
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.GeoPalette
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Device-camera preview; no phone-camera permission or scanner is involved. */
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
    val prefs = LocalContext.current.let { context -> remember(context) {
        context.getSharedPreferences("camera_capture", Context.MODE_PRIVATE)
    } }
    var saveDevice by remember { mutableStateOf(prefs.getBoolean("device", true)) }
    var saveMobile by remember { mutableStateOf(prefs.getBoolean("mobile", false)) }
    var chooseCapture by remember(state.deviceId, state.targetConnected) { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis(), state.deviceId) {
        while (true) { value = System.currentTimeMillis(); delay(1_000) }
    }
    val stage = cameraStageState(telemetryFresh && (camera.active || state.targetConnected), state.frame != null,
        state.updatedAtEpochMillis, now, state.error != null, checkedAt = state.checkedAtEpochMillis)
    val canCapture = state.targetStillCurrent() && cameraCaptureEnabled(stage, captureBusy, saveDevice, saveMobile)
    val captureNow = {
        val currentStage = cameraStageState(telemetryFresh && (camera.active || state.targetConnected),
            state.frame != null, state.updatedAtEpochMillis, System.currentTimeMillis(), state.error != null)
        if (state.targetStillCurrent() && cameraCaptureEnabled(currentStage, captureBusy, saveDevice, saveMobile)) {
            onCapture(saveDevice, saveMobile)
        }
    }
    val host = remember { SnackbarHostState() }
    LaunchedEffect(captureMessage) {
        captureMessage?.let { host.showGeoSnackbar(it, withDismissAction = true); onDismissCaptureMessage(it) }
    }
    val type = LocalGeoType.current
    Column(Modifier.fillMaxSize()) {
        DeviceTitleBar("카메라", onBack)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            val compact = maxHeight < 360.dp
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    state.frame?.let {
                        Image(it.asImageBitmap(), if (stage == CameraStageState.LIVE) "장치 카메라 최근 수신 영상" else "장치 카메라 마지막 수신 영상",
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    if (stage == CameraStageState.WAITING || stage == CameraStageState.ERROR) {
                        Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (stage == CameraStageState.ERROR) "영상을 받지 못했습니다" else "카메라 영상을 기다리고 있습니다",
                                style = type.body, color = Color.White)
                            if (stage == CameraStageState.ERROR) CompositionLocalProvider(LocalGeoColors provides GeoPalette.GRAPHITE.colors) {
                                GeoButton("다시 시도", onRefresh, modifier = Modifier.widthIn(max = 240.dp),
                                    style = GeoButtonStyle.Secondary, enabled = !state.isRefreshing,
                                    busy = state.isRefreshing, busyLabel = "다시 시도 중")
                            }
                        }
                    }
                    Column(Modifier.align(Alignment.TopStart).padding(12.dp)
                        .background(Color.Black.copy(alpha = .72f)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val word = when (stage) {
                            CameraStageState.LIVE -> "실시간"
                            CameraStageState.DELAYED -> "지연"
                            CameraStageState.WAITING -> "대기 중"
                            CameraStageState.ERROR -> "받지 못함"
                        }
                        GeoBadge(word, when (stage) {
                            CameraStageState.LIVE -> Tone.SUCCESS
                            CameraStageState.DELAYED -> Tone.WARNING
                            CameraStageState.ERROR -> Tone.ERROR
                            CameraStageState.WAITING -> Tone.UNKNOWN
                        }, variant = GeoBadgeVariant.ON_DARK)
                        if (stage == CameraStageState.LIVE) cameraResolution(camera)?.let {
                            Text(it, style = type.caption, color = Color.White)
                        }
                        if (stage == CameraStageState.DELAYED) state.updatedAtEpochMillis?.let {
                            Text("마지막 수신 ${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Seoul")).format(Instant.ofEpochMilli(it))}",
                                style = type.caption, color = Color.White)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 24.dp, vertical = if (compact) 8.dp else 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape)
                        .clickable(enabled = canCapture, role = Role.Button) {
                            captureNow()
                        }.semantics { contentDescription = if (captureBusy) "캡처 중" else "캡처" }, contentAlignment = Alignment.Center) {
                        if (captureBusy) GeoBusyIndicator(Color.White)
                        else Box(Modifier.size(52.dp).background(if (canCapture) Color.White else Color(0xFFBDBFC4), CircleShape))
                    }
                    Box(Modifier.heightIn(min = 48.dp).widthIn(max = 180.dp)
                        .clickable(role = Role.Button) { chooseCapture = true }.padding(12.dp)) {
                        Text("저장 위치", style = type.button, color = Color.White)
                    }
                }
            }
            GeoSnackbar(host, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (chooseCapture) GeoSheet("캡처 저장 위치", { chooseCapture = false },
        subtitle = listOfNotNull(state.deviceName, cameraResolution(camera)).joinToString(" · "),
        footer = {
            GeoSheetFooter(GeoOverlayAction("캡처", {
                chooseCapture = false
                captureNow()
            }, enabled = canCapture, disabledReason = if (!saveDevice && !saveMobile) "저장 위치를 선택하세요."
                else "실시간 영상을 받으면 캡처할 수 있습니다."), GeoOverlayAction("취소", { chooseCapture = false }))
        }) {
        GeoCheckboxRow("장치에 저장", saveDevice, { saveDevice = it; prefs.edit().putBoolean("device", it).apply() })
        GeoCheckboxRow("휴대폰 갤러리에 저장", saveMobile, { saveMobile = it; prefs.edit().putBoolean("mobile", it).apply() })
    }
}
