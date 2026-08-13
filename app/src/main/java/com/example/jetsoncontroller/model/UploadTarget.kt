package com.example.jetsoncontroller.model

data class UploadTarget(
    val id: String,
    val label: String,
    val type: String? = null,
    val baseUrl: String? = null,
    val editable: Boolean = false
)
