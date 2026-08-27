package com.example.jetsoncontroller.model

data class CameraPreviewFrame(
    val bytes: ByteArray,
    val revision: Long? = null
)
