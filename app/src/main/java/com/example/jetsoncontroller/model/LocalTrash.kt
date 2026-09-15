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
    val restoreSupported: Boolean = false,
    val purgeSupported: Boolean = false
)

data class TrashEntriesResponse(
    val entries: List<TrashEntry> = emptyList(),
    val refreshedAt: String? = null,
    val emptySupported: Boolean = false
)

data class EmptyTrashRequest(
    val confirmed: Boolean = true,
    val trashIds: List<String>
)

data class EmptyTrashResult(
    val trashId: String,
    val state: String,
    val error: String? = null
)

data class EmptyTrashResponse(
    val results: List<EmptyTrashResult>,
    val refreshedAt: String? = null
)
