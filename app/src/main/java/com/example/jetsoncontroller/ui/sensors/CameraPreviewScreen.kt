package com.example.jetsoncontroller.ui.sensors

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.jetsoncontroller.model.CameraSensorStatus
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.*
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Device-camera preview; capture always rechecks freshness and the pinned target. */
@Composable
fun CameraPreviewScreen(
    state: CameraPreviewUiState,
    camera: CameraSensorStatus,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    captureBusy: Boolean = false,
    captureMessage: String? = null,
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
        val currentStage = cameraStageState(state.targetConnected, state.frame != null,
            state.frameFreshness.receivedAt, SystemClock.elapsedRealtime(), state.error != null,
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
    val window = LocalGeoWindow.current
    val tablet = window.widthDp >= 600f
    val compactInfo = window.fontScale >= 1.5f
    val received = state.updatedAtEpochMillis?.let {
        "마지막 수신 ${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Seoul")).format(Instant.ofEpochMilli(it))}"
    }
    GeoImmersiveMediaFrame(
        captureAction = captureNow,
        captureEnabled = canCapture,
        info = GeoMediaInfoSpec(
            status = when (stage) {
                CameraStageState.LIVE -> "실시간"
                CameraStageState.DELAYED -> "지연"
                CameraStageState.WAITING -> "대기 중"
                CameraStageState.ERROR -> "받지 못함"
            },
            tone = when (stage) {
                CameraStageState.LIVE -> Tone.SUCCESS
                CameraStageState.DELAYED -> Tone.WARNING
                CameraStageState.ERROR -> Tone.ERROR
                CameraStageState.WAITING -> Tone.UNKNOWN
            },
            device = state.deviceName,
            resolution = cameraResolution(camera).takeUnless { compactInfo }
        ),
        caption = if (compactInfo) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    received?.let { Text(it, style = type.caption.copy(fontFeatureSettings = "tnum"), color = GeoFixedColors.mediaText) }
                    Text(cameraCaptureDestinationText(saveDevice, saveMobile), style = type.caption, color = GeoFixedColors.mediaText)
                    if (captureBusy) Text("캡처 중", style = type.caption, color = GeoFixedColors.mediaText)
                    if (stage == CameraStageState.DELAYED) Text("실시간 영상을 받으면 캡처할 수 있습니다.",
                        style = type.supporting, color = GeoFixedColors.mediaText)
                }
            }
        },
        state = if (stage == CameraStageState.ERROR) GeoStateContent(GeoStateKind.ERROR,
            "영상을 받지 못했습니다", cameraPreviewErrorExplanation(state.error)) else null,
        onRetry = if (stage == CameraStageState.ERROR) onRefresh else null,
        retryEnabled = !state.isRefreshing,
        loadingText = if (stage == CameraStageState.WAITING) "카메라 영상을 기다리고 있습니다" else null,
        controls = {
            GeoMediaIconButton(GeoIcons.back, "뒤로", onBack, Modifier.align(Alignment.TopStart))
            GeoMediaIconButton(GeoIcons.folder, "캡처 저장 위치", { chooseCapture = true },
                Modifier.align(if (tablet) Alignment.TopEnd else Alignment.BottomStart))
            GeoSnackbar(host, Modifier.align(Alignment.BottomCenter))
        }
    ) {
        state.frame?.let {
            Image(it.asImageBitmap(),
                if (stage == CameraStageState.LIVE) "장치 카메라 최근 수신 영상" else "장치 카메라 마지막 수신 영상",
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
    if (chooseCapture) GeoSheet("캡처 저장 위치", { chooseCapture = false },
        kind = GeoSheetKind.CHOICE,
        subtitle = listOfNotNull(state.deviceName, cameraResolution(camera)).joinToString(" · "),
        footer = {
            GeoSheetFooter(GeoOverlayAction("캡처", {
                chooseCapture = false
                captureNow()
            }, enabled = canCapture, disabledReason = if (!saveDevice && !saveMobile) "저장 위치를 선택하세요."
                else "실시간 영상을 받으면 캡처할 수 있습니다."), GeoOverlayAction("취소", { chooseCapture = false }))
        }) {
        if (compactInfo) {
            received?.let { Text(it, style = type.supporting, color = LocalGeoColors.current.textSecondary) }
            cameraResolution(camera)?.let { Text(it, style = type.supporting, color = LocalGeoColors.current.textSecondary) }
            Text(cameraCaptureDestinationText(saveDevice, saveMobile), style = type.supporting, color = LocalGeoColors.current.textSecondary)
            if (captureBusy) Text("캡처 중", style = type.supporting, color = LocalGeoColors.current.textSecondary)
            if (stage == CameraStageState.DELAYED) Text("실시간 영상을 받으면 캡처할 수 있습니다.",
                style = type.supporting, color = LocalGeoColors.current.textSecondary)
            Spacer(Modifier.height(12.dp))
        }
        GeoCheckboxRow("장치에 저장", saveDevice, { saveDevice = it; prefs.edit().putBoolean("device", it).apply() })
        GeoCheckboxRow("휴대폰 갤러리에 저장", saveMobile, { saveMobile = it; prefs.edit().putBoolean("mobile", it).apply() })
    }
}
