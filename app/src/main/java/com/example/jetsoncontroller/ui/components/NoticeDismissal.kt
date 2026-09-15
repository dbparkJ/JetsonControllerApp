package com.example.jetsoncontroller.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Session-scoped dismissal keys. A changed semantic key is shown as a new notice. */
@Stable
class NoticeDismissalState internal constructor(initialKeys: Set<String> = emptySet()) {
    private var keys by mutableStateOf(initialKeys)

    fun isDismissed(noticeKey: String): Boolean = noticeKey in keys

    fun dismiss(noticeKey: String) {
        keys = keys + noticeKey
    }

    companion object {
        val Saver: Saver<NoticeDismissalState, List<String>> = Saver(
            save = { it.keys.toList() },
            restore = { NoticeDismissalState(it.toSet()) }
        )
    }
}

private val LocalNoticeDismissals = staticCompositionLocalOf<NoticeDismissalState?> { null }

@Composable
fun rememberNoticeDismissalState(): NoticeDismissalState =
    rememberSaveable(saver = NoticeDismissalState.Saver) { NoticeDismissalState() }

@Composable
fun NoticeDismissalProvider(
    state: NoticeDismissalState = rememberNoticeDismissalState(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalNoticeDismissals provides state, content = content)
}

/** Use for explanatory help. Live errors and blocking state should remain condition-bound. */
@Composable
fun DismissibleNoticeBanner(
    noticeKey: String,
    message: String,
    tone: StatusTone = StatusTone.INFO,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val localState = LocalNoticeDismissals.current
    val fallbackState = rememberNoticeDismissalState()
    val state = localState ?: fallbackState
    if (!state.isDismissed(noticeKey)) {
        AppBanner(
            message = message,
            tone = tone,
            modifier = modifier,
            actionLabel = actionLabel,
            onAction = onAction,
            onDismiss = { state.dismiss(noticeKey) }
        )
    }
}
