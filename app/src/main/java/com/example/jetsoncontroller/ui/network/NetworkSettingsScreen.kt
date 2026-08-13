package com.example.jetsoncontroller.ui.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.data.network.WifiSecurity
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.components.InlineMessage
import com.example.jetsoncontroller.ui.components.SectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NetworkSettingsScreen(
    state: NetworkSettingsUiState,
    onBack: () -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    wifiScanPermissionGranted: Boolean,
    onRequestWifiScanPermission: () -> Unit,
    onScanAccessPoints: () -> Unit,
    onSelectAccessPoint: (WifiAccessPoint) -> Unit,
    onWifiDirectClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var manualEntryExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("네트워크") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        onClick = if (wifiScanPermissionGranted) {
                            onScanAccessPoints
                        } else {
                            onRequestWifiScanPermission
                        },
                        enabled = !state.scanningAccessPoints
                    ) {
                        if (state.scanningAccessPoints) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Wi-Fi 다시 검색")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("연결 상태") },
                    icon = { Icon(Icons.Default.Router, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Jetson Wi-Fi") },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
                )
            }
            if (selectedTab == 0) {
                NetworkConnectionStatus(
                    state = state,
                    wifiScanPermissionGranted = wifiScanPermissionGranted,
                    onRequestWifiScanPermission = onRequestWifiScanPermission,
                    onWifiDirectClick = onWifiDirectClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    ConnectionMethodLabel(state.transportType)
                    if (state.wifiConnected && !state.currentWifiSsid.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        CurrentWifiCard(state.currentWifiSsid)
                    }
                    state.accessPointError?.let { error ->
                        Spacer(Modifier.height(12.dp))
                        InlineMessage(message = error, isError = true)
                    }
                }
            }

            item {
                SectionHeader(
                    title = "주변 네트워크",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            if (state.accessPoints.isEmpty() && !state.scanningAccessPoints) {
                item {
                    EmptyState(
                        title = "검색된 네트워크가 없습니다",
                        message = "Wi-Fi 검색 권한과 위치 서비스 상태를 확인하세요.",
                        actionLabel = if (wifiScanPermissionGranted) "다시 검색" else "권한 허용",
                        onAction = if (wifiScanPermissionGranted) {
                            onScanAccessPoints
                        } else {
                            onRequestWifiScanPermission
                        }
                    )
                }
            } else {
                items(
                    items = state.accessPoints,
                    key = { accessPoint -> accessPoint.ssid }
                ) { accessPoint ->
                    WifiAccessPointRow(
                        accessPoint = accessPoint,
                        selected = state.selectedAccessPointSsid == accessPoint.ssid &&
                            !state.isCurrentJetsonWifi(accessPoint.ssid),
                        connected = state.isCurrentJetsonWifi(accessPoint.ssid),
                        password = state.password,
                        sending = state.sending,
                        message = state.message,
                        messageIsError = state.isError,
                        onSelect = {
                            manualEntryExpanded = false
                            onSelectAccessPoint(accessPoint)
                        },
                        onPasswordChange = onPasswordChange,
                        onSubmit = onSubmit
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(
                    title = "직접 입력",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("숨겨진 네트워크 추가") },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            if (manualEntryExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        manualEntryExpanded = !manualEntryExpanded
                        if (manualEntryExpanded) {
                            onSsidChange("")
                            onHiddenChange(true)
                        }
                    }
                )
                AnimatedVisibility(visible = manualEntryExpanded) {
                    ManualNetworkForm(
                        state = state,
                        onSsidChange = onSsidChange,
                        onPasswordChange = onPasswordChange,
                        onHiddenChange = onHiddenChange,
                        onSubmit = onSubmit
                    )
                }
            }
                }
            }
        }
    }
}

