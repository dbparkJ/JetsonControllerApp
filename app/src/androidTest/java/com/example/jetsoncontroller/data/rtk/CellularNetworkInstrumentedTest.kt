package com.example.jetsoncontroller.data.rtk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.app.NotificationManager
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CellularNetworkInstrumentedTest {
    @Test
    fun cellularNetworkCanResolveNtripCasterWithoutChangingProcessNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val available = CountDownLatch(1)
        val selected = AtomicReference<Network?>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                selected.set(network)
                available.countDown()
            }

            override fun onUnavailable() {
                available.countDown()
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivity.requestNetwork(request, callback, 15_000)
        try {
            assertTrue("cellular network was not available", available.await(20, TimeUnit.SECONDS))
            val network = selected.get()
            assertNotNull("cellular network request timed out", network)
            val capabilities = connectivity.getNetworkCapabilities(network)
            assertTrue(
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            )
            assertTrue(network!!.getAllByName("www.gnssdata.or.kr").isNotEmpty())
        } finally {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
    }

    @Test
    fun mobileRtkForegroundServiceStartsFromTheApplication() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ContextCompat.startForegroundService(
            context,
            MobileRtkRelayService.startIntent(context)
        )
        try {
            val notifications = context.getSystemService(NotificationManager::class.java)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (
                notifications.activeNotifications.none {
                    it.notification.channelId == "mobile_rtk_relay"
                } && System.nanoTime() < deadline
            ) {
                Thread.sleep(50)
            }
            assertTrue(
                notifications.activeNotifications.any {
                    it.notification.channelId == "mobile_rtk_relay"
                }
            )
        } finally {
            context.stopService(Intent(context, MobileRtkRelayService::class.java))
        }
    }
}
