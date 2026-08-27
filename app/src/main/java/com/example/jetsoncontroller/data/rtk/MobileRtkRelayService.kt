package com.example.jetsoncontroller.data.rtk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.jetsoncontroller.JetsonApplication
import com.example.jetsoncontroller.MainActivity
import com.example.jetsoncontroller.R

class MobileRtkRelayService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "모바일 RTK 중계",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as JetsonApplication).repository.stopMobileRtkRelay()
            stopSelf()
            return START_NOT_STICKY
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopRelay = PendingIntent.getService(
            this,
            1,
            Intent(this, MobileRtkRelayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("모바일 데이터 RTK 중계 중")
            .setContentText("Wi-Fi Direct로 장치에 NTRIP 데이터를 전달합니다.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(0, "중지", stopRelay)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        internal const val CHANNEL_ID = "mobile_rtk_relay"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_STOP =
            "com.example.jetsoncontroller.action.STOP_MOBILE_RTK_RELAY"

        fun startIntent(context: Context): Intent =
            Intent(context, MobileRtkRelayService::class.java)

        fun stop(context: Context) {
            context.stopService(Intent(context, MobileRtkRelayService::class.java))
        }
    }
}
