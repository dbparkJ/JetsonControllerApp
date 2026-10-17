package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.connection.DeviceConnectionUi
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton

/**
 * How to connect one device. The same sheet opens from the device root and from the device
 * detail, so the choices never differ between the two places.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onSaveDiagnostics: () -> Unit
) {
    val c = LocalGeoColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val summary = listOfNotNull(connection.label, lastSeenText?.let { "마지막 응답 $it" })
        .joinToString(" · ")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        contentColor = c.ink
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = GeoSpace.gutter)
                .padding(bottom = GeoSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
        ) {
            Text("$deviceName 연결 방법", style = MaterialTheme.typography.titleLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = c.muted)
            ConnectOption(
                title = "같은 네트워크에서 다시 찾기",
                description = "휴대폰과 장치가 같은 Wi-Fi에 있을 때 씁니다. 모든 기능과 파일 전송을 쓸 수 있습니다.",
                emphasized = true,
                onClick = onSameNetwork
            )
            ConnectOption(
                title = "장치에 직접 연결",
                description = "공유기가 없는 현장에서 씁니다. 수집과 상태 확인은 되지만, 서버로 파일 보내기는 같은 네트워크에서만 됩니다.",
                emphasized = false,
                onClick = onDirectConnect
            )
            ConnectOption(
                title = "QR 코드로 다시 등록",
                description = "장치를 다시 설치했거나 인증이 맞지 않는다고 나올 때 씁니다.",
                emphasized = false,
                onClick = onQrRegister
            )
            if (developerModeEnabled) {
                TextButton(onClick = onSaveDiagnostics) {
                    Text("연결 진단 기록 저장")
                }
            }
        }
    }
}

@Composable
private fun ConnectOption(
    title: String,
    description: String,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val c = LocalGeoColors.current
    Surface(
        onClick = onClick,
        color = if (emphasized) c.brandSoft else c.sectionSoft,
        contentColor = c.ink,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(GeoSpace.lg),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
    }
}
