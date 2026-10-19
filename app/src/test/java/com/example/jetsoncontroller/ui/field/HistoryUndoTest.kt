package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.TrashEntry
import org.junit.Assert.*
import org.junit.Test

class HistoryUndoTest {
    private val target = HistoryUndoTarget("device", "trash-1", "capture/run.log")
    private val entry = TrashEntry("trash-1", "RUN_HISTORY", "TRASHED", relativePath = "capture/run.log",
        name = "run.log", restoreSupported = true)

    @Test fun `undo restores only the captured trash item and original path`() {
        assertTrue(historyUndoMatches(target, entry))
        assertFalse(historyUndoMatches(target, entry.copy(trashId = "trash-2")))
        assertFalse(historyUndoMatches(target, entry.copy(relativePath = "capture/other.log")))
        assertFalse(historyUndoMatches(target, entry.copy(category = "DEVICE_FILE")))
    }

    @Test fun `purged restored missing and unsupported trash items cannot be restored`() {
        assertFalse(historyUndoMatches(target, null))
        assertFalse(historyUndoMatches(target, entry.copy(state = "RESTORED")))
        assertFalse(historyUndoMatches(target, entry.copy(state = "PURGED")))
        assertFalse(historyUndoMatches(target, entry.copy(restoreSupported = false)))
    }
}
