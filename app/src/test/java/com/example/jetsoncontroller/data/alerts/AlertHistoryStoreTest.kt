package com.example.jetsoncontroller.data.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertHistoryStoreTest {
    @Test
    fun `new alerts are newest first and bounded`() {
        val old = alert("old", 1L)
        val middle = alert("middle", 2L)
        val newest = alert("newest", 3L)

        val result = appendAlert(listOf(middle, old), newest, limit = 2)

        assertEquals(listOf("newest", "middle"), result.map { it.id })
    }

    @Test
    fun `adding the same id replaces the previous record`() {
        val unread = alert("same", 1L)
        val read = unread.copy(read = true, createdAtEpochMillis = 2L)

        val result = appendAlert(listOf(unread), read)

        assertEquals(1, result.size)
        assertTrue(result.single().read)
    }

    private fun alert(id: String, timestamp: Long) = AlertRecord(
        id = id,
        title = id,
        message = "message",
        destination = AlertDestination.DASHBOARD,
        severity = AlertSeverity.INFO,
        createdAtEpochMillis = timestamp
    )
}
