package com.example.jetsoncontroller.data.alerts

import android.content.Context
import android.content.Intent
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class DeviceAlertMonitorFanTest {
    @Test fun `permission denied still writes error history with captured device and sensor destination`() = runTest {
        val context = Mockito.mock(Context::class.java)
        val repository = Mockito.mock(JetsonRepository::class.java)
        val preferences = Mockito.mock(AlertPreferencesStore::class.java)
        val history = Mockito.mock(AlertHistoryStore::class.java)
        Mockito.`when`(repository.registeredDevices).thenReturn(MutableStateFlow(emptyList()))
        val monitor = DeviceAlertMonitor(context, repository, preferences, history, backgroundScope)
        val connected = TransportState.Connected(TransportType.LAN, "192.168.1.10", "device-a", "GEO-X-1")
        Mockito.`when`(repository.transportState).thenReturn(MutableStateFlow(connected))

        monitor.publishFanStallAlert(connected, allowed = false, episodeId = "episode-a")

        Mockito.verify(history).addFanStall(
            "episode-a",
            "GEO-X-1 냉각 팬이 멈췄습니다",
            "팬 구동 출력이 있지만 회전수가 0 RPM으로 계속 확인됩니다. 수집을 중지하고 냉각 팬을 점검하세요.",
            AlertTarget("device-a", "GEO-X-1")
        )
        Mockito.verifyNoInteractions(context)
    }

    @Test fun `notification failure propagates for retry with stable tag and only alert once`() = runTest {
        val context = Mockito.mock(Context::class.java)
        val repository = Mockito.mock(JetsonRepository::class.java)
        val preferences = Mockito.mock(AlertPreferencesStore::class.java)
        val history = Mockito.mock(AlertHistoryStore::class.java)
        val manager = Mockito.mock(NotificationManager::class.java)
        val notification = Mockito.mock(Notification::class.java)
        val pendingIntent = Mockito.mock(PendingIntent::class.java)
        val connected = TransportState.Connected(TransportType.LAN, "192.168.1.10", "device-a", "GEO-X-1")
        Mockito.`when`(repository.registeredDevices).thenReturn(MutableStateFlow(emptyList()))
        Mockito.`when`(repository.transportState).thenReturn(MutableStateFlow(connected))
        Mockito.`when`(context.getSystemService(NotificationManager::class.java)).thenReturn(manager)
        Mockito.doThrow(IllegalStateException("notification failed")).doNothing()
            .`when`(manager).notify("episode-a", 2003, notification)
        val monitor = DeviceAlertMonitor(context, repository, preferences, history, backgroundScope)
        Mockito.mockConstruction(Intent::class.java).use {
            Mockito.mockStatic(PendingIntent::class.java).use { pending ->
                pending.`when`<PendingIntent> {
                    PendingIntent.getActivity(Mockito.eq(context), Mockito.eq(2003), Mockito.any(Intent::class.java), Mockito.anyInt())
                }.thenReturn(pendingIntent)
                Mockito.mockConstruction(NotificationCompat.BigTextStyle::class.java,
                    Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF)).use {
                    Mockito.mockConstruction(NotificationCompat.Builder::class.java,
                        Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF)) { builder, _ ->
                        Mockito.`when`(builder.build()).thenReturn(notification)
                    }.use { builders ->
                        var failures = 0
                        try {
                            monitor.publishFanStallAlert(connected, true, "episode-a")
                        } catch (_: IllegalStateException) { failures++ }
                        assertEquals(1, failures)
                        monitor.publishFanStallAlert(connected, true, "episode-a")
                        Mockito.verify(manager, Mockito.times(2)).notify("episode-a", 2003, notification)
                        assertEquals(2, builders.constructed().size)
                        builders.constructed().forEach { Mockito.verify(it).setOnlyAlertOnce(true) }
                    }
                }
            }
        }
        Mockito.verify(history, Mockito.times(2)).addFanStall(
            "episode-a", "GEO-X-1 냉각 팬이 멈췄습니다",
            "팬 구동 출력이 있지만 회전수가 0 RPM으로 계속 확인됩니다. 수집을 중지하고 냉각 팬을 점검하세요.",
            AlertTarget("device-a", "GEO-X-1")
        )
    }
}
