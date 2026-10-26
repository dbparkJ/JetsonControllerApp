package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.FanStatus
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

internal interface FanStallLatchStore {
    /** Returns a durable pending episode, or null when this fault was already delivered. */
    suspend fun claimFanStall(deviceId: String): String?
    suspend fun pendingFanStall(deviceId: String): String?
    suspend fun completeFanStall(deviceId: String, episodeId: String)
    suspend fun clearFanStall(deviceId: String)
}

internal fun fanLatchKey(deviceId: String): String = deviceId.trim().lowercase(Locale.ROOT)

/** No shared UI state is updated by this polling loop. */
internal class FanAlertPoller(
    private val transportState: StateFlow<TransportState>,
    private val readFan: suspend () -> Result<FanStatus>,
    private val latches: FanStallLatchStore,
    private val notificationsAllowed: () -> Boolean,
    private val publish: suspend (TransportState.Connected, Boolean, String) -> Unit
) {
    suspend fun run() {
        transportState.collectLatest { transport ->
            if (transport !is TransportState.Connected ||
                transport.type !in setOf(TransportType.LAN, TransportType.WIFI_DIRECT) ||
                transport.deviceId.isNullOrBlank() || transport.endpoint.isNullOrBlank()
            ) return@collectLatest

            val evaluator = FanStallEvaluator()
            while (currentCoroutineContext().isActive) {
                try {
                    val status = try {
                        readFan().getOrNull()
                    } catch (cancelled: CancellationException) {
                        // The repository also cancels stale generations with identical public state.
                        currentCoroutineContext().ensureActive()
                        evaluator.reset()
                        null
                    } catch (_: Exception) {
                        null
                    }
                    currentCoroutineContext().ensureActive()
                    if (transportState.value !== transport) return@collectLatest
                    val decision = evaluator.evaluate(status)
                    val pending = if (decision == FanStallDecision.STALLED) {
                        latches.claimFanStall(transport.deviceId)
                    } else {
                        latches.pendingFanStall(transport.deviceId)
                    }
                    currentCoroutineContext().ensureActive()
                    if (transportState.value !== transport) return@collectLatest
                    if (pending != null) {
                        // History is unconditional; permission gates only the system notification.
                        publish(transport, notificationsAllowed(), pending)
                        currentCoroutineContext().ensureActive()
                        if (transportState.value !== transport) return@collectLatest
                        latches.completeFanStall(transport.deviceId, pending)
                    }
                    if (decision == FanStallDecision.RECOVERED) {
                        currentCoroutineContext().ensureActive()
                        if (transportState.value !== transport) return@collectLatest
                        latches.clearFanStall(transport.deviceId)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed store/history/notification operation keeps its pending episode.
                    // The regular interval also backs off retries without stopping other devices.
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    internal companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
