package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.jetsoncontroller.ui.theme.AppSpacing
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors

@Composable
internal fun ConnectionRecoveryLayout(
    deviceId: String?,
    connectionAvailable: Boolean,
    message: String?,
    onResolveConnection: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val snackbarHost = remember { SnackbarHostState() }
    var announcedDevices by rememberSaveable { mutableStateOf(listOf<String>()) }
    val resolveConnection by rememberUpdatedState(onResolveConnection)
    val deviceKey = deviceId ?: "<no-device>"

    // Consume once per device's disconnected period, not once per destination.
    // Leaving a screen or restoring it must not replay the same notice.
    LaunchedEffect(deviceKey, connectionAvailable, message) {
        if (connectionAvailable) {
            announcedDevices = announcedDevices - deviceKey
        } else if (message != null && deviceKey !in announcedDevices) {
            announcedDevices = announcedDevices + deviceKey
            if (snackbarHost.showSnackbar(
                    message = message,
                    actionLabel = "연결 문제 해결",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                ) == SnackbarResult.ActionPerformed
            ) {
                resolveConnection()
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // Each screen keeps its full height and its own bottom system inset.
            content(Modifier.fillMaxSize())
            val colors = LocalCobaltColors.current
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                    ))
                    .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    actionOnNewLine = true,
                    containerColor = colors.infoBg,
                    contentColor = colors.info,
                    actionColor = colors.info,
                    dismissActionContentColor = colors.info
                )
            }
        }
    }
}
