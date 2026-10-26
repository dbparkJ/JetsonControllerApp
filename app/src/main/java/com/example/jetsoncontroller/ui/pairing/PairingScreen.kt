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
import com.example.jetsoncontroller.ui.permissions.bluetoothPermissionText
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

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
                    GeoOverlayAction("다시 시도", onRetry, enabled = state.pairingInfo != null,
                        disabledReason = "QR 코드로 다시 등록하세요."),
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
            Image(painterResource(R.drawable.geo_device), null, Modifier.size(72.dp, 64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.displayDeviceName ?: "장치", style = type.deviceName, color = colors.text)
                Text(maskedPairingCode(state.pairingInfo), style = type.caption, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.phase) {
                PairingPhase.QR_SCANNED -> {
                    Text("블루투스로 장치를 찾아 이 ${deviceWord}에 등록합니다.", style = type.body, color = colors.text)
                    Text(bluetoothPermissionText(Build.VERSION.SDK_INT), style = type.body, color = colors.textSecondary)
                }
                PairingPhase.ERROR -> {
                    GeoStateBlock(GeoStateContent(GeoStateKind.ERROR, "등록하지 못했습니다", pairingFailureText(state.errorMessage)))
                    GeoButton("다시 스캔", onCancel, style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.Start)
                }
                else -> {
                    Text(pairingProgressText(state.phase, state.connectionAttempt), style = type.body, color = colors.text)
                    if (state.phase != PairingPhase.READY) GeoProgress()
                }
            }
        }
    }
}