@Composable
private fun NetworkConnectionStatus(
    state: NetworkSettingsUiState,
    wifiScanPermissionGranted: Boolean,
    onRequestWifiScanPermission: () -> Unit,
    onWifiDirectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ConnectionMethodLabel(state.transportType) }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    NetworkIdentityRow(
                        icon = Icons.Default.Smartphone,
                        label = "모바일",
                        value = state.mobileWifiSsid ?: "Wi-Fi 확인 필요"
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    NetworkIdentityRow(
                        icon = Icons.Default.Router,
                        label = "Jetson",
                        value = if (state.wifiConnected) {
                            state.currentWifiSsid ?: "연결된 Wi-Fi"
                        } else {
                            "Wi-Fi 연결 안 됨"
                        }
                    )
                }
            }
        }
        item {
            when {
                state.sameWifi && state.transportType == TransportType.LAN -> InlineMessage(
                    "같은 Wi-Fi에서 LAN으로 자동 연결되었습니다.",
                    isError = false
                )
                state.sameWifi -> InlineMessage(
                    "모바일과 Jetson이 같은 Wi-Fi에 연결되어 있습니다.",
                    isError = false
                )
                !wifiScanPermissionGranted -> InlineMessage(
                    "모바일 Wi-Fi를 확인하려면 위치 권한이 필요합니다.",
                    isError = false
                )
                !state.mobileWifiSsid.isNullOrBlank() && !state.currentWifiSsid.isNullOrBlank() ->
                    InlineMessage(
                        "모바일과 Jetson의 Wi-Fi가 서로 다릅니다.",
                        isError = true
                    )
            }
        }
        if (!wifiScanPermissionGranted) {
            item {
                OutlinedButton(
                    onClick = onRequestWifiScanPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wi-Fi 확인 권한 허용")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onWifiDirectClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.WifiTethering, contentDescription = null)
                Text("Wi-Fi Direct로 연결", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun NetworkIdentityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
}

@Composable
private fun CurrentWifiCard(ssid: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Column {
                Text(
                    "현재 Wi-Fi 연결됨",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(ssid, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ConnectionMethodLabel(type: TransportType?) {
    val text = when (type) {
        TransportType.BLE -> "Bluetooth로 Jetson에 전송"
        TransportType.LAN -> "LAN으로 Jetson에 전송"
        TransportType.WIFI_DIRECT -> "Wi-Fi Direct로 Jetson에 전송"
        null -> "Jetson 연결 확인 중"
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WifiAccessPointRow(
    accessPoint: WifiAccessPoint,
    selected: Boolean,
    connected: Boolean,
    password: String,
    sending: Boolean,
    message: String?,
    messageIsError: Boolean,
    onSelect: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var passwordVisible by rememberSaveable(accessPoint.ssid) { mutableStateOf(false) }

    LaunchedEffect(selected) {
        if (selected) {
            delay(180)
            bringIntoViewRequester.bringIntoView()
            if (accessPoint.requiresPassword && accessPoint.provisionable) {
                focusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(selected, imeBottom) {
        if (selected && imeBottom > 0) {
            delay(80)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Surface(
        color = if (selected || connected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .bringIntoViewRequester(bringIntoViewRequester)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        accessPoint.ssid,
                        fontWeight = if (selected || connected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                    )
                },
                supportingContent = {
                    Text(
                        if (connected) {
                            "현재 Jetson이 연결됨 · ${securityLabel(accessPoint.security)}"
                        } else {
                            securityLabel(accessPoint.security)
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (selected || connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            signalLabel(accessPoint.rssi),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        Icon(
                            when {
                                selected || connected -> Icons.Default.CheckCircle
                                accessPoint.secured -> Icons.Default.Lock
                                else -> Icons.Default.LockOpen
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected || connected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier
                    .testTag("wifi-access-point-${accessPoint.ssid}")
                    .clickable(enabled = !connected, onClick = onSelect)
            )

            AnimatedVisibility(visible = selected && !connected) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 68.dp, end = 20.dp, bottom = 16.dp)
                        .testTag("wifi-selected-network-form"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!accessPoint.provisionable) {
                        InlineMessage(
                            message = when (accessPoint.security) {
                                WifiSecurity.ENTERPRISE ->
                                    "기업용 네트워크는 사용자 계정과 인증서 설정이 필요합니다."
                                else -> "WEP 네트워크는 보안상 앱에서 연결하지 않습니다."
                            },
                            isError = false
                        )
                    } else if (accessPoint.requiresPassword) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focus ->
                                    if (focus.isFocused) {
                                        scope.launch {
                                            delay(180)
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                            placeholder = { Text("8자 이상 입력") },
                            label = { Text("비밀번호") },
                            singleLine = true,
                            enabled = !sending,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                if (passwordByteLengthIsValid(password)) {
                                    focusManager.clearFocus()
                                    onSubmit()
                                }
                            }),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 표시"
                                    )
                                }
                            }
                        )
                    }

                    if (accessPoint.provisionable && !accessPoint.requiresPassword) {
                        Text(
                            text = "비밀번호가 필요 없는 네트워크입니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (accessPoint.provisionable) {
                        message?.let {
                            InlineMessage(message = it, isError = messageIsError)
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSubmit()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wifi-connect-button"),
                            enabled = !sending &&
                                (!accessPoint.requiresPassword || passwordByteLengthIsValid(password))
                        ) {
                            if (sending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(if (sending) "전송 중" else "이 네트워크에 연결")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualNetworkForm(
    state: NetworkSettingsUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.ssid,
            onValueChange = onSsidChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("네트워크 이름") },
            singleLine = true,
            enabled = !state.sending
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("비밀번호") },
            supportingText = { Text("개방형 네트워크는 비워 두세요") },
            singleLine = true,
            enabled = !state.sending,
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (
                    state.ssid.isNotBlank() &&
                    (state.password.isEmpty() || passwordByteLengthIsValid(state.password))
                ) {
                    focusManager.clearFocus()
                    onSubmit()
                }
            }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 표시"
                    )
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("숨겨진 네트워크", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = state.hidden,
                onCheckedChange = onHiddenChange,
                enabled = !state.sending
            )
        }
        state.message?.let {
            InlineMessage(message = it, isError = state.isError)
        }
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.sending && state.ssid.isNotBlank() &&
                (state.password.isEmpty() || passwordByteLengthIsValid(state.password)) &&
                state.ssid.toByteArray(Charsets.UTF_8).size <= 32
        ) {
            Text(if (state.sending) "전송 중" else "네트워크 추가")
        }
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "매우 강함"
    rssi >= -67 -> "강함"
    rssi >= -75 -> "보통"
    else -> "약함"
}

private fun securityLabel(security: WifiSecurity): String = when (security) {
    WifiSecurity.OPEN -> "개방형 네트워크"
    WifiSecurity.ENHANCED_OPEN -> "향상된 개방형 네트워크"
    WifiSecurity.PERSONAL -> "WPA 개인용 네트워크"
    WifiSecurity.ENTERPRISE -> "기업용 네트워크"
    WifiSecurity.LEGACY_WEP -> "구형 WEP 네트워크"
}

private fun passwordByteLengthIsValid(password: String): Boolean =
    password.toByteArray(Charsets.UTF_8).size in 8..63
