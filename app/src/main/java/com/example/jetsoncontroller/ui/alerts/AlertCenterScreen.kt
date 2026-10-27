package com.example.jetsoncontroller.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.alerts.AlertRecord
import com.example.jetsoncontroller.data.alerts.AlertSeverity
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

@Composable
fun AlertIconButton(unreadCount: Int, onClick: () -> Unit) {
    Box {
        GeoIconButton(GeoIcons.bell,
            if (unreadCount > 0) "알림, 읽지 않은 알림 " + unreadCount + "개" else "알림", onClick)
        if (unreadCount > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
            .size(6.dp).clip(CircleShape).background(LocalGeoColors.current.danger))
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun AlertCenterScreen(state: AlertCenterUiState, onBack: () -> Unit, onAlertClick: (AlertRecord) -> Unit,
    onDelete: (String) -> Unit, onMarkAllRead: () -> Unit, onClear: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var deletion by remember { mutableStateOf<AlertRecord?>(null) }
    var knownIds by remember { mutableStateOf<Set<String>?>(null) }
    var newThisVisitIds by remember { mutableStateOf(emptySet<String>()) }
    val currentIds = state.alerts.mapTo(linkedSetOf()) { it.id }
    LaunchedEffect(state.isLoaded, currentIds) {
        if (state.isLoaded) {
            val previous = knownIds
            if (previous == null) knownIds = currentIds
            else {
                newThisVisitIds = newThisVisitIds + newlyArrivedAlertIds(previous, currentIds)
                knownIds = currentIds
            }
        }
    }
    if (menuOpen) GeoSheet("알림 옵션", { menuOpen = false }, kind = GeoSheetKind.CHOICE) {
        GeoRow("모두 지우기", { menuOpen = false; confirmClear = true },
            leading = { GeoIconTile(GeoIcons.trash, tone = Tone.ERROR) }, divider = false,
            enabled = state.alerts.isNotEmpty(), disabledReason = "알림이 없습니다.")
    }
    if (confirmClear) GeoConfirmDialog("알림을 모두 지울까요?", "지운 알림은 되돌릴 수 없습니다.", "모두 지우기",
        { confirmClear = false; onClear() }, { confirmClear = false }, kind = GeoConfirmKind.DANGER)
    deletion?.let { target ->
        GeoConfirmDialog("알림을 지울까요?", target.title + "\n지운 알림은 되돌릴 수 없습니다.", "알림 삭제",
            { deletion = null; if (target.id in currentIds) onDelete(target.id) },
            { deletion = null }, kind = GeoConfirmKind.DANGER,
            confirmEnabled = target.id in currentIds, disabledReason = "이미 지운 알림입니다.")
    }
    GeoLargeTitleScaffold("알림", onBack = onBack,
        actions = { GeoIconButton(GeoIcons.more, "알림 옵션", { menuOpen = true }) }) {
        val gutter = LocalGeoWindow.current.gutterDp.dp
        when {
            !state.isLoaded -> GeoSkeleton("알림을 불러오는 중", Modifier.padding(horizontal = gutter), kind = GeoSkeletonKind.Rows)
            state.alerts.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = gutter), contentAlignment = Alignment.Center) {
                GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "알림이 없습니다",
                    "장치 상태와 수집, 전송 알림이 여기에 표시됩니다."))
            }
            else -> {
                val now = System.currentTimeMillis()
                val groups = state.alerts.groupBy { alertDayLabel(it.createdAtEpochMillis, now) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
                    start = gutter, end = gutter, bottom = 24.dp)) {
                    groups.entries.forEachIndexed { index, (label, alerts) ->
                        item(key = "day-$label") {
                            GeoGroupedSection(label, first = index == 0) {
                                alerts.forEachIndexed { row, alert ->
                                    AlertRow(alert, alert.id in newThisVisitIds,
                                        { onAlertClick(alert) }, { deletion = alert }, row < alerts.lastIndex)
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
private fun AlertRow(alert: AlertRecord, isNewThisVisit: Boolean, onClick: () -> Unit, onDelete: () -> Unit,
    divider: Boolean) {
    GeoActionRow(alert.title, "삭제", onClick, onDelete,
        meta = alertSupportingText(alert.message, formatAlertTime(alert.createdAtEpochMillis), alert.read), divider = divider,
        leading = {
            Box {
                GeoIconTile(alertIcon(alert.severity), tone = when (alert.severity) {
                    AlertSeverity.ERROR -> Tone.ERROR
                    AlertSeverity.WARNING -> Tone.WARNING
                    else -> null
                })
                if (isNewThisVisit) Box(Modifier.align(Alignment.TopEnd).size(6.dp).clip(CircleShape)
                    .background(LocalGeoColors.current.danger).semantics { contentDescription = "새 알림" })
            }
        })
}

private fun alertIcon(severity: AlertSeverity) = when (severity) {
    AlertSeverity.INFO -> GeoIcons.info
    AlertSeverity.SUCCESS -> GeoIcons.success
    AlertSeverity.WARNING -> GeoIcons.warning
    AlertSeverity.ERROR -> GeoIcons.danger
}
