package com.example.jetsoncontroller.ui.sensors

import android.content.Context
import android.os.SystemClock
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.jetsoncontroller.ui.theme.GeoFixedColors
import com.example.jetsoncontroller.ui.theme.GeoShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Device-camera preview; no phone-camera permission or scanner is involved. */
@Composable
fun CameraPreviewScreen(
    state: CameraPreviewUiState,
    camera: CameraSensorStatus,
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
    val stage = cameraStageState(state.targetConnected, state.frame != null,
        state.frameFreshness.receivedAt, state.checkedAtElapsedMillis, state.error != null,
        freshness = state.frameFreshness)
    val canCapture = state.targetStillCurrent() && cameraCaptureEnabled(stage, captureBusy, saveDevice, saveMobile)
    val captureNow = {
        val currentStage = cameraStageState(state.targetConnected,
            state.frame != null, state.frameFreshness.receivedAt, SystemClock.elapsedRealtime(), state.error != null,
            freshness = state.frameFreshness)
        if (state.targetStillCurrent() && cameraCaptureEnabled(currentStage, captureBusy, saveDevice, saveMobile)) {
            onCapture(saveDevice, saveMobile)
        }
    }
    val host = remember { SnackbarHostState() }
    LaunchedEffect(captureMessage) {
        captureMessage?.let { host.showGeoSnackbar(it, withDismissAction = true); onDismissCaptureMessage(it) }
    }
    val type = LocalGeoType.current
    val media = GeoPalette.GRAPHITE.colors.copy(
        canvas = GeoFixedColors.mediaStage, surface = GeoFixedColors.mediaStage,
        topBar = GeoFixedColors.mediaStage, onTopBar = GeoFixedColors.mediaText,
        onTopBarIcon = GeoFixedColors.mediaText
    )
    CompositionLocalProvider(LocalGeoColors provides media) {
        Column(Modifier.fillMaxSize().background(GeoFixedColors.mediaStage)) {
            DeviceTitleBar("카메라", onBack)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val compact = maxHeight < 360.dp
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Text(state.deviceName ?: "장치", Modifier.weight(1f), style = type.deviceName, color = GeoFixedColors.mediaText)
                            GeoBadge(when (stage) {
                                CameraStageState.LIVE -> "실시간"
                                CameraStageState.DELAYED -> "지연"
                                CameraStageState.WAITING -> "대기 중"
                                CameraStageState.ERROR -> "받지 못함"
                            }, when (stage) {
                                CameraStageState.LIVE -> Tone.SUCCESS
                                CameraStageState.DELAYED -> Tone.WARNING
                                CameraStageState.ERROR -> Tone.ERROR
                                CameraStageState.WAITING -> Tone.UNKNOWN
                            }, variant = GeoBadgeVariant.ON_DARK)
                        }
                        val received = state.updatedAtEpochMillis?.let {
                            "마지막 수신 ${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Seoul")).format(Instant.ofEpochMilli(it))}"
                        }
                        val metadata = listOfNotNull(received, cameraResolution(camera)).joinToString(" · ")
                        if (metadata.isNotEmpty()) Text(metadata, style = type.caption, color = GeoFixedColors.mediaText)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        state.frame?.let {
                            val description = if (stage == CameraStageState.LIVE) "장치 카메라 최근 수신 영상" else "장치 카메라 마지막 수신 영상"
                            Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            Row(Modifier.align(Alignment.TopStart).padding(16.dp)
                                .background(GeoFixedColors.mediaBadge, GeoShape.badge).padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(GeoIcons.camera, null, Modifier.size(20.dp), tint = GeoFixedColors.mediaText)
                                Text("카메라 영상", style = type.caption, color = GeoFixedColors.mediaText)
                            }
                        }
                        if (stage == CameraStageState.WAITING || stage == CameraStageState.ERROR) {
                            Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(if (stage == CameraStageState.ERROR) "영상을 받지 못했습니다" else "카메라 영상을 기다리고 있습니다",
                                    style = type.body, color = GeoFixedColors.mediaText)
                                if (stage == CameraStageState.ERROR) GeoButton("다시 시도", onRefresh,
                                    modifier = Modifier.widthIn(max = 240.dp), style = GeoButtonStyle.Secondary,
                                    enabled = !state.isRefreshing, busy = state.isRefreshing, busyLabel = "다시 시도 중")
                            }
                        }
                    }
                    if (stage == CameraStageState.DELAYED) Text(
                        "카메라 영상이 늦게 도착하고 있습니다. 실시간 영상을 받으면 캡처할 수 있습니다.",
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        style = type.supporting, color = GeoFixedColors.mediaText)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 8.dp else 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            GeoButton("저장 위치", { chooseCapture = true }, style = GeoButtonStyle.Tertiary,
                                contentColor = GeoFixedColors.mediaText, edge = GeoButtonEdge.Start)
                        }
                        Box(Modifier.size(72.dp).clip(CircleShape).border(2.dp, GeoFixedColors.mediaPending, CircleShape)
                            .clickable(enabled = canCapture, role = Role.Button) { captureNow() }
                            .semantics { contentDescription = if (captureBusy) "캡처 중" else "캡처" }, contentAlignment = Alignment.Center) {
                            if (captureBusy) GeoBusyIndicator(GeoFixedColors.mediaText)
                            else Box(Modifier.size(56.dp).background(
                                if (canCapture) GeoFixedColors.mediaText else GeoFixedColors.mediaPending, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Icon(GeoIcons.camera, null, Modifier.size(28.dp), tint = GeoFixedColors.mediaStage)
                            }
                        }
                        Text(listOfNotNull("장치에 저장".takeIf { saveDevice }, "휴대폰 갤러리에 저장".takeIf { saveMobile })
                            .joinToString(" · ").ifEmpty { "저장 위치를 선택하세요." },
                            Modifier.weight(1f), style = type.caption, color = GeoFixedColors.mediaPending)
                    }
                }
                GeoSnackbar(host, Modifier.align(Alignment.BottomCenter))
            }
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
