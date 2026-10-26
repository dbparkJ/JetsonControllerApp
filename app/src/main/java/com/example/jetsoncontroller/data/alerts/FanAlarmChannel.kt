package com.example.jetsoncontroller.data.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings

internal const val FAN_ALARM_CHANNEL_ID = "jetson_fan_stall_alarm"

internal fun createFanAlarmChannel(
    sound: Uri = Settings.System.DEFAULT_ALARM_ALERT_URI
): NotificationChannel = NotificationChannel(
    FAN_ALARM_CHANNEL_ID,
    "냉각 팬 정지 경고",
    NotificationManager.IMPORTANCE_HIGH
).apply {
    description = "팬 구동 중 회전이 멈췄을 때 경고음 알림"
    setSound(
        sound,
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    )
}
