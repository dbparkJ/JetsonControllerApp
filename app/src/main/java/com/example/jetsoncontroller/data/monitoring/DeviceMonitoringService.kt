package com.example.jetsoncontroller.data.monitoring

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.jetsoncontroller.JetsonApplication
import com.example.jetsoncontroller.MainActivity
import com.example.jetsoncontroller.BluetoothPermissionPolicy
import com.example.jetsoncontroller.WifiPermissionPolicy
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Keeps the Application-owned connection, alerts and RTK recovery alive without an Activity. */
class DeviceMonitoringService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "장치 연결", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            preferences.edit().putBoolean(REQUESTED, false).apply()
            (application as JetsonApplication).repository.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null && !preferences.getBoolean(REQUESTED, false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val disconnect = PendingIntent.getService(this, 0,
            Intent(this, DeviceMonitoringService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("장치 연결 유지 중")
            .setContentText("수집 상태와 전송 진행 상황을 확인합니다.")
            .setContentIntent(openApp).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "연결 해제", disconnect).build()
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (error: SecurityException) {
            ConnectionDiagnostics.record("monitor_service_start_failed",
                mapOf("reasonCode" to error.javaClass.simpleName))
            preferences.edit().putBoolean(REQUESTED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        } catch (error: IllegalStateException) {
            ConnectionDiagnostics.record("monitor_service_start_failed",
                mapOf("reasonCode" to error.javaClass.simpleName))
            preferences.edit().putBoolean(REQUESTED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        preferences.edit().putBoolean(REQUESTED, true).apply()
        val repository = (application as JetsonApplication).repository
        if (!observing) {
            observing = true
            // Reuse the 10/23 connection policy and stored target after a sticky process restart.
            fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            repository.configureAutomaticConnectivity(
                enabled = true,
                localNetworkPermissionGranted = Build.VERSION.SDK_INT < 37 || granted(Manifest.permission.ACCESS_LOCAL_NETWORK),
                nearbyWifiPermissionGranted = WifiPermissionPolicy.hasNearbyPermissions(Build.VERSION.SDK_INT, ::granted),
                bluetoothPermissionGranted = BluetoothPermissionPolicy.hasPermissions(Build.VERSION.SDK_INT, ::granted)
            )
            scope.launch {
                combine(repository.automaticConnectionRequested, repository.registeredDeviceSnapshot) { requested, devices ->
                    requested && (devices == null || devices.isNotEmpty())
                }.collect { requested ->
                    if (!requested) {
                        preferences.edit().putBoolean(REQUESTED, false).apply()
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "device_monitoring"
        private const val NOTIFICATION_ID = 4103
        private const val PREFERENCES = "device_monitoring"
        private const val REQUESTED = "requested"
        private const val ACTION_DISCONNECT = "com.example.jetsoncontroller.action.STOP_DEVICE_MONITORING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DeviceMonitoringService::class.java))
        }
    }
}
