package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.field.RunReceiptStrip
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

/**
 * 작업 root: the place to start a 현장 수집, see the one running collection, and look back at
 * recent receipts. Everything here is state plus callbacks, so the integrator only wires routes.
 */
@Composable
internal fun TasksHomeScreen(
    state: TasksHomeUiState,
    onSelectDevice: () -> Unit,
    onStartSurvey: () -> Unit,
    onChangeSection: () -> Unit,
    onOpenActiveRun: () -> Unit,
    onOpenRun: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenTicket: (String) -> Unit = {},
    onSelectTicketUnit: (String?) -> Unit = {}
) {
    val c = LocalGeoColors.current
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        TasksHeader(state.deviceName, onSelectDevice)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { PinnedSurveyCard(state.pinned, runActive = state.activeRun != null, onStartSurvey, onChangeSection) }
            state.ticketSection?.let { tickets ->
                item { ServerTicketsSection(tickets, onOpenTicket, onSelectTicketUnit) }
            }
            state.activeRun?.let { run ->
                item { ActiveRunCard(run, onOpenActiveRun) }
            }
            item { RecentSection(state.recent, state.recentEmptyText, onOpenRun, onOpenHistory) }
            item { OtherTasksSection(state.otherTasks, state.otherTasksNote, state.otherTasksEmptyText, onOpenTask) }
        }
    }
}

@Composable
private fun ServerTicketsSection(
    state: ServerTicketUiState,
    onOpenTicket: (String) -> Unit,
    onSelectUnit: (String?) -> Unit
) {
    val c = LocalGeoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("수집 구간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.selectedUnit == null,
                onClick = { onSelectUnit(null) },
                label = { Text("전체") }
            )
            state.units.forEach { unit ->
                FilterChip(
                    selected = state.selectedUnit == unit,
                    onClick = { onSelectUnit(unit) },
                    label = { Text(unit) }
                )
            }
        }
        state.quietError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
        if (state.isLoading && state.tickets.isEmpty()) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.primary)
        } else if (state.visibleTickets.isEmpty()) {
            Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("표시할 수집 구간이 없습니다.", style = MaterialTheme.typography.bodyMedium, color = c.muted,
                    modifier = Modifier.padding(16.dp))
            }
        } else {
            Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    state.visibleTickets.forEachIndexed { index, ticket ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.border)
                        Column(
                            Modifier.fillMaxWidth().clickable { onOpenTicket(ticket.rowId) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(ticket.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.size(8.dp))
                                StatusBadge(ticketStatusLabel(ticket), ticketStatusTone(ticket))
                            }
                            Text(
                                "프로젝트 ${ticket.project?.takeIf(String::isNotBlank) ?: "미정"} · " +
                                    "호기 ${ticket.unit?.takeIf(String::isNotBlank) ?: "미정"}",
                                style = MaterialTheme.typography.bodySmall, color = c.muted
                            )
                            Text("담당 ${ticket.assignees.orEmpty().filter(String::isNotBlank).joinToString().ifBlank { "미지정" }}",
                                style = MaterialTheme.typography.bodySmall, color = c.inkStrong)
                            Text("마감 ${ticket.dueDate?.takeIf(String::isNotBlank) ?: "미정"}",
                                style = MaterialTheme.typography.bodySmall, color = c.muted)
                            val reportLabel = state.reportMessages[ticket.rowId] ?: ticketReportStateLabel(ticket)
                            Text(reportLabel, style = MaterialTheme.typography.bodySmall,
                                color = if (ticketConflictCopy(ticket) != null ||
                                    state.reportMessages[ticket.rowId]?.contains("HUMAN_CHANGED") == true
                                ) c.warning else c.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksHeader(deviceName: String, onSelectDevice: () -> Unit) {
    val c = LocalGeoColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("작업", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.ink)
        Surface(
            onClick = onSelectDevice,
            shape = RoundedCornerShape(8.dp),
            color = c.surface,
            border = BorderStroke(1.dp, c.controlBorder),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .semantics { contentDescription = "작업할 장치 바꾸기" }
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    deviceName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 40.dp).padding(vertical = 10.dp)
                )
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = c.inkSubtle, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PinnedSurveyCard(
    pinned: PinnedSurveyState,
    runActive: Boolean,
    onStartSurvey: () -> Unit,
    onChangeSection: () -> Unit
) {
    val c = LocalGeoColors.current
    Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("현장 수집", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.ink)
                Text(
                    "구간을 고르고 장치를 점검한 뒤 시작합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkStrong
                )
            }
            Row(
                Modifier.fillMaxWidth().background(c.subtle, RoundedCornerShape(8.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = c.inkSubtle, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pinned.scopeLabel ?: "구간을 골라 주세요",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(pinned.scopeCaption, style = MaterialTheme.typography.labelSmall, color = c.muted)
                }
                TextButton(onClick = onChangeSection, enabled = pinned.changeEnabled) {
                    Text(
                        if (pinned.scopeLabel != null) "바꾸기" else "고르기",
                        color = if (pinned.changeEnabled) c.primary else c.inkSubtle
                    )
                }
            }
            if (runActive) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("점검하고 시작") }
            } else {
                PrimaryActionButton("점검하고 시작", onStartSurvey, enabled = pinned.startEnabled)
            }
            pinned.startBlockedReason?.let { reason ->
                Text(reason, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

@Composable
private fun ActiveRunCard(run: ActiveRunState, onOpenActiveRun: () -> Unit) {
    val c = LocalGeoColors.current
    Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge("수집 중", StatusTone.PENDING)
            Text(run.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(run.caption, style = MaterialTheme.typography.bodySmall, color = c.muted)
            Spacer(Modifier.height(4.dp))
            PrimaryActionButton("진행 보기", onOpenActiveRun, enabled = true)
        }
    }
}

@Composable
private fun RecentSection(
    recent: List<RecentRunState>,
    emptyText: String,
    onOpenRun: (String) -> Unit,
    onOpenHistory: () -> Unit
) {
    val c = LocalGeoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("최근 작업", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onOpenHistory) { Text("전체 기록", color = c.primary) }
        }
        if (recent.isEmpty()) {
            Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    recent.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.border)
                        RecentRunRow(row, onClick = { onOpenRun(row.runId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRunRow(row: RecentRunState, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.meta.isNotBlank()) {
                Text(
                    row.meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = c.muted
                )
            }
        }
        RunReceiptStrip(row.cells)
    }
}

@Composable
private fun OtherTasksSection(
    tasks: List<OtherTaskState>,
    note: String,
    emptyText: String,
    onOpenTask: (String) -> Unit
) {
    val c = LocalGeoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("다른 작업", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.ink)
            Text(note, style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
        if (tasks.isEmpty()) {
            Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    tasks.forEachIndexed { index, task ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.border)
                        OtherTaskRow(task, onClick = { onOpenTask(task.pipelineId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherTaskRow(task: OtherTaskState, onClick: () -> Unit) {
    val c = LocalGeoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(task.caption, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Text(task.stateLabel, style = MaterialTheme.typography.labelMedium, color = c.inkStrong)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.inkSubtle, modifier = Modifier.size(20.dp))
    }
}

/** The one filled action on a screen. Sized to the 52 dp primary height and the 8 dp control radius. */
@Composable
private fun PrimaryActionButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    val c = LocalGeoColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
