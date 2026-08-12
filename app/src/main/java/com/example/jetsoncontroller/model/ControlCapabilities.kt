package com.example.jetsoncontroller.model

data class ControlCapabilities(
    val systemControlConfigured: Boolean = false,
    val powerCommandsEnabled: Boolean = false,
    val fileBrowsing: Boolean = false,
    val uploads: Boolean = false,
    val wifiProvisioning: Boolean = true,
    val pipelines: Boolean = false
)

data class ControlOperationState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)
