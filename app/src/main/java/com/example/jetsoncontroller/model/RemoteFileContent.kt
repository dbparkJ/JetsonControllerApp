package com.example.jetsoncontroller.model

data class RemoteFileContent(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
)
