package com.example.jetsoncontroller.ui.wifi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.pairing.PairingTopBar
import com.example.jetsoncontroller.ui.devices.DeviceSurface
import com.example.jetsoncontroller.ui.model.localDeviceWord
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Composable
fun WifiDirectScreen(
    state: WifiDirectState,
    permissionGranted: Boolean,
    onBack: () -> Unit,
    onPermissionClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    onConnectClick: (WifiDirectPeer) -> Unit,
    onRetryApi: () -> Unit,
    onCancel: () -> Unit = {}
) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val deviceWord = localDeviceWord()
    val gutter = LocalGeoWindow.current.gutterDp.dp
    var pendingAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var opener by remember { mutableStateOf<FocusRequester?>(null) }
    var cancelling by rememberSaveable { mutableStateOf(false) }
    val stage = directStage(state, permissionGranted, cancelling)
    LaunchedEffect(state.cleaningUp, state.connectingPeerAddress, state.connected) {
        if (!state.cleaningUp && state.connectingPeerAddress == null && !state.connected) cancelling = false
    }
    LaunchedEffect(stage, state.peers, pendingAddress) {
        if (stage !in setOf(DirectStage.PEERS, DirectStage.DISCOVERING) ||
            state.peers.none { it.deviceAddress == pendingAddress }) pendingAddress = null
    }
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        PairingTopBar("직접 연결", onBack)
        LazyColumn(contentPadding = PaddingValues(start = gutter, end = gutter, top = 24.dp, bottom = 28.dp)) {
            if (stage != DirectStage.UNSUPPORTED) item {
                GeoInlineNote("공유기 없이 ${deviceWord}과 장치를 바로 연결합니다. 서버로 보내기는 같은 네트워크에서만 됩니다.")
                Spacer(Modifier.height(24.dp))
            }
            when (stage) {
                DirectStage.UNSUPPORTED -> item {
                    DirectState("이 ${deviceWord}은 직접 연결을 지원하지 않습니다", "같은 네트워크에서 다시 찾거나 블루투스로 기본 연결하세요.", GeoStateKind.UNSUPPORTED)
                }
                DirectStage.PERMISSION -> item {
                    GeoSection("주변 장치", first = true) {
                        DirectState("근처 기기 권한이 필요합니다", "가까운 장치를 찾아 바로 연결하려면 권한을 허용하세요.", GeoStateKind.PERMISSION, "권한 허용", onPermissionClick)
                    }
                }
                DirectStage.RADIO_OFF -> item {
                    DirectState("직접 연결이 꺼져 있습니다", "${deviceWord}의 Wi-Fi를 켠 뒤 다시 확인하세요.", GeoStateKind.OFFLINE, "다시 확인", onDiscoveryClick)
                }
                DirectStage.CLEANING_UP, DirectStage.CONNECTING, DirectStage.CANCELLING -> item {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(when (stage) {
                            DirectStage.CLEANING_UP -> "이전 연결 정리 중"
                            DirectStage.CANCELLING -> "연결 취소 중"
                            else -> "장치 연결 준비 중"
                        }, style = type.stateTitle, color = colors.text)
                        val peer = state.peers.firstOrNull { it.deviceAddress == state.connectingPeerAddress }
                        Text(when (stage) {
                            DirectStage.CANCELLING -> "연결을 정리하고 있습니다."
                            DirectStage.CLEANING_UP -> "이전 연결을 정리하고 있습니다."
                            else -> "${peer?.let(::peerDisplayName) ?: "장치"}에 연결하는 중입니다."
                        }, style = type.body, color = colors.textSecondary)
                        GeoProgress()
                        GeoButton("연결 취소", { cancelling = true; onCancel() }, style = GeoButtonStyle.Secondary,
                            enabled = stage == DirectStage.CONNECTING,
                            disabledReason = if (stage == DirectStage.CANCELLING) "연결을 정리하고 있습니다." else "이전 연결을 정리하고 있습니다.")
                    }
                }
                DirectStage.VERIFYING -> item {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DirectState("장치 확인 중", "장치 인증과 제어 연결을 확인하고 있습니다.")
                        GeoProgress()
                        GeoButton("연결 취소", { cancelling = true; onCancel() }, style = GeoButtonStyle.Secondary)
                    }
                }
                DirectStage.AUTHENTICATION_ERROR -> item {
                    DirectState("장치 확인 실패", directFailureText(state.apiError), GeoStateKind.ERROR, "장치 다시 확인", onRetryApi)
                    GeoButton("연결 취소", { cancelling = true; onCancel() }, style = GeoButtonStyle.Secondary)
                }
                DirectStage.READY -> item {
                    DirectState("${state.apiDeviceName?.takeIf(String::isNotBlank) ?: "장치"} · 연결됨", "장치 인증을 마쳤습니다. 전체 제어를 사용할 수 있습니다.")
                }
                DirectStage.DISCOVERY_ERROR -> item {
                    DirectState("장치를 찾지 못했습니다", directFailureText(state.error), GeoStateKind.ERROR, "다시 찾기", onDiscoveryClick)
                }
                DirectStage.EMPTY, DirectStage.DISCOVERING, DirectStage.PEERS -> {
                    item {
                        GeoSection("주변 장치", first = true, count = if (state.discovering) "검색 중" else null,
                            actionLabel = if (!state.discovering && state.peers.isNotEmpty()) "다시 찾기" else null,
                            onAction = onDiscoveryClick) {
                            if (state.discovering) DeviceSurface {
                                GeoSkeleton("장치를 찾고 있습니다", kind = GeoSkeletonKind.Rows)
                            }
                            if (state.peers.isEmpty() && !state.discovering) DirectState(
                                if (state.discovering) "장치를 찾고 있습니다" else "찾은 장치가 없습니다",
                                if (state.discovering) "찾은 장치가 여기에 표시됩니다." else "장치 전원과 거리를 확인하세요.",
                                actionLabel = if (state.discovering) null else "다시 찾기", onAction = onDiscoveryClick
                            )
                        }
                    }
                    items(state.peers, key = { it.deviceAddress }) { peer ->
                        val requester = remember(peer.deviceAddress) { FocusRequester() }
                        val enabled = canConnectDirectPeer(state, permissionGranted, peer.deviceAddress)
                        val status = peerStatusLabel(peer.status)
                        DeviceSurface {
                            GeoRowList(GeoRowTrailing.Status) {
                                GeoRow(peerDisplayName(peer), { opener = requester; pendingAddress = peer.deviceAddress },
                                    modifier = Modifier.focusRequester(requester),
                                    badge = { GeoBadge(status, peerStatusTone(peer.status)) },
                                    leading = { Image(painterResource(R.drawable.geo_device), null, Modifier.size(40.dp)) },
                                    enabled = enabled, disabledReason = status, divider = false)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
    val pendingPeer = state.peers.firstOrNull { it.deviceAddress == pendingAddress }
    if (pendingPeer != null) {
        val enabled = canConnectDirectPeer(state, permissionGranted, pendingPeer.deviceAddress)
        GeoConfirmDialog(
            title = "${peerDisplayName(pendingPeer)}에 직접 연결할까요?",
            body = "장치의 공유기 연결과 진행 중인 전송이 끊길 수 있습니다.",
            confirmLabel = "직접 연결",
            openerFocusRequester = opener,
            confirmEnabled = enabled,
            disabledReason = "장치의 연결 상태를 다시 확인하세요.",
            onConfirm = {
                if (canConnectDirectPeer(state, permissionGranted, pendingPeer.deviceAddress)) onConnectClick(pendingPeer)
                pendingAddress = null
            },
            onDismissRequest = { pendingAddress = null }
        )
    }
}

@Composable
private fun DirectState(
    title: String,
    explanation: String,
    kind: GeoStateKind = GeoStateKind.EMPTY,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    GeoStateBlock(GeoStateContent(kind, title, explanation, actionLabel), onAction = onAction)
}
