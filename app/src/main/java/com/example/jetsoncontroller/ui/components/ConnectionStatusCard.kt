package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ConnectionState

@Composable
fun ConnectionStatusCard(
    isScanning: Boolean,
    connectionState:
        ConnectionState
) {

    val title =
        when {

            isScanning ->
                "주변 장비 검색 중"

            connectionState
                is ConnectionState.Ready ->
                "Jetson 연결됨"

            connectionState
                is ConnectionState.Connecting ->
                "Jetson 연결 중"

            connectionState
                is ConnectionState.RegistrationRequired ->
                "장비 인증 필요"

            else ->
                "Bluetooth 준비됨"
        }

    val detail =
        when (
            connectionState
        ) {

            ConnectionState.Disconnected ->
                if (isScanning)
                    "BLE 광고 패킷을 검색하고 있습니다."
                else
                    "주변 Jetson 장비를 검색할 수 있습니다."

            is ConnectionState.Connecting ->
                connectionState.deviceName

            is ConnectionState.Connected ->
                "${connectionState.deviceName} · 서비스 검색 중"

            is ConnectionState.Ready ->
                "${connectionState.deviceName} · 제어 준비 완료"

            is ConnectionState.RegistrationRequired ->
                "${connectionState.deviceName} · QR을 스캔해 등록을 완료하세요."

            is ConnectionState.Error ->
                connectionState.message
        }

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            MaterialTheme.shapes.large,
        color =
            MaterialTheme
                .colorScheme
                .surfaceContainerLow,
        tonalElevation = 1.dp
    ) {

        Row(
            modifier =
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .background(
                            color =
                                when {
                                    connectionState
                                        is ConnectionState.Ready ->
                                        MaterialTheme
                                            .colorScheme
                                            .primary

                                    isScanning ->
                                        MaterialTheme
                                            .colorScheme
                                            .tertiary

                                    else ->
                                        MaterialTheme
                                            .colorScheme
                                            .outline
                                },
                            shape =
                                CircleShape
                        )
            )

            Spacer(
                modifier =
                    Modifier.size(12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = title,
                    style =
                        MaterialTheme
                            .typography
                            .titleSmall,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    text = detail,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}
