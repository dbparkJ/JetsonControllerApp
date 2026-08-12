package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.components.ConnectionStatusCard
import com.example.jetsoncontroller.ui.components.DeviceCard

@Composable
fun DeviceListScreen(
    state: DeviceListUiState,
    onScanClick: () -> Unit,
    onConnect: (JetsonDevice) -> Unit,
    onReconnect: (RegisteredDevice) -> Unit,
    onAddDeviceClick: () -> Unit,
    onBack: () -> Unit
) {

    Scaffold(
        containerColor =
            MaterialTheme
                .colorScheme
                .background
    ) { paddingValues ->

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
        ) {

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로"
                    )
                }

                Text(
                    text = "Jetson Control",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    "Bluetooth를 통해 주변 Jetson 장비에 연결하세요.",
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
                    Modifier.height(22.dp)
            )

            ConnectionStatusCard(
                isScanning =
                    state.isScanning,
                connectionState =
                    state.connectionState
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            Button(
                onClick = onAddDeviceClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (state.connectionState is ConnectionState.RegistrationRequired) {
                        "현재 연결된 장비 QR 인증"
                    } else {
                        "QR 코드로 장비 추가"
                    }
                )
            }

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            if (state.registeredDevices.isNotEmpty()) {
                Text(
                    text = "등록된 장비",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.registeredDevices,
                        key = { it.deviceId }
                    ) { device ->
                        RegisteredDeviceCard(
                            device = device,
                            reconnecting =
                                state.reconnectingDeviceId == device.deviceId,
                            enabled =
                                state.permissionGranted &&
                                    state.reconnectingDeviceId == null,
                            onReconnect = { onReconnect(device) },
                            modifier = Modifier.width(320.dp)
                        )
                    }
                }

                state.reconnectError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = "주변 장비",
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Text(
                        text =
                            "${state.devices.size}개의 이름 있는 BLE 장비",
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

                FilledTonalButton(
                    onClick =
                        onScanClick,
                    enabled =
                        state.permissionGranted
                ) {

                    if (
                        state.isScanning
                    ) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(
                                    18.dp
                                ),
                            strokeWidth =
                                2.dp
                        )

                        Spacer(
                            modifier =
                                Modifier.size(
                                    8.dp
                                )
                        )

                        Text("중지")

                    } else {

                        Text("검색")
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            if (
                !state.permissionGranted
            ) {

                PermissionMessage(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                )

            } else if (
                state.devices.isEmpty()
            ) {

                EmptyDeviceView(
                    scanning =
                        state.isScanning,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                )

            } else {

                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        ),
                    contentPadding =
                        PaddingValues(
                            bottom = 32.dp
                        )
                ) {

                    items(
                        items =
                            state.devices,
                        key = {
                            it.address
                        }
                    ) {
                            device ->

                        DeviceCard(
                            device =
                                device,
                            onConnect = {
                                onConnect(
                                    device
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun RegisteredDeviceCard(
    device: RegisteredDevice,
    reconnecting: Boolean,
    enabled: Boolean,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "QR 인증 정보 저장됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onReconnect,
                enabled = enabled
            ) {
                if (reconnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("검색 중")
                } else {
                    Text("다시 연결")
                }
            }
        }
    }
}


@Composable
private fun EmptyDeviceView(
    scanning: Boolean,
    modifier: Modifier = Modifier
) {

    Box(
        modifier =
            modifier,
        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Surface(
                modifier =
                    Modifier.size(72.dp),
                shape =
                    CircleShape,
                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceContainerHighest
            ) {

                Box(
                    contentAlignment =
                        Alignment.Center
                ) {

                    if (scanning) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(
                                    28.dp
                                ),
                            strokeWidth =
                                3.dp
                        )

                    } else {

                        Text(
                            text = "BLE",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelLarge,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Text(
                text =
                    if (scanning)
                        "주변 장비를 찾고 있습니다"
                    else
                        "검색된 장비가 없습니다",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    if (scanning)
                        "이름이 확인된 BLE 장비만 표시합니다."
                    else
                        "장비가 켜져 있고 BLE 광고 중인지 확인하세요.",
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


@Composable
private fun PermissionMessage(
    modifier: Modifier = Modifier
) {

    Box(
        modifier =
            modifier,
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                "주변 기기 권한을 허용해야 Jetson을 검색할 수 있습니다.",
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
