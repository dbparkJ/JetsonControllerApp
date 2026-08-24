package com.example.jetsoncontroller.model

data class SystemTimeStatus(
    val synchronized: Boolean,
    val deviceTimeEpochMillis: Long,
    val source: String? = null,
    val sourceTimeEpochMillis: Long? = null,
    val synchronizedAtEpochMillis: Long? = null,
    val offsetBeforeMillis: Long? = null,
    val clockChanged: Boolean = false
)

data class FanStatus(
    val available: Boolean,
    val mode: String,
    val percent: Int? = null,
    val rpm: Int? = null,
    val pwm: Int? = null,
    val maxPwm: Int? = null,
    val controller: String? = null,
    val autoAvailable: Boolean = false,
    val minimumManualPercent: Int = 20
)
