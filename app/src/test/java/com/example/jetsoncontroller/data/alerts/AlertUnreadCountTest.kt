package com.example.jetsoncontroller.data.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertUnreadCountTest {

    private val now = 1_000_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun `counts only unread alerts raised within 24 hours`() {
        val alerts = listOf(
            alert("recent-unread", createdAt = now - 2 * hour, read = false),
            alert("recent-read", createdAt = now - 3 * hour, read = true),
            alert("old-unread", createdAt = now - 25 * hour, read = false)
        )

        assertEquals(1, countRecentUnread(alerts, now))
    }

    @Test
    fun `an alert exactly at the window edge no longer counts`() {
        val edge = alert("edge", createdAt = now - RECENT_UNREAD_WINDOW_MILLIS, read = false)

        assertEquals(0, countRecentUnread(listOf(edge), now))
    }

    @Test
    fun `an alert ages out of the count as time passes`() {
        val alerts = listOf(alert("a", createdAt = now - 23 * hour, read = false))

        assertEquals(1, countRecentUnread(alerts, now))
        assertEquals(0, countRecentUnread(alerts, now + 2 * hour))
    }

    private fun alert(id: String, createdAt: Long, read: Boolean) = AlertRecord(
        id = id,
        title = id,
        message = "message",
        destination = AlertDestination.DASHBOARD,
        severity = AlertSeverity.INFO,
        createdAtEpochMillis = createdAt,
        read = read
    )
}
