package com.example.jetsoncontroller.model

data class JetsonStatus(
    val cpuPercent: Int = 0,
    val gpuPercent: Int = 0,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val temperatureC: Float = 0f,
    val storagePercent: Int = 0,
    val storageUsedBytes: Long = 0,
    val storageTotalBytes: Long = 0,
    val storageAvailableBytes: Long = 0,
    val cameraRunning: Boolean = false,
    val lidarRunning: Boolean = false,
    val gnssRunning: Boolean = false,
    val mmsRunning: Boolean = false
)
