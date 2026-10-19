package com.example.jetsoncontroller.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.jetsoncontroller.ui.components.geo.GeoSnackbar
import com.example.jetsoncontroller.ui.components.geo.showGeoSnackbar
import com.example.jetsoncontroller.ui.components.geo.snackbarAction

/** Consume on timeout, dismissal, or navigation so completed operations never replay. */
@Composable
fun OperationMessageHost(
    message: String?,
    onDismissMessage: (String) -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val host = remember { SnackbarHostState() }
    val dismissMessage by rememberUpdatedState(onDismissMessage)
    LaunchedEffect(message) {
        val shown = message ?: return@LaunchedEffect
        val action = snackbarAction(actionLabel, onAction)
        try {
            val result = host.showGeoSnackbar(
                shown,
                actionLabel = action?.label,
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) action?.onClick?.invoke()
        } finally {
            dismissMessage(shown)
        }
    }
    GeoSnackbar(host, Modifier.testTag("operation-message"))
}
