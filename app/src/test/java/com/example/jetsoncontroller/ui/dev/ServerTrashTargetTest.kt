package com.example.jetsoncontroller.ui.dev

import org.junit.Assert.*
import org.junit.Test

class ServerTrashTargetTest {
    @Test fun onlyTheConfirmedShownSubsetCanBePurgedOnTheSameProfile() {
        val frozen = ServerPurgeTarget("production", listOf("shown-a", "shown-b"))
        assertTrue(frozen.valid("production", listOf("shown-a", "shown-b", "new-c"), true, false))
        assertEquals(listOf("shown-a", "shown-b"), frozen.sessions)
        assertFalse(frozen.valid("test", frozen.sessions, true, false))
        assertFalse(frozen.valid("production", listOf("shown-a"), true, false))
        assertFalse(frozen.valid("production", frozen.sessions, false, false))
        assertFalse(frozen.valid("production", frozen.sessions, true, true))
    }

    @Test fun emptyDuplicateAndOversizeRequestsStayDisabled() {
        assertFalse(ServerPurgeTarget("p", emptyList()).valid("p", emptyList(), true, false))
        assertFalse(ServerPurgeTarget("p", listOf("a", "a")).valid("p", listOf("a"), true, false))
        val tooMany = (1..201).map(Int::toString)
        assertFalse(ServerPurgeTarget("p", tooMany).valid("p", tooMany, true, false))
    }
}
