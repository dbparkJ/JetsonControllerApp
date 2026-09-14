package com.example.jetsoncontroller.data.network

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.JetsonApplication
import com.example.jetsoncontroller.MainActivity
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.EndpointTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in field test: uses stored pairing credentials and real TLS/HMAC responses. */
@RunWith(AndroidJUnit4::class)
class LanReconnectInstrumentedTest {
    @Test
    fun authenticatedWifiRecoversWhenDiscoveryChangesToAnotherInterface() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val deviceId = arguments.getString("fieldDeviceId")
        val reachableHost = arguments.getString("fieldReachableHost")
        val alternateHost = arguments.getString("fieldAlternateHost")
        assumeTrue("Requires an explicitly selected, paired field device",
            deviceId != null && reachableHost != null && alternateHost != null)
        val port = arguments.getString("fieldPort")?.toInt() ?: 8765
        val repository = ApplicationProvider.getApplicationContext<JetsonApplication>().repository
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val endpoint = DeviceEndpoint(deviceId!!, "Field test", reachableHost!!, port, EndpointTransport.LAN)

        ActivityScenario.launch(MainActivity::class.java).use {
            suspend fun connect(candidate: DeviceEndpoint, timeoutMillis: Long = 20_000) {
                instrumentation.runOnMainSync {
                    repository.disconnect()
                    repository.connectLan(candidate)
                }
                val connected = withTimeout(timeoutMillis) {
                    repository.transportState.first { state ->
                        state is TransportState.Connected && state.type == TransportType.LAN &&
                            state.deviceId.equals(deviceId, ignoreCase = true)
                    }
                } as TransportState.Connected
                assertEquals("$reachableHost:$port", connected.endpoint)
                assertTrue("Authenticated status must work after reconnect", repository.refreshStatus())
            }

            try {
                connect(endpoint)
                // A new manager must also recover after the app process is recreated.
                val restored = LanDiscoveryManager(ApplicationProvider.getApplicationContext())
                    .reconnectEndpoint(endpoint.copy(host = alternateHost!!))
                assertEquals(reachableHost, restored.host)
                // Finish before the unreachable interface's 5-second socket timeout;
                // a later mDNS rediscovery must not mask a failed address selection.
                repeat(3) { connect(endpoint.copy(host = alternateHost!!), timeoutMillis = 4_000) }
            } finally {
                instrumentation.runOnMainSync { repository.startLanDiscovery() }
            }
        }
    }
}
