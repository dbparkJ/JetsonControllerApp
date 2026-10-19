package com.example.jetsoncontroller.data.server

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TicketCacheStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `ticket snapshots survive recreation and remain scoped by profile and device`() {
        val file = temporaryFolder.newFile("ticket-cache.json")
        val store = TicketCacheStore(file)
        val geometry = JsonParser.parseString("""{"type":"FeatureCollection","features":[]}""")
        store.save(snapshot("profile-a", "device-1", "2026-10-19T07:36:00Z", "row-a").copy(
            tickets = listOf(ServerTicket("row-a", "Route", revision = "ticket-4", features = geometry)),
            selectedUnit = "unit"
        ))
        store.save(snapshot("profile-a", "device-2", "2026-10-19T07:37:00Z", "row-b"))
        store.save(snapshot("profile-b", "device-1", "2026-10-19T07:38:00Z", "row-c"))

        val recreated = TicketCacheStore(file)
        val restored = requireNotNull(recreated.read("profile-a", "DEVICE-1"))
        assertEquals("2026-10-19T07:36:00Z", restored.cachedAt)
        assertEquals("unit", restored.selectedUnit)
        assertEquals("row-a", restored.tickets.single().rowId)
        assertEquals(geometry, restored.tickets.single().features)
        assertEquals("row-b", recreated.read("profile-a", "device-2")?.tickets?.single()?.rowId)
        assertEquals("row-c", recreated.read("profile-b", "device-1")?.tickets?.single()?.rowId)
        assertNull(recreated.read("profile-c", "device-1"))

        val serialized = file.readText(Charsets.UTF_8)
        assertFalse(serialized.contains("employeeToken", ignoreCase = true))
        assertFalse(serialized.contains("emp_secret"))
    }

    @Test
    fun `saving the same case insensitive device scope replaces its old snapshot`() {
        val file = temporaryFolder.newFile("ticket-cache.json")
        val store = TicketCacheStore(file)
        store.save(snapshot("profile", "Device-A", "first", "old"))
        store.save(snapshot("profile", "device-a", "second", "new"))

        val restored = requireNotNull(TicketCacheStore(file).read("profile", "DEVICE-A"))
        assertEquals("second", restored.cachedAt)
        assertEquals("new", restored.tickets.single().rowId)
        assertEquals(1, JsonParser.parseString(file.readText(Charsets.UTF_8))
            .asJsonObject.getAsJsonArray("snapshots").size())
    }

    private fun snapshot(profileId: String, deviceId: String, cachedAt: String, rowId: String) =
        TicketCacheSnapshot(
            profileId = profileId,
            deviceId = deviceId,
            tickets = listOf(ServerTicket(rowId, rowId)),
            units = listOf("unit"),
            deviceUnit = "unit",
            selectedUnit = null,
            capabilities = null,
            integration = null,
            cachedAt = cachedAt
        )
}
