package com.example.jetsoncontroller.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.ui.components.MetricCard

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onDisconnect: () -> Unit,
    onStartSystem: () -> Unit,
    onStopSystem: () -> Unit,
    onRestartServices: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit
) {

    val deviceName =
        when (
            val connection =
                state.connectionState
        ) {

            is ConnectionState.Ready ->
                connection.deviceName

            is ConnectionState.Connected ->
                connection.deviceName

            is ConnectionState.Connecting ->
                connection.deviceName

            else ->
                "Jetson"
        }

    Scaffold(
        containerColor =
            MaterialTheme
                .colorScheme
                .background
    ) {
            paddingValues ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .padding(
                        horizontal = 22.dp
                    )
                    .verticalScroll(
                        rememberScrollState()
                    )
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = deviceName,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    if (
                        state.connectionState
                        is ConnectionState.Ready
                    )
                        "● 연결됨"
                    else
                        "연결 상태 확인 중",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = "시스템 상태",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                MetricCard(
                    title = "CPU",
                    value =
                        "${state.status.cpuPercent}%",
                    modifier =
                        Modifier.weight(1f)
                )

                MetricCard(
                    title = "GPU",
                    value =
                        "${state.status.gpuPercent}%",
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                MetricCard(
                    title = "TEMP",
                    value =
                        "${state.status.temperatureC}°C",
                    modifier =
                        Modifier.weight(1f)
                )

                MetricCard(
                    title = "STORAGE",
                    value =
                        "${state.status.storagePercent}%",
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = "서비스",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            ServiceRow(
                name = "Camera",
                running =
                    state.status
                        .cameraRunning
            )

            ServiceRow(
                name = "LiDAR",
                running =
                    state.status
                        .lidarRunning
            )

            ServiceRow(
                name = "GNSS",
                running =
                    state.status
                        .gnssRunning
            )

            ServiceRow(
                name = "MMS",
                running =
                    state.status
                        .mmsRunning
            )

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Button(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onStartSystem
            ) {

                Text("전체 시스템 시작")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onStopSystem
            ) {

                Text("전체 시스템 중지")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onRestartServices
            ) {

                Text("서비스 재시작")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        30.dp
                    )
            )

            HorizontalDivider()

            Spacer(
                modifier =
                    Modifier.height(
                        22.dp
                    )
            )

            Text(
                text = "장비 관리",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onReboot
            ) {
                Text("Jetson 재부팅")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onShutdown
            ) {
                Text("Jetson 종료")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onDisconnect
            ) {
                Text("연결 해제")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        40.dp
                    )
            )
        }
    }
}


@Composable
private fun ServiceRow(
    name: String,
    running: Boolean
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 10.dp
                ),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            text = name,
            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )

        Text(
            text =
                if (running)
                    "● Running"
                else
                    "○ Stopped",
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}
