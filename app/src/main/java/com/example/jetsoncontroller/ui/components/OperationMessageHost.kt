package com.example.jetsoncontroller.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Consume on timeout, dismissal, or navigation so completed operations never replay. */
@Composable
fun OperationMessageHost(message: String?, onDismissMessage: (String) -> Unit) {
    val host = remember { SnackbarHostState() }
    val dismissMessage by rememberUpdatedState(onDismissMessage)
    LaunchedEffect(message) {
        val shown = message ?: return@LaunchedEffect
        try {
            host.showSnackbar(shown, withDismissAction = true, duration = SnackbarDuration.Short)
        } finally {
            dismissMessage(shown)
        }
    }
    SnackbarHost(host) { data ->
        Snackbar(modifier = Modifier.testTag("operation-message"), dismissAction = {
            IconButton(onClick = { data.dismiss() }) { Icon(Icons.Default.Close, "메시지 닫기") }
        }) { Text(data.visuals.message) }
    }
}
