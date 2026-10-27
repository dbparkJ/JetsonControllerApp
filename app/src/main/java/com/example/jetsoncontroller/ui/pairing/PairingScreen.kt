package com.example.jetsoncontroller.ui.pairing

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.localDeviceWord
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.permissions.bluetoothPermissionText
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

internal fun pairingErrorRecoveryAction(
    hasPairingInfo: Boolean,
    onRetry: () -> Unit,
    onScanAgain: () -> Unit
): GeoOverlayAction =
    if (hasPairingInfo) GeoOverlayAction("다시 시도", onRetry)
    else GeoOverlayAction("다시 스캔", onScanAgain)

@Composable
fun PairingScreen(
    state: PairingUiState,
    onStartPairing: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val deviceWord = localDeviceWord()
    FrozenPairingScanner(onBack = onCancel)
    GeoSheet(
        title = "장치 등록",
        onDismissRequest = onCancel,
        kind = GeoSheetKind.FORM,
        footer = {
            when (state.phase) {
                PairingPhase.QR_SCANNED -> GeoSheetFooter(
                    GeoOverlayAction("등록", onStartPairing, enabled = state.pairingInfo != null,
                        disabledReason = "QR 코드로 다시 등록하세요."),
                    GeoOverlayAction("다시 스캔", onCancel)
                )
                PairingPhase.ERROR -> GeoSheetFooter(
                    pairingErrorRecoveryAction(state.pairingInfo != null, onRetry, onCancel),
                    GeoOverlayAction("취소", onCancel)
                )
                PairingPhase.READY -> Unit
                else -> GeoButton("취소", onCancel, Modifier.fillMaxWidth(), style = GeoButtonStyle.Secondary)
            }
        }
    ) {
        val colors = LocalGeoColors.current
        val type = LocalGeoType.current
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.geo_device), null, Modifier.size(40.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.displayDeviceName ?: "장치", style = type.deviceName, color = colors.text)
                Text(maskedPairingCode(state.pairingInfo), style = type.caption, color = colors.textSecondary)
            }
        }
        GeoGroupedSection("등록 점검", trailingColumn = GeoRowTrailing.Status) {
            GeoRow("등록 코드", {}, meta = maskedPairingCode(state.pairingInfo), navigable = false,
                leading = { GeoIconTile(GeoIcons.qr) },
                badge = { GeoBadge(if (state.pairingInfo != null) "확인됨" else "미확인",
                    if (state.pairingInfo != null) Tone.SUCCESS else Tone.UNKNOWN) })
            GeoRow("장치 등록", {}, navigable = false, divider = false,
                meta = if (state.phase == PairingPhase.ERROR) null
                    else pairingProgressText(state.phase, state.connectionAttempt),
                leading = { GeoIconTile(GeoIcons.bluetooth) },
                badge = { GeoBadge(when (state.phase) {
                    PairingPhase.READY -> "등록됨"
                    PairingPhase.ERROR -> "실패"
                    PairingPhase.QR_SCANNED -> "대기 중"
                    else -> "진행 중"
                }, when (state.phase) {
                    PairingPhase.READY -> Tone.SUCCESS
                    PairingPhase.ERROR -> Tone.ERROR
                    else -> Tone.PENDING
                }) })
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.phase) {
                PairingPhase.QR_SCANNED -> {
                    Text("블루투스로 장치를 찾아 이 ${deviceWord}에 등록합니다.", style = type.body, color = colors.text)
                    Text(bluetoothPermissionText(Build.VERSION.SDK_INT), style = type.body, color = colors.textSecondary)
                }
                PairingPhase.ERROR -> {
                    GeoStateBlock(GeoStateContent(GeoStateKind.ERROR, "등록하지 못했습니다", pairingFailureText(state.errorMessage)))
                }
                else -> {
                    Text(pairingProgressText(state.phase, state.connectionAttempt), style = type.body, color = colors.text)
                    if (state.phase != PairingPhase.READY) GeoProgress()
                }
            }
        }
    }
}
