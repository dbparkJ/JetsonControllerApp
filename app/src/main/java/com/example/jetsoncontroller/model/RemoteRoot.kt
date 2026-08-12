package com.example.jetsoncontroller.model

data class RemoteRoot(
    val id: String,
    val label: String,
    val pathHint: String?,
    val totalBytes: Long? = null,
    val usedBytes: Long? = null,
    val availableBytes: Long? = null
)
