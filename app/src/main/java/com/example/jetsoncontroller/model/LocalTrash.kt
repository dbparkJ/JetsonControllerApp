package com.example.jetsoncontroller.model

import com.google.gson.JsonElement

data class TrashEntry(
    val trashId: String,
    val category: String,
    val state: String,
    val rootId: String? = null,
    val relativePath: String? = null,
    val name: String,
    val entryType: String? = null,
    val trashedAt: String? = null,
    val restoredAt: String? = null,
    val updatedAt: String? = null,
    val lastError: String? = null,
    val metadata: JsonElement? = null,
    val audit: JsonElement? = null,
    val restoreSupported: Boolean = false
)

data class TrashEntriesResponse(
    val entries: List<TrashEntry> = emptyList(),
    val refreshedAt: String? = null
)
