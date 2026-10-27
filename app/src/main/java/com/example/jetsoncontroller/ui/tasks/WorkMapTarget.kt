package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.PendingTicketSelection
import com.example.jetsoncontroller.data.server.ServerTicket

/** Resolve geometry by the bound ids; display names never select a map. */
internal fun workMapTicket(
    deviceId: String?, profileId: String?, pipelineId: String,
    preparation: TicketPreparation?, selection: PendingTicketSelection?, tickets: ServerTicketUiState
): ServerTicket? {
    if (deviceId.isNullOrBlank() || profileId.isNullOrBlank() || pipelineId.isBlank()) return null
    val prepared = preparation?.takeIf {
        it.deviceId.equals(deviceId, true) && it.profileId == profileId && it.pipelineId == pipelineId
    }
    val pending = selection?.takeIf {
        it.deviceId.equals(deviceId, true) && it.profileId == profileId && it.pipelineId == pipelineId
    }
    val rowId = pending?.rowId ?: prepared?.ticket?.rowId ?: return null
    val live = tickets.takeIf { it.deviceId.equals(deviceId, true) && it.profileId == profileId }
    if (live?.credentialRevision != null && prepared != null && live.credentialRevision != prepared.credentialRevision) return null
    return live?.tickets?.singleOrNull { it.rowId == rowId }
        ?: prepared?.ticket?.takeIf { it.rowId == rowId }
}
