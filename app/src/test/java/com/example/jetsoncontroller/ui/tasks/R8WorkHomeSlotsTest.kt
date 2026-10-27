package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.ui.components.geo.r8UiSource
import org.junit.Assert.*
import org.junit.Test

class R8WorkHomeSlotsTest {
    @Test fun featuredDeadlineAndRecentReceiptsUseTheirSharedSlots() {
        val home = r8UiSource("tasks/TasksHomeScreen.kt")
        assertTrue(home.contains("meta = current.project, deadline = \"마감 ${'$'}{ticketDueLabel(current.dueDate)}\""))
        val recent = home.substringAfter("val recentContent").substringBefore("val recent =")
        assertTrue(recent.contains("GeoRowList(GeoRowTrailing.Status)"))
        assertTrue(recent.contains("badge = { GeoReceiptLine(row.receipt, compact = true) }"))
        assertFalse(recent.contains("extra ="))
        assertTrue(r8UiSource("field/RunResultScreen.kt").contains("GeoReceipt(chain.receipt)"))
    }
}
