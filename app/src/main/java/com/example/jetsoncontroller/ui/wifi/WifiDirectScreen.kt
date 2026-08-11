package com.example.jetsoncontroller.ui.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectApiStatus
import com.example.jetsoncontroller.data.network.WifiDirectState

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
                title = { Text("Wi-Fi Direct") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 22.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Wi-Fi Direct",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "공유기 없이 Jetson과 직접 연결합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            when {
                !permissionGranted -> {
                    PermissionView(onPermissionClick)
                }

                state.connected -> {
                    ConnectedView(
                        state = state,
                        onRetryApi = onRetryApi
                    )
                }

                else -> {
                    DiscoveryView(
                        state = state,
                        onDiscoveryClick = onDiscoveryClick,
                        onConnectClick = onConnectClick
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionView(
    onPermissionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "주변 기기 권한이 필요합니다",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "가까이 있는 Jetson을 검색하고 직접 연결하기 위해 권한을 허용해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onPermissionClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("주변 기기 권한 허용")
        }
    }
}

@Composable
private fun ConnectedView(
    state: WifiDirectState,
    onRetryApi: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "연결됨",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "그룹 소유자 IP: ${state.groupOwnerAddress ?: "확인 중"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (state.apiStatus) {
                WifiDirectApiStatus.IDLE,
                WifiDirectApiStatus.CHECKING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Jetson API :8765 확인 중")
                    }
                }

                WifiDirectApiStatus.READY -> {
                    Text(
                        text = "API 연결됨 · ${state.apiDeviceName ?: "Jetson"} · :8765",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                WifiDirectApiStatus.ERROR -> {
                    Text(
                        text = state.apiError ?: "Jetson API에 연결하지 못했습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetryApi) {
                        Text("API 다시 확인")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryView(
    state: WifiDirectState,
    onDiscoveryClick: () -> Unit,
    onConnectClick: (WifiDirectPeer) -> Unit
) {
    if (state.error != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = state.error,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "검색된 장비",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        TextButton(
            onClick = onDiscoveryClick,
            enabled = !state.discovering && state.supported
        ) {
            if (state.discovering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("검색 중...")
            } else {
                Text("다시 검색")
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (state.peers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.discovering) {
                    "주변 Wi-Fi Direct 장비를 찾고 있습니다."
                } else if (state.discoveryAttempted) {
                    "Android Wi-Fi P2P API에서 검색된 장비가 0개입니다.\n" +
                        "Jetson에서 Wi-Fi Direct 대기(p2p_listen/p2p_find)와 권한을 확인해 주세요."
                } else {
                    "검색된 장비가 없습니다.\nJetson의 Wi-Fi Direct 대기 상태를 확인해 주세요."
                },
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                items = state.peers,
                key = { it.deviceAddress }
            ) { peer ->
                PeerCard(
                    peer = peer,
                    connecting = state.connectingPeerAddress == peer.deviceAddress,
                    connectionInProgress = state.connectingPeerAddress != null,
                    onConnect = onConnectClick
                )
            }
        }
    }
}

@Composable
private fun PeerCard(
    peer: WifiDirectPeer,
    connecting: Boolean,
    connectionInProgress: Boolean,
    onConnect: (WifiDirectPeer) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = peer.deviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { onConnect(peer) },
                enabled = !connectionInProgress
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("연결 중")
                } else {
                    Text("연결")
                }
            }
        }
    }
}
