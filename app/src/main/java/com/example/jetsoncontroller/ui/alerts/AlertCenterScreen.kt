package com.example.jetsoncontroller.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.alerts.AlertDestination
import com.example.jetsoncontroller.data.alerts.AlertRecord
import com.example.jetsoncontroller.data.alerts.AlertSeverity
import com.example.jetsoncontroller.ui.components.EmptyState
import com.example.jetsoncontroller.ui.theme.AppSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertIconButton(
    unreadCount: Int,
    onClick: () -> Unit
) {
    BadgedBox(
        badge = {
            if (unreadCount > 0) {
                Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
            }
        }
    ) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = if (unreadCount > 0) "알림 ${unreadCount}개" else "알림"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertCenterScreen(
    state: AlertCenterUiState,
    onBack: () -> Unit,
    onAlertClick: (AlertRecord) -> Unit,
    onDelete: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onClear: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("알림 내역을 모두 삭제할까요?") },
            text = { Text("삭제한 알림 내역은 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("전체 삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("알림")
                        Text(
                            if (state.unreadCount > 0) "읽지 않은 알림 ${state.unreadCount}개" else "모든 알림을 확인했습니다",
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
                    if (state.unreadCount > 0) {
                        IconButton(onClick = onMarkAllRead) {
                            Icon(Icons.Default.DoneAll, contentDescription = "모두 읽음")
                        }
                    }
                    if (state.alerts.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "알림 전체 삭제")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.alerts.isEmpty()) {
            EmptyState(
                title = "새 알림이 없습니다",
                message = "장비 상태와 작업 알림이 이곳에 표시됩니다.",
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(bottom = AppSpacing.section)
            ) {
                items(state.alerts, key = { it.id }) { alert ->
                    AlertRow(
                        alert = alert,
                        onClick = { onAlertClick(alert) },
                        onDelete = { onDelete(alert.id) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    alert: AlertRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = alertIcon(alert.destination)
    ListItem(
        headlineContent = {
            Text(
                alert.title,
                fontWeight = if (alert.read) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(alert.message, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    formatAlertTime(alert.createdAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            BadgedBox(badge = { if (!alert.read) Badge() }) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = alertColor(alert.severity),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "알림 삭제")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun alertIcon(destination: AlertDestination): ImageVector = when (destination) {
    AlertDestination.DASHBOARD -> Icons.Default.Notifications
    AlertDestination.STORAGE -> Icons.Default.Storage
    AlertDestination.SENSORS -> Icons.Default.Thermostat
    AlertDestination.PIPELINES -> Icons.AutoMirrored.Filled.PlaylistPlay
    AlertDestination.UPLOAD_QUEUE -> Icons.Default.CloudUpload
}

@Composable
private fun alertColor(severity: AlertSeverity): Color = when (severity) {
    AlertSeverity.INFO -> MaterialTheme.colorScheme.secondary
    AlertSeverity.SUCCESS -> MaterialTheme.colorScheme.primary
    AlertSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
    AlertSeverity.ERROR -> MaterialTheme.colorScheme.error
}

private fun formatAlertTime(epochMillis: Long): String = ALERT_TIME_FORMATTER.format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
)

private val ALERT_TIME_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
