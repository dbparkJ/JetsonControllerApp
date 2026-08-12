package com.example.jetsoncontroller.data.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.jetsoncontroller.MainActivity
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.data.repository.JetsonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DeviceAlertMonitor(
    private val context: Context,
    private val repository: JetsonRepository,
    private val preferences: AlertPreferencesStore,
    private val scope: CoroutineScope
) {
    fun start() {
        createChannel()
        scope.launch {
            combine(repository.status, preferences.settings) { status, settings ->
                status to settings
            }.collect { (status, settings) ->
                if (status.storageTotalBytes <= 0L && status.temperatureC <= 0f) {
                    return@collect
                }

                val decision = AlertThresholdEvaluator.evaluate(
                    status,
                    settings,
                    notificationsAllowed()
                )

                if (decision.notifyStorage) {
                    notify(
                        STORAGE_NOTIFICATION_ID,
                        "Jetson 저장공간 경고",
                        "저장공간 사용량이 ${status.storagePercent}%입니다. 수집 데이터를 확인하세요."
                    )
                }

                if (decision.notifyTemperature) {
                    notify(
                        TEMPERATURE_NOTIFICATION_ID,
                        "Jetson 온도 경고",
                        "장비 온도가 ${status.temperatureC} C입니다. 냉각 상태를 확인하세요."
                    )
                }

                if (decision.storageLatched != settings.storageAlertLatched ||
                    decision.temperatureLatched != settings.temperatureAlertLatched
                ) {
                    preferences.setLatches(
                        decision.storageLatched,
                        decision.temperatureLatched
                    )
                }
            }
        }
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Jetson 장비 경고",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "저장공간 및 장비 온도 임계치 알림"
            }
        )
    }

    private fun notify(id: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_ID = "jetson_device_alerts"
        const val STORAGE_NOTIFICATION_ID = 2001
        const val TEMPERATURE_NOTIFICATION_ID = 2002
    }
}
