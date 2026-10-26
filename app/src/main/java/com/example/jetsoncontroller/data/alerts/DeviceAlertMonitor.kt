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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun formatAlertTemperature(celsius: Float): String =
    if (celsius.isFinite() && celsius == celsius.toInt().toFloat()) {
        celsius.toInt().toString() + "°C"
    } else {
        formatCelsius(celsius)
    }

internal fun storageAlertTitle(deviceName: String?, percent: Int): String =
    alertTitle(deviceName, "저장 공간 사용량 " + percent + "%입니다")

internal fun temperatureAlertTitle(deviceName: String?, celsius: Float): String =
    alertTitle(deviceName, "온도가 " + formatAlertTemperature(celsius) + "입니다")

internal fun pipelineStartedAlertTitle(deviceName: String?, pipelineName: String): String =
    alertTitle(deviceName, pipelineName + " 작업이 시작되었습니다")

internal fun pipelineFailedAlertTitle(deviceName: String?, pipelineName: String): String =
    alertTitle(deviceName, pipelineName + " 작업이 오류로 끝났습니다")

internal fun uploadStartedAlertTitle(deviceName: String?, uploadName: String): String =
    alertTitle(deviceName, uploadName + " 전송을 시작했습니다")

internal fun uploadEndedAlertTitle(
    deviceName: String?,
    uploadName: String,
    completed: Boolean,
    cancelled: Boolean
): String = alertTitle(
    deviceName,
    when {
        completed -> uploadName + " 전송을 마쳤습니다"
        cancelled -> uploadName + " 전송을 취소했습니다"
        else -> uploadName + " 전송에 실패했습니다"
    }
)

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
        startFanAlerts()
        startPipelineAlerts()
        startUploadAlerts()
    }

    private fun startFanAlerts() {
        scope.launch {
            FanAlertPoller(
                transportState = repository.transportState,
                readFan = repository::getFanStatus,
                latches = preferences,
                notificationsAllowed = ::notificationsAllowed,
                publish = ::publishFanStallAlert
            ).run()
        }
    }

    internal suspend fun publishFanStallAlert(
        transport: TransportState.Connected,
        allowed: Boolean,
        episodeId: String
    ) {
        val registeredName = repository.registeredDevices.value.firstOrNull {
            it.deviceId.equals(transport.deviceId, ignoreCase = true)
        }?.deviceName
        val deviceName = transport.deviceName?.takeIf { it.isNotBlank() } ?: registeredName
        val title = fanStallAlertTitle(deviceName)
        val message = "팬 구동 출력이 있지만 회전수가 0 RPM으로 계속 확인됩니다. 수집을 중지하고 냉각 팬을 점검하세요."
        history.addFanStall(
            episodeId, title, message, AlertTarget(requireNotNull(transport.deviceId), deviceName)
        )
        currentCoroutineContext().ensureActive()
        if (repository.transportState.value !== transport) return
        if (allowed) notify(FAN_STALL_NOTIFICATION_ID, FAN_ALARM_CHANNEL_ID, title, message, episodeId)
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
                        title = storageAlertTitle(alertDeviceName(), status.storagePercent),
                        message = "기준 " + settings.storageThresholdPercent +
                            "%를 넘었습니다. 수집 데이터를 확인하세요.",
                        destination = AlertDestination.STORAGE,
                        severity = AlertSeverity.WARNING,
                        target = alertTarget(),
                        showSystemNotification = decision.notifyStorage
                    )
                }

                if (decision.temperatureTriggered) {
                    publishAlert(
                        notificationId = TEMPERATURE_NOTIFICATION_ID,
                        channelId = HEALTH_CHANNEL_ID,
                        title = temperatureAlertTitle(alertDeviceName(), status.temperatureC),
                        message = "기준 " + settings.temperatureThresholdC +
                            "°C를 넘었습니다. 냉각 상태를 확인하세요.",
                        destination = AlertDestination.SENSORS,
                        severity = AlertSeverity.WARNING,
                        target = alertTarget(),
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
                                    title = pipelineStartedAlertTitle(alertDeviceName(), pipeline.label),
                                    message = "작업 실행을 확인하세요.",
                                    destination = AlertDestination.PIPELINES,
                                    severity = AlertSeverity.INFO,
                                    target = transport.deviceId?.let { id -> AlertTarget(id, transport.deviceName, pipeline.id,
                                        pipeline.activeRunId?.takeIf { it.isNotBlank() } ?: pipeline.execution?.runId?.takeIf { it.isNotBlank() }) },
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
                                    title = pipelineFailedAlertTitle(alertDeviceName(), pipeline.label),
                                    message = "작업이 오류로 끝났습니다. 실행 로그를 확인하세요.",
                                    destination = AlertDestination.PIPELINES,
                                    severity = AlertSeverity.ERROR,
                                    target = transport.deviceId?.let { id -> AlertTarget(id, transport.deviceName, pipeline.id,
                                        pipeline.activeRunId?.takeIf { it.isNotBlank() } ?: pipeline.execution?.runId?.takeIf { it.isNotBlank() }) },
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
                                    title = uploadStartedAlertTitle(
                                        alertDeviceName(), uploadDisplayName(job)
                                    ),
                                    message = "${uploadDisplayName(job)} 데이터를 서버로 전송하기 시작했습니다.",
                                    destination = AlertDestination.UPLOAD_QUEUE,
                                    severity = AlertSeverity.INFO,
                                    target = transport.deviceId?.let { AlertTarget(it, transport.deviceName, transferJob = job) },
                                    showSystemNotification = notificationsAllowed()
                                )
                            }
                        }
                        if (settings.uploadEndedEnabled) {
                            decision.ended.forEach { job ->
                                val (title, message) = uploadEndMessage(alertDeviceName(), job)
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
                                    target = transport.deviceId?.let { AlertTarget(it, transport.deviceName, transferJob = job) },
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
        manager.createNotificationChannel(createFanAlarmChannel())
        manager.createNotificationChannel(
            NotificationChannel(
                HEALTH_CHANNEL_ID,
                "장치 경고",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "저장 공간과 장치 온도 기준 알림"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                UPLOAD_CHANNEL_ID,
                "장치 업로드 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "수집 데이터 업로드 시작 및 종료 알림"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PIPELINE_CHANNEL_ID,
                "장치 작업 알림",
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
        showSystemNotification: Boolean,
        target: AlertTarget? = null
    ) {
        history.add(title, message, destination, severity, target)
        if (showSystemNotification) {
            notify(notificationId, channelId, title, message)
        }
    }

    private fun notify(id: Int, channelId: String, title: String, message: String, fanEpisodeId: String? = null) {
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
            .apply { if (fanEpisodeId != null) setOnlyAlertOnce(true) }
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        if (fanEpisodeId != null) manager.notify(fanEpisodeId, id, notification)
        else manager.notify(id, notification)
    }

    private fun pipelineNotificationId(base: Int, pipelineId: String): Int =
        base + (pipelineId.hashCode() and PIPELINE_NOTIFICATION_ID_MASK)

    private fun uploadNotificationId(base: Int, uploadId: String): Int =
        base + (uploadId.hashCode() and PIPELINE_NOTIFICATION_ID_MASK)

    private fun uploadDisplayName(job: UploadJob): String =
        job.relativePath.substringAfterLast('/').ifBlank { "수집 데이터" }

    private fun alertTarget(): AlertTarget? {
        val connected = repository.transportState.value as? TransportState.Connected ?: return null
        return connected.deviceId?.let { AlertTarget(it, connected.deviceName) }
    }

    /** The device the alert is about: the connected device first, then the registered one. */
    private fun alertDeviceName(): String {
        val connected = repository.transportState.value as? TransportState.Connected
        val deviceId = connected?.deviceId ?: repository.selectedDeviceId.value
        val registeredName = repository.registeredDevices.value
            .firstOrNull { deviceId != null && it.deviceId.equals(deviceId, ignoreCase = true) }
            ?.deviceName
        val connectedName = connected?.deviceName?.takeIf { it.isNotBlank() }
        return readableDeviceName(connectedName ?: registeredName)
    }

    private fun uploadEndMessage(deviceName: String, job: UploadJob): Pair<String, String> {
        val name = uploadDisplayName(job)
        return when (job.state) {
            UploadJobState.COMPLETED ->
                uploadEndedAlertTitle(deviceName, name, completed = true, cancelled = false) to
                    "$name 데이터 전송이 완료되었습니다."
            UploadJobState.FAILED ->
                uploadEndedAlertTitle(deviceName, name, completed = false, cancelled = false) to
                    "$name 데이터 전송이 실패했습니다."
            UploadJobState.CANCELLED ->
                uploadEndedAlertTitle(deviceName, name, completed = false, cancelled = true) to
                    "$name 데이터 전송이 취소되었습니다."
            else ->
                alertTitle(deviceName, "업로드 종료") to "$name 데이터 전송이 종료되었습니다."
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
        const val FAN_STALL_NOTIFICATION_ID = 2003
        const val PIPELINE_STARTED_NOTIFICATION_BASE = 10_000
        const val PIPELINE_FAILED_NOTIFICATION_BASE = 30_000
        const val UPLOAD_STARTED_NOTIFICATION_BASE = 50_000
        const val UPLOAD_ENDED_NOTIFICATION_BASE = 70_000
        const val PIPELINE_NOTIFICATION_ID_MASK = 0x3fff
        const val PIPELINE_POLL_INTERVAL_MS = 5_000L
        const val UPLOAD_POLL_INTERVAL_MS = 2_000L
    }
}
