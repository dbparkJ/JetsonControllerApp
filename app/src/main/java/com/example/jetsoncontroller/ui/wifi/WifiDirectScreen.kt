package com.example.jetsoncontroller.ui.wifi

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane
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
    val deviceWord = localDeviceWord()
    val gutter = if (LocalGeoPane.current) 0.dp else 24.dp
    var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var opener by remember { mutableStateOf<FocusRequester?>(null) }
    var cancelling by rememberSaveable { mutableStateOf(false) }
    val stage = directStage(state, permissionGranted, cancelling)
    val selectedPeer = directSelectedPeer(state, permissionGranted, selectedAddress)
    LaunchedEffect(state.cleaningUp, state.connectingPeerAddress, state.connected) {
        if (!state.cleaningUp && state.connectingPeerAddress == null && !state.connected) cancelling = false
    }
    LaunchedEffect(stage, state.peers, pendingAddress) {
        if (stage !in setOf(DirectStage.PEERS, DirectStage.DISCOVERING) ||
            state.peers.none { it.deviceAddress == pendingAddress }) pendingAddress = null
        if (state.peers.none { it.deviceAddress == selectedAddress }) selectedAddress = null
    }
    val cancelConnection = { cancelling = true; onCancel() }
    val primary = when (stage) {
        DirectStage.PERMISSION -> GeoButtonAction("권한 허용", onPermissionClick)
        DirectStage.RADIO_OFF -> GeoButtonAction("다시 확인", onDiscoveryClick)
        DirectStage.PREPARATION_ERROR -> GeoButtonAction("다시 시도", onRetryApi)
        DirectStage.AUTHENTICATION_ERROR -> GeoButtonAction("장치 다시 확인", onRetryApi)
        DirectStage.DISCOVERY_ERROR -> GeoButtonAction("다시 찾기", onDiscoveryClick)
        DirectStage.PREPARING -> GeoButtonAction("연결 취소", onCancel, GeoButtonStyle.Secondary)
        DirectStage.VERIFYING -> GeoButtonAction("연결 취소", cancelConnection, GeoButtonStyle.Secondary)
        DirectStage.CLEANING_UP, DirectStage.CONNECTING, DirectStage.CANCELLING ->
            GeoButtonAction("연결 취소", cancelConnection, GeoButtonStyle.Secondary,
                enabled = stage == DirectStage.CONNECTING,
                disabledReason = if (stage == DirectStage.CANCELLING) "연결을 정리하고 있습니다." else "이전 연결을 정리하고 있습니다.")
        DirectStage.EMPTY, DirectStage.DISCOVERING, DirectStage.PEERS ->
            if (selectedPeer != null) GeoButtonAction("직접 연결", { pendingAddress = selectedPeer.deviceAddress })
            else GeoButtonAction("다시 찾기", onDiscoveryClick, enabled = !state.discovering,
                busy = state.discovering, busyLabel = "검색 중", disabledReason = "장치를 찾고 있습니다.")
        else -> null
    }
    val secondary = when (stage) {
        DirectStage.PREPARATION_ERROR -> GeoButtonAction("연결 취소", onCancel, GeoButtonStyle.Secondary)
        DirectStage.AUTHENTICATION_ERROR -> GeoButtonAction("연결 취소", cancelConnection, GeoButtonStyle.Secondary)
        DirectStage.PEERS, DirectStage.DISCOVERING -> if (selectedPeer != null)
            GeoButtonAction("다시 찾기", onDiscoveryClick, GeoButtonStyle.Secondary, enabled = !state.discovering,
                disabledReason = "장치를 찾고 있습니다.") else null
        else -> null
    }
    GeoLargeTitleScaffold("직접 연결", onBack = onBack, primaryAction = primary, secondaryAction = secondary) {
        LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 24.dp)) {
            if (stage != DirectStage.UNSUPPORTED) item {
                Text("공유기 없이 ${deviceWord}과 장치를 바로 연결합니다. 서버로 보내기는 같은 네트워크에서만 됩니다.",
                    style = LocalGeoType.current.supporting, color = LocalGeoColors.current.textSecondary)
                Spacer(Modifier.height(24.dp))
            }
            when (stage) {
                DirectStage.UNSUPPORTED -> item {
                    DirectState("이 ${deviceWord}은 직접 연결을 지원하지 않습니다", "같은 네트워크에서 다시 찾거나 블루투스로 기본 연결하세요.", GeoStateKind.UNSUPPORTED)
                }
                DirectStage.PERMISSION -> item {
                    GeoGroupedSection("주변 장치", first = true) {
                        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        DirectState(if (legacy) "위치 권한이 필요합니다" else "근처 기기 권한이 필요합니다",
                            if (legacy) "가까운 장치를 찾아 바로 연결하려면 위치 권한을 허용하고 위치 서비스를 켜세요."
                            else "가까운 장치를 찾아 바로 연결하려면 권한을 허용하세요.", GeoStateKind.PERMISSION)
                    }
                }
                DirectStage.RADIO_OFF -> item {
                    DirectState("직접 연결이 꺼져 있습니다", "${deviceWord}의 Wi-Fi를 켠 뒤 다시 확인하세요.", GeoStateKind.OFFLINE)
                }
                DirectStage.PREPARING -> item {
                    GeoSkeleton("시각 동기화 준비 중", kind = GeoSkeletonKind.Rows)
                    DirectState("시각 동기화 준비 중", "블루투스 인증과 장치 시각을 확인하고 있습니다.")
                }
                DirectStage.PREPARATION_ERROR -> item {
                    DirectState("연결 준비 실패", state.apiError ?: "시각을 확인하지 못했습니다. 다시 시도해 주세요.", GeoStateKind.ERROR)
                }
                DirectStage.CLEANING_UP, DirectStage.CONNECTING, DirectStage.CANCELLING -> item {
                    val title = when (stage) {
                        DirectStage.CLEANING_UP -> "이전 연결 정리 중"
                        DirectStage.CANCELLING -> "연결 취소 중"
                        else -> "장치 연결 준비 중"
                    }
                    val peer = state.peers.firstOrNull { it.deviceAddress == state.connectingPeerAddress }
                    GeoSkeleton(title, kind = GeoSkeletonKind.Rows)
                    DirectState(title, when (stage) {
                        DirectStage.CANCELLING -> "연결을 정리하고 있습니다."
                        DirectStage.CLEANING_UP -> "이전 연결을 정리하고 있습니다."
                        else -> "${peer?.let(::peerDisplayName) ?: "장치"}에 연결하는 중입니다."
                    })
                }
                DirectStage.VERIFYING -> item {
                    GeoSkeleton("장치 확인 중", kind = GeoSkeletonKind.Rows)
                    DirectState("장치 확인 중", "장치 인증과 제어 연결을 확인하고 있습니다.")
                }
                DirectStage.AUTHENTICATION_ERROR -> item {
                    DirectState("장치 확인 실패", directFailureText(state.apiError), GeoStateKind.ERROR)
                }
                DirectStage.READY -> item {
                    GeoGroupedSection("연결 상태", first = true, trailingColumn = GeoRowTrailing.Status) {
                        GeoRow(state.apiDeviceName?.takeIf(String::isNotBlank) ?: "장치", {},
                            meta = "장치 인증을 마쳤습니다. 전체 제어를 사용할 수 있습니다.",
                            badge = { GeoBadge("연결됨", com.example.jetsoncontroller.ui.model.Tone.SUCCESS) },
                            leading = { GeoIconTile(GeoIcons.link) }, navigable = false, divider = false)
                    }
                }
                DirectStage.DISCOVERY_ERROR -> item {
                    DirectState("장치를 찾지 못했습니다", directFailureText(state.error), GeoStateKind.ERROR)
                }
                DirectStage.EMPTY, DirectStage.DISCOVERING, DirectStage.PEERS -> item {
                    GeoGroupedSection("주변 장치", first = true, trailingColumn = GeoRowTrailing.Status) {
                        if (state.discovering) GeoSkeleton("장치를 찾고 있습니다", Modifier.padding(16.dp), kind = GeoSkeletonKind.Rows)
                        if (state.peers.isEmpty() && !state.discovering) {
                            DirectState("찾은 장치가 없습니다", "장치 전원과 거리를 확인하세요.")
                        }
                        state.peers.forEachIndexed { index, peer ->
                            key(peer.deviceAddress) {
                                val requester = remember(peer.deviceAddress) { FocusRequester() }
                                val enabled = canConnectDirectPeer(state, permissionGranted, peer.deviceAddress)
                                val photo: @Composable () -> Unit = {
                                    Image(painterResource(R.drawable.geo_device), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                                }
                                val badge: @Composable () -> Unit = { GeoBadge(peerStatusLabel(peer.status), peerStatusTone(peer.status)) }
                                if (enabled) GeoPaneListRow(peerDisplayName(peer),
                                    { opener = requester; selectedAddress = peer.deviceAddress },
                                    selected = peer.deviceAddress == selectedAddress,
                                    index = index, rowCount = state.peers.size,
                                    nextSelected = state.peers.getOrNull(index + 1)?.deviceAddress == selectedAddress,
                                    modifier = Modifier.focusRequester(requester),
                                    leading = photo, leadingIsPhoto = true, badge = badge)
                                else GeoRow(peerDisplayName(peer), {}, modifier = Modifier.focusRequester(requester),
                                    leading = photo, leadingIsPhoto = true, badge = badge,
                                    enabled = false, disabledReason = peerStatusLabel(peer.status),
                                    divider = index < state.peers.lastIndex)
                            }
                        }
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
            confirmLabel = "직접 연결", kind = GeoConfirmKind.DANGER,
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
private fun DirectState(title: String, explanation: String, kind: GeoStateKind = GeoStateKind.EMPTY) {
    GeoStateBlock(GeoStateContent(kind, title, explanation),
        Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
}
