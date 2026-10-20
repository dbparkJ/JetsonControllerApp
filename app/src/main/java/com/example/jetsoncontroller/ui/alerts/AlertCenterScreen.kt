package com.example.jetsoncontroller.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.alerts.AlertRecord
import com.example.jetsoncontroller.data.alerts.AlertSeverity
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.GeoButton
import com.example.jetsoncontroller.ui.components.geo.GeoButtonEdge
import com.example.jetsoncontroller.ui.components.geo.GeoButtonStyle
import com.example.jetsoncontroller.ui.components.geo.GeoConfirmDialog
import com.example.jetsoncontroller.ui.components.geo.GeoConfirmKind
import com.example.jetsoncontroller.ui.components.geo.GeoIconButton
import com.example.jetsoncontroller.ui.components.geo.GeoIcons
import com.example.jetsoncontroller.ui.components.geo.GeoMenu
import com.example.jetsoncontroller.ui.components.geo.GeoMenuItem
import com.example.jetsoncontroller.ui.components.geo.GeoSection
import com.example.jetsoncontroller.ui.components.geo.GeoStateBlock
import com.example.jetsoncontroller.ui.components.geo.GeoStateContent
import com.example.jetsoncontroller.ui.components.geo.GeoStateKind
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Composable
fun AlertIconButton(unreadCount: Int, onClick: () -> Unit) {
    Box {
        GeoIconButton(
            icon = GeoIcons.bell,
            contentDescription = if (unreadCount > 0) {
                "알림, 읽지 않은 알림 " + unreadCount + "개"
            } else {
                "알림"
            },
            onClick = onClick
        )
        if (unreadCount > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                    .size(8.dp).clip(CircleShape)
                    .background(LocalGeoColors.current.danger)
            )
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun AlertCenterScreen(
    state: AlertCenterUiState,
    onBack: () -> Unit,
    onAlertClick: (AlertRecord) -> Unit,
    onDelete: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onClear: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var knownIds by remember { mutableStateOf<Set<String>?>(null) }
    var newThisVisitIds by remember { mutableStateOf(emptySet<String>()) }
    val currentIds = state.alerts.mapTo(linkedSetOf()) { it.id }

    LaunchedEffect(state.isLoaded, currentIds) {
        if (state.isLoaded) {
            val previous = knownIds
            if (previous == null) {
                knownIds = currentIds
            } else {
                newThisVisitIds = newThisVisitIds + newlyArrivedAlertIds(previous, currentIds)
                knownIds = currentIds
            }
        }
    }

    if (confirmClear) {
        GeoConfirmDialog(
            title = "알림을 모두 지울까요?",
            body = "지운 알림은 되돌릴 수 없습니다.",
            confirmLabel = "모두 지우기",
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismissRequest = { confirmClear = false },
            kind = GeoConfirmKind.DANGER
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GeoIconButton(GeoIcons.back, "뒤로", onBack)
            Text(
                "알림",
                Modifier.weight(1f).padding(start = 12.dp),
                color = LocalGeoColors.current.text,
                style = LocalGeoType.current.title
            )
            Box {
                GeoIconButton(GeoIcons.more, "알림 옵션", { menuOpen = true })
                GeoMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    items = listOf(
                        GeoMenuItem(
                            label = "모두 지우기",
                            onClick = { confirmClear = true },
                            destructive = true,
                            enabled = state.alerts.isNotEmpty(),
                            disabledReason = "알림이 없습니다."
                        )
                    )
                )
            }
        }

        when {
            !state.isLoaded -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GeoStateBlock(
                    GeoStateContent(
                        kind = GeoStateKind.MISSING,
                        title = "알림을 불러오는 중",
                        explanation = "저장된 알림을 확인하고 있습니다."
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            state.alerts.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GeoStateBlock(
                    GeoStateContent(
                        kind = GeoStateKind.EMPTY,
                        title = "알림이 없습니다",
                        explanation = "장치 상태와 수집, 전송 알림이 여기에 표시됩니다."
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            else -> {
                val now = System.currentTimeMillis()
                val groups = state.alerts.groupBy { alertDayLabel(it.createdAtEpochMillis, now) }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    groups.entries.forEachIndexed { index, (label, alerts) ->
                        item(key = "day-$label") {
                            GeoSection(label, first = index == 0) {
                                Column(Modifier.fillMaxWidth()) {
                                    alerts.forEach { alert ->
                                        AlertRow(
                                            alert = alert,
                                            isNewThisVisit = alert.id in newThisVisitIds,
                                            onClick = { onAlertClick(alert) },
                                            onDelete = { onDelete(alert.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    alert: AlertRecord,
    isNewThisVisit: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = alertIcon(alert.severity)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
            tint = alertColor(alert.severity))
        Column(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    alert.title,
                    color = LocalGeoColors.current.text,
                    style = LocalGeoType.current.titleSm.copy(
                        fontWeight = if (alert.read) FontWeight.Medium else FontWeight.SemiBold
                    )
                )
                if (isNewThisVisit) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(LocalGeoColors.current.danger)
                            .semantics { contentDescription = "새 알림" }
                    )
                }
            }
            Text(alert.message, color = LocalGeoColors.current.text, style = LocalGeoType.current.body)
            Text(
                formatAlertTime(alert.createdAtEpochMillis),
                color = LocalGeoColors.current.textSecondary,
                style = LocalGeoType.current.caption
            )
        }
        GeoButton(
            label = "삭제",
            onClick = onDelete,
            modifier = Modifier.widthIn(min = 56.dp, max = 72.dp),
            style = GeoButtonStyle.Tertiary, edge = GeoButtonEdge.End
        )
    }
}

private fun alertIcon(severity: AlertSeverity) = when (severity) {
    AlertSeverity.INFO -> GeoIcons.info
    AlertSeverity.SUCCESS -> GeoIcons.success
    AlertSeverity.WARNING -> GeoIcons.warning
    AlertSeverity.ERROR -> GeoIcons.danger
}

@Composable
private fun alertColor(severity: AlertSeverity) = when (severity) {
    AlertSeverity.INFO -> LocalGeoColors.current.info
    AlertSeverity.SUCCESS -> LocalGeoColors.current.success
    AlertSeverity.WARNING -> LocalGeoColors.current.warning
    AlertSeverity.ERROR -> LocalGeoColors.current.danger
}
