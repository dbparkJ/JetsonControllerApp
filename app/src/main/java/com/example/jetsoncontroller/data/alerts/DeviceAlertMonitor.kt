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
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceAlertMonitor(
    private val context: Context,
    private val repository: JetsonRepository,
    private val preferences: AlertPreferencesStore,
    private val history: AlertHistoryStore,
    private val scope: CoroutineScope
) {
    fun start() {
        createChannels()
        startHealthAlerts()
        startPipelineAlerts()
        startUploadAlerts()
    }

    private fun startHealthAlerts() {
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

                if (decision.storageTriggered) {
                    publishAlert(
                        notificationId = STORAGE_NOTIFICATION_ID,
                        channelId = HEALTH_CHANNEL_ID,
                        title = "Jetson 저장공간 경고",
                        message = "저장공간 사용량이 ${status.storagePercent}%입니다. 수집 데이터를 확인하세요.",
                        destination = AlertDestination.STORAGE,
                        severity = AlertSeverity.WARNING,
                        showSystemNotification = decision.notifyStorage
                    )
                }

                if (decision.temperatureTriggered) {
                    publishAlert(
                        notificationId = TEMPERATURE_NOTIFICATION_ID,
                        channelId = HEALTH_CHANNEL_ID,
                        title = "Jetson 온도 경고",
                        message = "장비 온도가 ${status.temperatureC} C입니다. 냉각 상태를 확인하세요.",
                        destination = AlertDestination.SENSORS,
                        severity = AlertSeverity.WARNING,
                        showSystemNotification = decision.notifyTemperature
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

    private fun startPipelineAlerts() {
        scope.launch {
            repository.transportState.collectLatest { transport ->
                if (transport !is TransportState.Connected || transport.type == TransportType.BLE) {
                    return@collectLatest
                }

                var previousStates: Map<String, PipelineState>? = null
                while (currentCoroutineContext().isActive) {
                    val pipelines = repository.getPipelines().getOrNull()
                    if (pipelines != null) {
                        val decision = PipelineAlertEvaluator.evaluate(previousStates, pipelines)
                        previousStates = decision.currentStates
                        val settings = preferences.settings.first()

                        if (settings.pipelineStartedEnabled) {
                            decision.started.forEach { pipeline ->
                                publishAlert(
                                    notificationId = pipelineNotificationId(
                                        PIPELINE_STARTED_NOTIFICATION_BASE,
                                        pipeline.id
                                    ),
                                    channelId = PIPELINE_CHANNEL_ID,
                                    title = "작업 시작됨",
                                    message = "${pipeline.label} 작업이 실행을 시작했습니다.",
                                    destination = AlertDestination.PIPELINES,
                                    severity = AlertSeverity.INFO,
                                    showSystemNotification = notificationsAllowed()
                                )
                            }
                        }
                        if (settings.pipelineFailedEnabled) {
                            decision.failed.forEach { pipeline ->
                                publishAlert(
                                    notificationId = pipelineNotificationId(
                                        PIPELINE_FAILED_NOTIFICATION_BASE,
                                        pipeline.id
                                    ),
                                    channelId = PIPELINE_CHANNEL_ID,
                                    title = "작업 오류 종료",
                                    message = "${pipeline.label} 작업이 오류로 종료되었습니다. 로그를 확인하세요.",
                                    destination = AlertDestination.PIPELINES,
                                    severity = AlertSeverity.ERROR,
                                    showSystemNotification = notificationsAllowed()
                                )
                            }
                        }
                    }
                    delay(PIPELINE_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun startUploadAlerts() {
        scope.launch {
            var previousStates: Map<String, UploadJobState>? = null
            repository.transportState.collectLatest { transport ->
                if (transport !is TransportState.Connected || transport.type == TransportType.BLE) {
                    return@collectLatest
                }

                while (currentCoroutineContext().isActive) {
                    val jobs = repository.getUploadJobs(activeOnly = false).getOrNull()
                    if (jobs != null) {
                        val decision = UploadAlertEvaluator.evaluate(previousStates, jobs)
                        previousStates = decision.currentStates
                        val settings = preferences.settings.first()

                        if (settings.uploadStartedEnabled) {
                            decision.started.forEach { job ->
                                publishAlert(
                                    notificationId = uploadNotificationId(
                                        UPLOAD_STARTED_NOTIFICATION_BASE,
                                        job.id
                                    ),
                                    channelId = UPLOAD_CHANNEL_ID,
                                    title = "업로드 시작됨",
                                    message = "${uploadDisplayName(job)} 데이터를 서버로 전송하기 시작했습니다.",
                                    destination = AlertDestination.UPLOAD_QUEUE,
                                    severity = AlertSeverity.INFO,
                                    showSystemNotification = notificationsAllowed()
                                )
                            }
                        }
                        if (settings.uploadEndedEnabled) {
                            decision.ended.forEach { job ->
                                val (title, message) = uploadEndMessage(job)
                                publishAlert(
                                    notificationId = uploadNotificationId(
                                        UPLOAD_ENDED_NOTIFICATION_BASE,
                                        job.id
                                    ),
                                    channelId = UPLOAD_CHANNEL_ID,
                                    title = title,
                                    message = message,
                                    destination = AlertDestination.UPLOAD_QUEUE,
                                    severity = uploadEndSeverity(job),
                                    showSystemNotification = notificationsAllowed()
                                )
                            }
                        }
                    }
                    delay(UPLOAD_POLL_INTERVAL_MS)
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

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                HEALTH_CHANNEL_ID,
                "Jetson 장비 경고",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "저장공간 및 장비 온도 임계치 알림"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                UPLOAD_CHANNEL_ID,
                "Jetson 업로드 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "수집 데이터 업로드 시작 및 종료 알림"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PIPELINE_CHANNEL_ID,
                "Jetson 작업 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "자동 실행 작업 시작 및 오류 종료 알림"
            }
        )
    }

    private suspend fun publishAlert(
        notificationId: Int,
        channelId: String,
        title: String,
        message: String,
        destination: AlertDestination,
        severity: AlertSeverity,
        showSystemNotification: Boolean
    ) {
        history.add(title, message, destination, severity)
        if (showSystemNotification) {
            notify(notificationId, channelId, title, message)
        }
    }

    private fun notify(id: Int, channelId: String, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
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

    private fun pipelineNotificationId(base: Int, pipelineId: String): Int =
        base + (pipelineId.hashCode() and PIPELINE_NOTIFICATION_ID_MASK)

    private fun uploadNotificationId(base: Int, uploadId: String): Int =
        base + (uploadId.hashCode() and PIPELINE_NOTIFICATION_ID_MASK)

    private fun uploadDisplayName(job: UploadJob): String =
        job.relativePath.substringAfterLast('/').ifBlank { "수집 데이터" }

    private fun uploadEndMessage(job: UploadJob): Pair<String, String> {
        val name = uploadDisplayName(job)
        return when (job.state) {
            UploadJobState.COMPLETED -> "업로드 완료" to "$name 데이터 전송이 완료되었습니다."
            UploadJobState.FAILED -> "업로드 종료" to "$name 데이터 전송이 실패했습니다."
            UploadJobState.CANCELLED -> "업로드 종료" to "$name 데이터 전송이 취소되었습니다."
            else -> "업로드 종료" to "$name 데이터 전송이 종료되었습니다."
        }
    }

    private fun uploadEndSeverity(job: UploadJob): AlertSeverity = when (job.state) {
        UploadJobState.COMPLETED -> AlertSeverity.SUCCESS
        UploadJobState.FAILED -> AlertSeverity.ERROR
        else -> AlertSeverity.INFO
    }

    private companion object {
        const val HEALTH_CHANNEL_ID = "jetson_device_alerts"
        const val PIPELINE_CHANNEL_ID = "jetson_pipeline_alerts"
        const val UPLOAD_CHANNEL_ID = "jetson_upload_alerts"
        const val STORAGE_NOTIFICATION_ID = 2001
        const val TEMPERATURE_NOTIFICATION_ID = 2002
        const val PIPELINE_STARTED_NOTIFICATION_BASE = 10_000
        const val PIPELINE_FAILED_NOTIFICATION_BASE = 30_000
        const val UPLOAD_STARTED_NOTIFICATION_BASE = 50_000
        const val UPLOAD_ENDED_NOTIFICATION_BASE = 70_000
        const val PIPELINE_NOTIFICATION_ID_MASK = 0x3fff
        const val PIPELINE_POLL_INTERVAL_MS = 5_000L
        const val UPLOAD_POLL_INTERVAL_MS = 2_000L
    }
}
