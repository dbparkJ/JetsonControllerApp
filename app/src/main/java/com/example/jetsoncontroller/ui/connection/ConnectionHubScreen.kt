package com.example.jetsoncontroller.ui.connection

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Jetson Controller")
                        Text(
                            "장비 연결",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onQrClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "QR로 장비 등록")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                SectionHeader(
                    title = "사용 가능한 장비",
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 8.dp),
                    trailing = {
                        IconButton(
                            onClick = onRefreshLan,
                            enabled = localNetworkPermissionGranted && !lanDiscovering
                        ) {
                            if (lanDiscovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "LAN 장비 다시 검색")
                            }
                        }
                    }
                )
                Text(
                    text = "등록된 Jetson을 현재 네트워크에서 자동으로 찾습니다.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }

            if (!localNetworkPermissionGranted) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineMessage(
                            message = "LAN 장비 검색을 위해 로컬 네트워크 권한이 필요합니다.",
                            isError = false
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onRequestLocalNetworkPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("권한 허용")
                        }
                    }
                }
            } else if (lanEndpoints.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lanError ?: if (lanDiscovering) {
                                    "LAN에서 Jetson을 검색하고 있습니다."
                                } else {
                                    "현재 네트워크에서 검색된 장비가 없습니다."
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (lanError == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            } else {
                items(lanEndpoints, key = { it.deviceId }) { endpoint ->
                    val registered = registeredDeviceIds.any {
                        it.equals(endpoint.deviceId, ignoreCase = true)
                    }
                    LanDeviceCard(
                        endpoint = endpoint,
                        registered = registered,
                        connecting = connectingLanDeviceId == endpoint.deviceId,
                        onConnect = { onConnectLan(endpoint) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                    )
                }
                lanError?.let { error ->
                    item {
                        InlineMessage(
                            message = error,
                            isError = true,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(26.dp))
                SectionHeader(
                    title = "다른 연결 방법",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                ConnectionMethod(
                    icon = Icons.Default.QrCodeScanner,
                    title = "새 장비 등록",
                    description = "본체 QR로 인증 정보를 안전하게 저장",
                    onClick = onQrClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 20.dp))
                ConnectionMethod(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth",
                    description = "주변 Jetson 검색, 인증 및 기본 제어",
                    onClick = onBleClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 20.dp))
                ConnectionMethod(
                    icon = Icons.Default.WifiTethering,
                    title = "Wi-Fi Direct",
                    description = "공유기 없이 저장소와 로컬 API에 연결",
                    onClick = onWifiDirectClick
                )
            }
        }
    }
}

@Composable
private fun LanDeviceCard(
    endpoint: DeviceEndpoint,
    registered: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Router, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    endpoint.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${endpoint.host}:${endpoint.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (registered) "등록됨" else "QR 등록 필요",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (registered) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            OutlinedButton(
                onClick = onConnect,
                enabled = registered && !connecting,
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                if (connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("연결")
                }
            }
        }
    }
}

@Composable
private fun ConnectionMethod(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
