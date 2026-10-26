package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.ui.map.GeoGeometryState
import com.example.jetsoncontroller.ui.map.mapGeometry

/** Keep the existing order and objects for unchanged rows while accepting additions and removals. */
internal fun reconcileTickets(previous: List<ServerTicket>, incoming: List<ServerTicket>): List<ServerTicket> {
    val remaining = incoming.associateByTo(linkedMapOf()) { it.rowId }
    return buildList {
        previous.forEach { old ->
            remaining.remove(old.rowId)?.let { add(if (old == it) old else it) }
        }
        addAll(remaining.values)
    }
}

internal fun ticketHasMap(ticket: ServerTicket): Boolean = mapGeometry(ticket).state != GeoGeometryState.ABSENT

internal data class TicketValidationKey(val revision: String?, val geometryRevision: String?, val geometryHash: String?)

internal fun ServerTicket.validationKey() = TicketValidationKey(revision, zoneRevision, zoneHash)

internal fun displayedTicket(saved: ServerTicket?, tickets: List<ServerTicket>, rowId: String): ServerTicket? =
    tickets.firstOrNull { it.rowId == rowId } ?: saved
