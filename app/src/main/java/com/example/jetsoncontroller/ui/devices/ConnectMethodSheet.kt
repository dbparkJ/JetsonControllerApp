package com.example.jetsoncontroller.ui.devices

import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.jetsoncontroller.BluetoothPermissionPolicy
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.connection.DeviceConnectionUi
import com.example.jetsoncontroller.ui.model.localDeviceWord
import com.example.jetsoncontroller.ui.permissions.bluetoothPermissionText

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
    bluetoothError: String? = null,
    bluetoothProgress: String? = null
) {
    val deviceWord = localDeviceWord()
    var confirmDirect by remember { mutableStateOf(false) }
    GeoSheet(
        title = "$deviceName 연결 방법",
        subtitle = listOfNotNull(connection.label, lastSeenText?.let { "마지막 응답 $it" }).joinToString(" · "),
        onDismissRequest = onDismiss,
        kind = GeoSheetKind.CHOICE
    ) {
        GeoRowList(GeoRowTrailing.None) {
            DeviceConnectionChoice("같은 네트워크에서 다시 찾기", GeoIcons.wifi, onClick = onSameNetwork,
                meta = "${deviceWord}과 장치가 같은 Wi-Fi에 있을 때 씁니다. 모든 기능을 쓸 수 있습니다.")
            DeviceConnectionChoice("장치에 직접 연결", GeoIcons.link, onClick = { confirmDirect = true },
                meta = "공유기 없이 ${deviceWord}과 장치를 바로 연결합니다. 서버로 보내기는 같은 네트워크에서만 됩니다.")
            DeviceConnectionChoice("블루투스로 기본 연결", GeoIcons.bluetooth, onClick = {
                if (bluetoothPermissionGranted) onBluetooth?.invoke() else onRequestBluetoothPermission()
            }, meta = if (bluetoothBusy) bluetoothProgress ?: "기본 연결을 확인하고 있습니다." else if (bluetoothPermissionGranted) "가까운 장치를 찾아 기본 연결합니다."
                else bluetoothPermissionText(Build.VERSION.SDK_INT),
                enabled = onBluetooth != null && !bluetoothBusy,
                disabledReason = if (bluetoothBusy) bluetoothProgress ?: "기본 연결을 확인하고 있습니다." else "연결할 장치를 먼저 선택하세요.")
            bluetoothError?.let {
                GeoInlineNote(it, tone = com.example.jetsoncontroller.ui.model.Tone.ERROR,
                    kind = GeoNoteKind.LIVE_ERROR, actionLabel = "다시 시도",
                    onAction = if (!bluetoothBusy) onBluetooth else null)
            }
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R && bluetoothPermissionGranted) {
                LegacyBluetoothBackgroundNote()
            }
            DeviceConnectionChoice("QR 코드로 다시 등록", GeoIcons.qr, onClick = onQrRegister,
                meta = "인증 정보를 확인할 수 없을 때 씁니다.", divider = false)
        }
    }
    if (confirmDirect) GeoConfirmDialog(
        title = "${deviceName}에 직접 연결할까요?",
        body = "장치의 공유기 연결과 진행 중인 전송이 끊길 수 있습니다.",
        confirmLabel = "직접 연결",
        kind = GeoConfirmKind.DANGER,
        onConfirm = { confirmDirect = false; onDirectConnect() },
        onDismissRequest = { confirmDirect = false }
    )
}

@Composable
private fun LegacyBluetoothBackgroundNote() {
    val context = LocalContext.current
    fun canScanInBackground() = BluetoothPermissionPolicy.hasBackgroundScanPermissions(Build.VERSION.SDK_INT) {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember(context) { mutableStateOf(canScanInBackground()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = canScanInBackground() }
    if (!granted) GeoInlineNote(
        "화면을 닫은 뒤에도 블루투스로 장치를 찾으려면 앱 설정에서 위치 권한을 '항상 허용'으로 바꾸세요.",
        actionLabel = "권한 설정",
        onAction = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"))) }
    )
}
