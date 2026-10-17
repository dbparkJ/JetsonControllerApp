package com.example.jetsoncontroller.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.ui.components.GeoPrimaryAction
import com.example.jetsoncontroller.ui.components.StatusBadge
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TicketDetailScreen(
    ticket: ServerTicket,
    role: String?,
    reportMessage: String?,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    val c = LocalGeoColors.current
    val availability = ticketStartAvailability(role, ticket)
    var confirmStart by remember(ticket.rowId) { mutableStateOf(false) }
    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("수집 시작") },
            text = { Text("${ticket.title} 수집을 시작합니다. 티켓이 '진행 중'으로 바뀝니다.") },
            confirmButton = {
                TextButton(onClick = { confirmStart = false; onStart() }) { Text("시작") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("취소") }
            }
        )
    }
    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = { Text("수집 구간") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.canvas, titleContentColor = c.ink)
            )
        },
        bottomBar = {
            Surface(color = c.surface) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GeoPrimaryAction(
                        label = "이 구간으로 수집 시작",
                        onClick = { confirmStart = true },
                        enabled = availability.enabled,
                        icon = Icons.Default.CheckCircle
                    )
                    availability.reason?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().background(c.canvas).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ticket.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = c.ink)
                            Spacer(Modifier.size(8.dp))
                            StatusBadge(ticketStatusLabel(ticket), ticketStatusTone(ticket))
                        }
                        TicketDetailRow("프로젝트", ticket.project)
                        TicketDetailRow("호기", ticket.unit)
                        TicketDetailRow("담당", ticket.assignees.orEmpty().filter(String::isNotBlank).joinToString().ifBlank { "미지정" })
                        TicketDetailRow("마감", ticket.dueDate)
                        TicketDetailRow("보고", ticketReportStateLabel(ticket))
                        (reportMessage ?: ticketConflictCopy(ticket))?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = c.warning)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketDetailRow(label: String, value: String?) {
    val c = LocalGeoColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value?.takeIf(String::isNotBlank) ?: "미정", style = MaterialTheme.typography.bodyMedium,
            color = c.inkStrong)
    }
}
