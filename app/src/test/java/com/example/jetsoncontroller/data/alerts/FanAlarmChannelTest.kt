package com.example.jetsoncontroller.data.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class FanAlarmChannelTest {
    @Test fun `dedicated high importance channel uses alarm audio and supplied alarm uri`() {
        val sound = Mockito.mock(Uri::class.java)
        val audio = Mockito.mock(AudioAttributes::class.java)
        Mockito.mockConstruction(AudioAttributes.Builder::class.java) { builder, _ ->
            Mockito.`when`(builder.setUsage(Mockito.anyInt())).thenReturn(builder)
            Mockito.`when`(builder.setContentType(Mockito.anyInt())).thenReturn(builder)
            Mockito.`when`(builder.build()).thenReturn(audio)
        }.use { builders ->
            Mockito.mockConstruction(NotificationChannel::class.java) { _, construction ->
                assertEquals(FAN_ALARM_CHANNEL_ID, construction.arguments()[0])
                assertEquals(NotificationManager.IMPORTANCE_HIGH, construction.arguments()[2])
            }.use { channels ->
                val channel = createFanAlarmChannel(sound)
                assertEquals(channels.constructed().single(), channel)
                Mockito.verify(channel).setSound(sound, audio)
                Mockito.verify(builders.constructed().single()).setUsage(AudioAttributes.USAGE_ALARM)
                Mockito.verify(builders.constructed().single()).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                Mockito.verify(channel, Mockito.never()).setBypassDnd(Mockito.anyBoolean())
            }
        }
    }
}
