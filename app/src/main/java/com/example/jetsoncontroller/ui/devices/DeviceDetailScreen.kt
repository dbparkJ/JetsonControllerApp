package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.ui.connection.DeviceConnectionUi
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.theme.Button
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton

private val DetailSurfaceShape = RoundedCornerShape(12.dp)
private val DetailControlShape = RoundedCornerShape(8.dp)

/**
 * One device, opened from the device root. It shows the live status (or the last known
 * values when offline), the work, sensors, connection and data, device management, and
 * the advanced tools that appear only in developer mode.
 *
 * Reboot and power-off are enabled only when [devicePowerActionEnabled] allows them, and
 * each one asks for confirmation with the device name first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceDetailScreen(
    deviceName: String,
    dashboard: DashboardUiState,
    snapshot: DeviceSnapshot?,
    connection: DeviceConnectionUi,
    lastSeenText: String?,
    freshness: String?,
    runActive: Boolean,
    developerModeEnabled: Boolean,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onStartCollection: () -> Unit,
    onViewRun: () -> Unit,
    onCamera: () -> Unit,
    onGnss: () -> Unit,
    onSameNetwork: () -> Unit,
    onDirectConnect: () -> Unit,
    onQrRegister: () -> Unit,
    onSaveDiagnostics: () -> Unit,
    onNetwork: () -> Unit,
    onFiles: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    onPipelines: () -> Unit,
    onFan: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val c = LocalGeoColors.current
    val online = dashboard.isOnline
    val statusIsCurrent = online && dashboard.statusFreshness == StatusFreshness.CURRENT
    val figures = deviceFigures(
        live = dashboard.status.takeIf { statusIsCurrent },
        snapshot = snapshot
    )
    val powerEnabled = devicePowerActionEnabled(
        powerCommandsEnabled = dashboard.capabilities.powerCommandsEnabled,
        online = online,
        operationInProgress = dashboard.operationInProgress,
        runActive = runActive
    )
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var pendingPowerAction by rememberSaveable { mutableStateOf<String?>(null) }

    pendingPowerAction?.let { action ->
        val reboot = action == POWER_REBOOT
        AlertDialog(
            onDismissRequest = { pendingPowerAction = null },
            title = { Text(if (reboot) "$deviceName 재부팅" else "$deviceName 전원 종료") },
            text = {
                Text(
                    "대상: $deviceName\n실행 중인 수집과 전송이 중단되고 연결이 끊어질 수 있습니다." +
                        if (reboot) "" else "\n다시 켜려면 장치의 전원 버튼을 눌러야 합니다."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingPowerAction = null
                        if (reboot) onReboot() else onShutdown()
                    },
                    enabled = powerEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.danger,
                        contentColor = c.onDanger
                    )
                ) {
                    Text(if (reboot) "장치 재부팅" else "장치 전원 종료")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPowerAction = null }) { Text("취소") }
            }
        )
    }

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            Surface(color = c.canvas, contentColor = c.ink) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GeoSpace.xs)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                    Text(
                        deviceName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (online) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "장치 메뉴")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("연결 끊기") },
                                    onClick = { menuOpen = false; onDisconnect() }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = GeoSpace.gutter,
                end = GeoSpace.gutter,
                top = GeoSpace.xs,
                bottom = GeoSpace.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(GeoSpace.xl)
        ) {
            item {
                IdentityCard(
                    deviceName = deviceName,
                    connection = connection,
                    lastSeenText = lastSeenText,
                    freshness = freshness.takeIf { statusIsCurrent },
                    online = online
                )
            }
            item {
                DetailSection("상태") {
                    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                            FigureCell(label = "온도", value = figures.temperature, detail = figures.sourceNote, modifier = Modifier.weight(1f))
                            FigureCell(label = "저장 여유", value = figures.storageFree, detail = figures.storageFreeDetail ?: figures.sourceNote, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
                            FigureCell(label = "CPU · GPU", value = figures.cpuGpu, detail = figures.sourceNote, modifier = Modifier.weight(1f))
                            FigureCell(label = "메모리", value = figures.memory, detail = figures.memoryDetail ?: figures.sourceNote, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                DetailSection("작업") {
                    Column(
                        Modifier.fillMaxWidth().padding(GeoSpace.lg),
                        verticalArrangement = Arrangement.spacedBy(GeoSpace.md)
                    ) {
                        Text(
                            if (runActive) "수집을 진행하고 있습니다." else "진행 중인 작업이 없습니다.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!runActive && !online) {
                            Text(
                                "장치가 연결되어 있어야 수집을 시작할 수 있습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.muted
                            )
                        }
                        if (runActive) {
                            Button(
                                onClick = onViewRun,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = DetailControlShape
                            ) { Text("진행 보기", fontWeight = FontWeight.Bold) }
                        } else {
                            Button(
                                onClick = onStartCollection,
                                enabled = online,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = DetailControlShape
                            ) { Text("수집 시작", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            item {
                DetailSection("센서") {
                    LinkRow(
                        title = "카메라",
                        value = if (dashboard.status.cameraSensor.active) "수신 중" else "꺼짐",
                        onClick = onCamera
                    )
                    DetailDivider()
                    LinkRow(
                        title = "위치(GNSS)",
                        value = if (dashboard.status.gnssSensor.active) "수신 중" else "꺼짐",
                        onClick = onGnss
                    )
                }
            }
            item {
                DetailSection("연결과 데이터") {
                    LinkRow(title = "연결 방법", value = connection.label, onClick = { sheetOpen = true })
                    DetailDivider()
                    LinkRow(title = "장치 Wi-Fi", value = null, onClick = onNetwork)
                    DetailDivider()
                    LinkRow(title = "장치 파일", value = null, onClick = onFiles)
                }
            }
            item {
                DetailSection("장치 관리") {
                    PowerRow(
                        title = "$deviceName 재부팅",
                        description = "확인 후 실행합니다. 수집 중에는 쓸 수 없습니다.",
                        enabled = powerEnabled,
                        onClick = { pendingPowerAction = POWER_REBOOT }
                    )
                    DetailDivider()
                    PowerRow(
                        title = "$deviceName 전원 종료",
                        description = "다시 켜려면 장치의 전원 버튼을 눌러야 합니다.",
                        enabled = powerEnabled,
                        onClick = { pendingPowerAction = POWER_SHUTDOWN }
                    )
                    if (!powerEnabled) {
                        Text(
                            powerUnavailableReason(dashboard, runActive),
                            modifier = Modifier.padding(horizontal = GeoSpace.lg, vertical = GeoSpace.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted
                        )
                    }
                }
            }
            if (developerModeEnabled) {
                item {
                    DetailSection("고급") {
                        LinkRow(title = "작업 등록과 편집", value = null, onClick = onPipelines)
                        DetailDivider()
                        LinkRow(title = "팬 수동 제어", value = null, onClick = onFan)
                        DetailDivider()
                        LinkRow(title = "연결 진단 기록", value = null, onClick = onDiagnostics)
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        ConnectMethodSheet(
            deviceName = deviceName,
            connection = connection,
            lastSeenText = lastSeenText,
            developerModeEnabled = developerModeEnabled,
            onDismiss = { sheetOpen = false },
            onSameNetwork = { sheetOpen = false; onSameNetwork() },
            onDirectConnect = { sheetOpen = false; onDirectConnect() },
            onQrRegister = { sheetOpen = false; onQrRegister() },
            onSaveDiagnostics = { sheetOpen = false; onSaveDiagnostics() }
        )
    }
}

private const val POWER_REBOOT = "reboot"
private const val POWER_SHUTDOWN = "shutdown"

/** Names the one condition that keeps the power actions off. */
internal fun powerUnavailableReason(dashboard: DashboardUiState, runActive: Boolean): String = when {
    runActive -> "수집 중에는 재부팅이나 전원 종료를 할 수 없습니다."
    !dashboard.isOnline -> "장치가 연결되어 있어야 재부팅하거나 전원을 끌 수 있습니다."
    !dashboard.capabilities.powerCommandsEnabled -> "이 장치는 앱에서 전원 명령을 보낼 수 없도록 설정되어 있습니다."
    dashboard.operationInProgress -> "진행 중인 작업이 끝난 뒤 사용할 수 있습니다."
    else -> ""
}

@Composable
private fun IdentityCard(
    deviceName: String,
    connection: DeviceConnectionUi,
    lastSeenText: String?,
    freshness: String?,
    online: Boolean
) {
    val c = LocalGeoColors.current
    Surface(color = c.surface, contentColor = c.ink, shape = DetailSurfaceShape, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(GeoSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DevicePhoto(size = 72.dp)
            Spacer(Modifier.width(GeoSpace.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GeoSpace.xs)) {
                StatusChip(
                    text = statusChipText(connection, lastSeenText, online, freshness),
                    tone = connection.tone
                )
                Text(
                    connection.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSubtle
                )
                Text(
                    deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GeoSpace.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(
            color = LocalGeoColors.current.surface,
            contentColor = LocalGeoColors.current.ink,
            shape = DetailSurfaceShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun DetailDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = GeoSpace.lg)
            .background(LocalGeoColors.current.sectionBorder)
    )
}

@Composable
private fun LinkRow(title: String, value: String?, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = GeoSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.width(GeoSpace.xs))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.muted)
    }
}

@Composable
private fun PowerRow(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = GeoSpace.lg, vertical = GeoSpace.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) c.danger else c.onDisabled
        )
        Text(description, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}
