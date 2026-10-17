package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.jetsoncontroller.ui.theme.AppSpacing

/**
 * Connection notice for the screen below it: one inline banner above the content.
 *
 * The banner pushes the content down, so it never covers a title. It takes the status bar
 * inset itself, and the content then consumes that inset, so the screen's own top bar does
 * not add the same padding a second time. When the connection is fine there is no banner
 * and the content gets the inset as before.
 */
@Composable
internal fun ConnectionRecoveryLayout(
    @Suppress("UNUSED_PARAMETER") deviceId: String?,
    connectionAvailable: Boolean,
    message: String?,
    onResolveConnection: () -> Unit,
    tone: StatusTone = StatusTone.WARNING,
    content: @Composable (Modifier) -> Unit
) {
    val showBanner = !connectionAvailable && message != null
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (message != null && !connectionAvailable) {
                AppBanner(
                    message = message,
                    tone = tone,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                        .padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small),
                    actionLabel = "연결 문제 해결",
                    onAction = onResolveConnection
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (showBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars)
                        else Modifier
                    )
            ) {
                // The activity already uses adjustResize. Consuming the IME inset here keeps
                // focused controls from being covered on edge-to-edge devices.
                content(Modifier.fillMaxSize().imePadding())
            }
        }
    }
}
