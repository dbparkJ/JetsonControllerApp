package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.components.ConnectionStatusCard
import com.example.jetsoncontroller.ui.components.DeviceCard
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    state: DeviceListUiState,
    onScanClick: () -> Unit,
    onConnect: (JetsonDevice) -> Unit,
    onReconnect: (RegisteredDevice) -> Unit,
    onForget: (RegisteredDevice) -> Unit,
    onAddDeviceClick: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bluetooth")
                        Text(
                            "Jetson 장비 검색",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onScanClick,
                        enabled = state.permissionGranted
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "BLE 장비 검색")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ConnectionStatusCard(
                    isScanning = state.isScanning,
                    connectionState = state.connectionState
                )
            }

            item {
                Button(
                    onClick = onAddDeviceClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        text = if (state.connectionState is ConnectionState.RegistrationRequired) {
                            "현재 장비 QR 인증"
                        } else {
                            "QR로 새 장비 등록"
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (!state.permissionGranted) {
                item {
                    InlineMessage(
                        message = "주변 기기 권한을 허용해야 Bluetooth 장비를 검색할 수 있습니다.",
                        isError = false
                    )
                }
            }

            if (state.registeredDevices.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("등록된 장비")
                }
                items(
                    items = state.registeredDevices,
                    key = { "registered-${it.deviceId}" }
                ) { device ->
                    RegisteredDeviceRow(
                        device = device,
                        reconnecting = state.reconnectingDeviceId == device.deviceId,
                        enabled = state.permissionGranted && state.reconnectingDeviceId == null,
                        onReconnect = { onReconnect(device) },
                        onForget = { onForget(device) }
                    )
                }
            }

            state.reconnectError?.let { error ->
                item {
                    InlineMessage(message = error, isError = true)
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                SectionHeader(
                    title = "주변 장비",
                    trailing = {
                        Text(
                            "${state.devices.size}개",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            if (state.permissionGranted && state.devices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (state.isScanning) "주변 장비를 찾고 있습니다" else "검색된 장비가 없습니다",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Jetson의 Bluetooth 광고 상태를 확인하세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!state.isScanning) {
                            FilledTonalButton(onClick = onScanClick) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Text("다시 검색", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            } else if (state.permissionGranted) {
                items(
                    items = state.devices,
                    key = { "nearby-${it.address}" }
                ) { device ->
                    DeviceCard(device = device, onConnect = { onConnect(device) })
                }
            }
        }
    }
}

@Composable
private fun RegisteredDeviceRow(
    device: RegisteredDevice,
    reconnecting: Boolean,
    enabled: Boolean,
    onReconnect: () -> Unit,
    onForget: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "QR 인증 정보 저장됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onReconnect,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                if (reconnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("연결")
                }
            }
            IconButton(onClick = onForget, enabled = !reconnecting) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "저장된 장비 삭제")
            }
        }
    }
}
