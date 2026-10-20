package com.example.jetsoncontroller.ui.devices

import androidx.compose.runtime.*
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.connection.DeviceConnectionUi

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun ConnectMethodSheet(
    deviceName: String,
    connection: DeviceConnectionUi,
    lastSeenText: String?,
    developerModeEnabled: Boolean,
    onDismiss: () -> Unit,
    onSameNetwork: () -> Unit,
    onDirectConnect: () -> Unit,
    onQrRegister: () -> Unit,
    onSaveDiagnostics: () -> Unit,
    onBluetooth: (() -> Unit)? = null,
    bluetoothPermissionGranted: Boolean = false,
    onRequestBluetoothPermission: () -> Unit = {},
    bluetoothBusy: Boolean = false,
    bluetoothError: String? = null
) {
    var confirmDirect by remember { mutableStateOf(false) }
    GeoSheet(
        title = "$deviceName 연결 방법",
        subtitle = listOfNotNull(connection.label, lastSeenText?.let { "마지막 응답 $it" }).joinToString(" · "),
        onDismissRequest = onDismiss
    ) {
        DeviceConnectionChoice("같은 네트워크에서 다시 찾기", GeoIcons.wifi, onClick = onSameNetwork,
            meta = "휴대폰과 장치가 같은 Wi-Fi에 있을 때 씁니다. 모든 기능을 쓸 수 있습니다.")
        DeviceConnectionChoice("장치에 직접 연결", GeoIcons.link, onClick = { confirmDirect = true },
            meta = "공유기 없이 휴대폰과 장치를 바로 연결합니다. 서버로 보내기는 같은 네트워크에서만 됩니다.")
        DeviceConnectionChoice("블루투스로 기본 연결", GeoIcons.bluetooth, onClick = {
            if (bluetoothPermissionGranted) onBluetooth?.invoke() else onRequestBluetoothPermission()
        }, meta = if (bluetoothBusy) "기본 연결을 확인하고 있습니다." else if (bluetoothPermissionGranted) "가까운 장치를 찾아 기본 연결합니다."
            else "가까운 장치를 찾으려면 블루투스 권한이 필요합니다.",
            enabled = onBluetooth != null && !bluetoothBusy,
            disabledReason = if (bluetoothBusy) "기본 연결을 확인하고 있습니다." else "연결할 장치를 먼저 선택하세요.")
        bluetoothError?.let {
            GeoInlineNote(it, tone = com.example.jetsoncontroller.ui.model.Tone.ERROR,
                kind = GeoNoteKind.LIVE_ERROR, actionLabel = "다시 시도",
                onAction = if (!bluetoothBusy) onBluetooth else null)
        }
        DeviceConnectionChoice("QR 코드로 다시 등록", GeoIcons.qr, onClick = onQrRegister, meta = "인증 정보를 확인할 수 없을 때 씁니다.")
    }
    if (confirmDirect) GeoConfirmDialog(
        title = "${deviceName}에 직접 연결할까요?",
        body = "장치의 공유기 연결과 진행 중인 전송이 끊길 수 있습니다.",
        confirmLabel = "직접 연결",
        onConfirm = { confirmDirect = false; onDirectConnect() },
        onDismissRequest = { confirmDirect = false }
    )
}
