package com.example.jetsoncontroller.model

data class WifiProvisionRequest(
    val ssid: String,
    val password: String,
    val hidden: Boolean = false
)
