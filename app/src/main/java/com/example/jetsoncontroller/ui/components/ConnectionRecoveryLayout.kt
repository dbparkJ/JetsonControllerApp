package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.jetsoncontroller.ui.theme.AppSpacing

@Composable
internal fun ConnectionRecoveryLayout(
    message: String?,
    onResolveConnection: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // The footer owns the bottom system inset while visible. The screen
            // retains its top inset and has space reserved for the notice.
            val screenModifier = if (message != null) {
                Modifier.weight(1f).consumeWindowInsets(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
            } else {
                Modifier.weight(1f)
            }
            content(screenModifier)
            if (message != null) {
                AppBanner(
                    message = message,
                    tone = StatusTone.INFO,
                    actionLabel = "연결 문제 해결",
                    onAction = onResolveConnection,
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                            )
                        )
                        .padding(
                            horizontal = AppSpacing.screen,
                            vertical = AppSpacing.small
                        )
                )
            }
        }
    }
}
