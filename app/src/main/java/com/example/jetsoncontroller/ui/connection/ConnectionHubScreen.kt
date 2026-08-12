package com.example.jetsoncontroller.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.DeviceEndpoint

@Composable
fun ConnectionHubScreen(
    onBleClick: () -> Unit,
    onQrClick: () -> Unit,
    onWifiDirectClick: () -> Unit,
    lanEndpoints: List<DeviceEndpoint>,
    registeredDeviceIds: Set<String>,
    lanDiscovering: Boolean,
    lanError: String?,
    connectingLanDeviceId: String?,
    localNetworkPermissionGranted: Boolean,
    onRequestLocalNetworkPermission: () -> Unit,
    onRefreshLan: () -> Unit,
    onConnectLan: (DeviceEndpoint) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Jetson Control",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "장비에 연결",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            ConnectionOptionCard(
                title = "Bluetooth로 연결",
                description = "주변 Jetson을 직접 검색합니다.",
                icon = Icons.Default.Bluetooth,
                buttonText = "연결",
                onClick = onBleClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            ConnectionOptionCard(
                title = "QR 코드로 연결",
                description = "본체 QR로 안전하게 등록합니다.",
                icon = Icons.Default.QrCodeScanner,
                buttonText = "스캔",
                onClick = onQrClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            ConnectionOptionCard(
                title = "Wi-Fi Direct로 연결",
                description = "공유기 없이 고속으로 연결합니다.",
                icon = Icons.Default.Wifi,
                buttonText = "연결",
                onClick = onWifiDirectClick
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "같은 네트워크의 장비",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Jetson이 광고한 LAN API를 검색합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onRefreshLan,
                    enabled = localNetworkPermissionGranted && !lanDiscovering
                ) {
                    if (lanDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("새로고침")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!localNetworkPermissionGranted) {
                Button(
                    onClick = onRequestLocalNetworkPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("로컬 네트워크 권한 허용")
                }
            } else if (lanEndpoints.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = lanError
                            ?: if (lanDiscovering) {
                                "같은 네트워크에서 Jetson을 찾고 있습니다."
                            } else {
                                "검색된 장비가 없습니다. Jetson의 mDNS 광고를 확인하세요."
                            },
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (lanError == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            } else {
                lanEndpoints.forEach { endpoint ->
                    val registered = registeredDeviceIds.any {
                        it.equals(endpoint.deviceId, ignoreCase = true)
                    }
                    LanDeviceCard(
                        endpoint = endpoint,
                        registered = registered,
                        connecting = connectingLanDeviceId == endpoint.deviceId,
                        onConnect = { onConnectLan(endpoint) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                lanError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LanDeviceCard(
    endpoint: DeviceEndpoint,
    registered: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = endpoint.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${endpoint.host}:${endpoint.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (registered) "등록된 장비" else "BLE/QR 등록 필요",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (registered) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Button(
                onClick = onConnect,
                enabled = registered && !connecting
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("연결")
                }
            }
        }
    }
}

@Composable
private fun ConnectionOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    buttonText: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.size(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text(buttonText)
            }
        }
    }
}
