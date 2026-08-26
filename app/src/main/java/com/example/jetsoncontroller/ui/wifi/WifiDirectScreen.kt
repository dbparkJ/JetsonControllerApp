package com.example.jetsoncontroller.ui.wifi

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.network.WifiDirectApiStatus
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDirectScreen(
    state: WifiDirectState,
    permissionGranted: Boolean,
    onBack: () -> Unit,
    onPermissionClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onConnectClick: (WifiDirectPeer) -> Unit,
    onRetryApi: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Wi-Fi Direct")
                        Text(
                            "공유기 없이 Jetson 연결",
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
                    if (permissionGranted && !state.connected) {
                        IconButton(
                            onClick = onDiscoveryClick,
                            enabled = state.supported && !state.discovering
                        ) {
                            if (state.discovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "장비 다시 검색")
                            }
                        }
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
            when {
                !state.supported -> item {
                    EmptyState(
                        title = "Wi-Fi Direct를 지원하지 않습니다",
                        message = "이 Android 기기에서는 직접 연결을 사용할 수 없습니다."
                    )
                }

                !permissionGranted -> item {
                    EmptyState(
                        title = "주변 기기 권한이 필요합니다",
                        message = "가까운 Jetson을 검색하고 연결하기 위해 권한을 허용하세요.",
                        actionLabel = "권한 허용",
                        onAction = onPermissionClick
                    )
                }

                state.connected -> item {
                    ConnectedPanel(
                        state = state,
                        onRetryApi = onRetryApi,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }

                else -> {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            DirectConnectionSummary(state)
                            state.error?.let { error ->
                                Spacer(Modifier.height(12.dp))
                                InlineMessage(message = error, isError = true)
                            }
                            Spacer(Modifier.height(22.dp))
                            SectionHeader(
                                title = "주변 장비",
                                trailing = {
                                    Text(
                                        if (state.discovering) "검색 중" else "${state.peers.size}대",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            if (state.discovering) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    if (state.peers.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (state.discovering) {
                                    "Jetson을 찾고 있습니다"
                                } else {
                                    "검색된 장비가 없습니다"
                                },
                                message = "Jetson의 Wi-Fi Direct 서비스와 Android 위치 서비스를 확인하세요.",
                                actionLabel = if (state.discovering) null else "다시 검색",
                                onAction = if (state.discovering) null else onDiscoveryClick
                            )
                        }
                    } else {
                        items(state.peers, key = { it.deviceAddress }) { peer ->
                            PeerRow(
                                peer = peer,
                                connecting = state.connectingPeerAddress == peer.deviceAddress,
                                connectionInProgress = state.connectingPeerAddress != null,
                                onConnect = { onConnectClick(peer) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectConnectionSummary(state: WifiDirectState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.WifiTethering,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.enabled) "직접 연결 사용 가능" else "Wi-Fi Direct 확인 필요",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Android의 인터넷 연결을 유지하면서 Jetson 제어망을 만듭니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConnectedPanel(
    state: WifiDirectState,
    onRetryApi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Router, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.apiDeviceName ?: "Jetson 직접 연결",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.groupOwnerAddress ?: "장비 주소 확인 중",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            when (state.apiStatus) {
                WifiDirectApiStatus.IDLE,
                WifiDirectApiStatus.CHECKING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text("장비 인증과 API 연결을 확인하고 있습니다.")
                }

                WifiDirectApiStatus.READY -> {
                    Text(
                        "인증된 제어 API에 연결되었습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                WifiDirectApiStatus.ERROR -> {
                    Text(
                        text = state.apiError ?: "Jetson API에 연결하지 못했습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetryApi, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("API 다시 확인", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peer: WifiDirectPeer,
    connecting: Boolean,
    connectionInProgress: Boolean,
    onConnect: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                peer.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = { Text(peerStatusLabel(peer.status)) },
        leadingContent = {
            Icon(
                Icons.Default.Router,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(
            enabled = !connectionInProgress && peer.status != WifiP2pDevice.UNAVAILABLE,
            onClick = onConnect
        )
    )
}

private fun peerStatusLabel(status: Int): String = when (status) {
    WifiP2pDevice.CONNECTED -> "연결됨"
    WifiP2pDevice.INVITED -> "연결 승인 대기 중"
    WifiP2pDevice.AVAILABLE -> "연결 가능"
    WifiP2pDevice.FAILED -> "이전 연결 실패"
    WifiP2pDevice.UNAVAILABLE -> "현재 연결할 수 없음"
    else -> "상태 확인 중"
}
