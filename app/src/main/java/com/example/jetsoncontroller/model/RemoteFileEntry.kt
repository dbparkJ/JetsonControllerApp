package com.example.jetsoncontroller.model

enum class RemoteEntryType {
    DIRECTORY,
    FILE
}

data class RemoteFileEntry(
    val name: String,
    val relativePath: String,
    val type: RemoteEntryType,
    val sizeBytes: Long?,
    val modifiedAt: String?
)

data class DeviceStorageDeletion(
    val rootId: String,
    val relativePath: String,
    val name: String,
    val type: RemoteEntryType,
    val state: String,
    val deletedAt: String?
)
