package com.example.jetsoncontroller.ui.components

import com.example.jetsoncontroller.ui.theme.TextButton
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.alerts.AlertIconButton

/** Flexible height: device identity, title and availability must survive Android font scaling. */
@Composable
fun DeviceContextHeader(
    title: String, deviceName: String, connectionLabel: String,
    onDevices: () -> Unit, unreadCount: Int = 0, onAlerts: () -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDevices, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)) {
                    Text(deviceName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Default.ExpandMore, contentDescription = "장비 선택")
                }
                actions()
                AlertIconButton(unreadCount, onAlerts)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(connectionLabel, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
