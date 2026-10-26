package com.example.jetsoncontroller.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

internal const val TicketRefreshIntervalMillis = 30_000L

/** Entering or resuming always refreshes immediately; leaving cancels the request and timer. */
internal suspend fun refreshTicketsWhileVisible(lifecycle: Lifecycle, refresh: suspend () -> Unit) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (true) {
            refresh()
            delay(TicketRefreshIntervalMillis)
        }
    }
}

@Composable
internal fun TicketRefreshEffect(
    viewModel: ServerTicketViewModel, profileId: String?, deviceId: String?,
    credentialRevision: String?, enabled: Boolean = true
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle, profileId, deviceId, credentialRevision, enabled) {
        if (!enabled || profileId.isNullOrBlank() || deviceId.isNullOrBlank()) return@LaunchedEffect
        refreshTicketsWhileVisible(lifecycle) {
            val request = viewModel.refresh(profileId, deviceId, showLoading = false)
            try {
                request?.join()
            } finally {
                // refresh() belongs to viewModelScope, so explicitly release our request on exit.
                if (request?.isActive == true) request.cancel()
            }
        }
    }
}
