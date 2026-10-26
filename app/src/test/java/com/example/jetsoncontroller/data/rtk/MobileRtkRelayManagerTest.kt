package com.example.jetsoncontroller.data.rtk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.jetsoncontroller.data.network.LocalApiClient
import com.example.jetsoncontroller.data.network.WifiDirectManager
import com.example.jetsoncontroller.model.MobileRtkRelayState
import com.example.jetsoncontroller.model.MobileRtkRelayConfig
import java.io.IOException
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import java.lang.reflect.InvocationTargetException

@OptIn(ExperimentalCoroutinesApi::class)
class MobileRtkRelayManagerTest {
    @Test
    fun `new mobile lease waits for internet validation without rejecting normal network startup`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        try {
            val cellular = fixture.cellularRoute(validated = false)
            val validation = async { fixture.manager.awaitInternet(cellular) }
            runCurrent()
            assertFalse(validation.isCompleted)
            val caps = fixture.manager.field<ConnectivityManager>("connectivityManager").getNetworkCapabilities(cellular)!!
            Mockito.`when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
            advanceTimeBy(250)
            runCurrent()
            validation.await()
        } finally { fixture.scope.cancel() }
    }

    @Test
    fun `mobile network without internet returns explicit error after bounded validation`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        try {
            val cellular = fixture.cellularRoute(validated = false)
            val validation = async {
                try {
                    fixture.manager.awaitInternet(cellular)
                    null
                } catch (error: IOException) { error }
            }
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertTrue(validation.await()?.message.orEmpty().contains("모바일 데이터"))
        } finally { fixture.scope.cancel() }
    }

    private fun ManagerFixture.cellularRoute(validated: Boolean): Network {
        val cellular = Mockito.mock(Network::class.java)
        val caps = Mockito.mock(NetworkCapabilities::class.java)
        Mockito.`when`(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true)
        Mockito.`when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        Mockito.`when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)).thenReturn(true)
        Mockito.`when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(validated)
        val cm = manager.field<ConnectivityManager>("connectivityManager")
        Mockito.`when`(cm.activeNetwork).thenReturn(cellular)
        Mockito.`when`(cm.getNetworkCapabilities(cellular)).thenReturn(caps)
        return cellular
    }

    private suspend fun MobileRtkRelayManager.awaitInternet(cellular: Network): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            try {
                javaClass.declaredMethods.single { it.name == "awaitMobileInternet" }
                    .apply { isAccessible = true }.invoke(this, cellular, continuation)
            } catch (error: InvocationTargetException) { throw error.targetException }
        }

    @Test
    fun `same caster on another pipeline shares socket and transfers heartbeat without acquisition ownership`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java, Mockito.withSettings().defaultAnswer { invocation ->
            if (invocation.method.name.substringBefore('-') == "getMobileRtkRelayConfig") {
                MobileRtkRelayConfig("other", true, "caster.example", 2101)
            } else org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
        })
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)
                fixture.manager.field<Any>("currentSession").setField("config",
                    MobileRtkRelayConfig("capture", true, "caster.example", 2101))
                fixture.manager.prepareForMonitoring("capture", client).getOrThrow()
                val acquisition = fixture.manager.prepare("other", client).getOrThrow()
                    as MobileRtkRelayManager.Preparation.Ready
                assertFalse(acquisition.newlyCreated)
                assertTrue(acquisition.server === server)
                assertEquals("other", fixture.state.value.pipelineId)
                assertFalse(fixture.manager.stopIfPrepared(client, acquisition))
                assertFalse(fixture.manager.stopAfterAcquisition(client))
                assertFalse(server.isClosed)
                Mockito.verify(client).unregisterMobileRtkRelay("capture")
                advanceTimeBy(10_000)
                runCurrent()
                Mockito.verify(client, Mockito.times(2)).registerMobileRtkRelay("other", server.localPort)
            } finally { fixture.scope.cancel() }
        }
    }

    @Test
    fun `lost cellular lease releases foreground relay for bounded monitor retry and ignores stale callbacks`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        val callback = Mockito.mock(ConnectivityManager.NetworkCallback::class.java)
        val network = Mockito.mock(Network::class.java)
        val leaseClass = Class.forName("com.example.jetsoncontroller.data.rtk.MobileRtkRelayManager\$CellularLease")
        val lease = leaseClass.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(network, callback)
        ServerSocket().use { server ->
            ServerSocket().use { replacement ->
                try {
                    fixture.activate(client, server)
                    fixture.manager.setField("cellularLease", lease)
                    val oldSession = fixture.manager.field<Any>("currentSession")
                    fixture.manager.call("invalidateCellular", oldSession, callback, network, "모바일 데이터 연결이 끊어졌습니다.")
                    assertTrue(server.isClosed)
                    assertFalse(fixture.state.value.active)
                    assertEquals("모바일 데이터 연결이 끊어졌습니다.", fixture.state.value.error)
                    Mockito.verify(client).unregisterMobileRtkRelay("capture")
                    fixture.activate(client, replacement)
                    fixture.manager.setField("cellularLease", lease)
                    fixture.manager.call("invalidateCellular", oldSession, callback, network, "late loss")
                    assertFalse(replacement.isClosed)
                    assertTrue(fixture.state.value.active)
                    assertEquals(null, fixture.state.value.error)
                    Mockito.verifyNoMoreInteractions(client)
                } finally { fixture.scope.cancel() }
            }
        }
    }

    @Test
    fun `other acquisition config cannot retire the shared sensor monitor`() = runTest {
        for (available in listOf(false, true)) {
            val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            val client = Mockito.mock(LocalApiClient::class.java, Mockito.withSettings().defaultAnswer { invocation ->
                if (invocation.method.name.substringBefore('-') == "getMobileRtkRelayConfig") {
                    MobileRtkRelayConfig("other", available, "different.example", 2101)
                } else org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
            })
            ServerSocket().use { server ->
                try {
                    fixture.activate(client, server)
                    fixture.manager.field<Any>("currentSession").setField("config",
                        MobileRtkRelayConfig("capture", true, "original.example", 2101))
                    fixture.manager.prepareForMonitoring("capture", client).getOrThrow()
                    val prepared = fixture.manager.prepare("other", client)
                    if (available) assertTrue(prepared.isFailure)
                    else assertTrue(prepared.getOrThrow() is MobileRtkRelayManager.Preparation.NotRequired)
                    assertTrue(fixture.state.value.active)
                    assertEquals("capture", fixture.state.value.pipelineId)
                    assertFalse(server.isClosed)
                    Mockito.verify(client, Mockito.never()).unregisterMobileRtkRelay(Mockito.anyString())
                } finally { fixture.scope.cancel() }
            }
        }
    }

    @Test
    fun `monitor promotion keeps shared relay after acquisition cleanup and releases on disconnect`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)
                val monitoring = fixture.manager.prepareForMonitoring("capture", client).getOrThrow()
                    as MobileRtkRelayManager.Preparation.Ready
                assertFalse(monitoring.newlyCreated)
                for (newlyCreated in listOf(true, false)) {
                    assertFalse(fixture.manager.stopIfPrepared(client,
                        MobileRtkRelayManager.Preparation.Ready(newlyCreated, server)))
                }
                assertFalse(fixture.manager.stopAfterAcquisition(client))
                assertTrue(fixture.state.value.active)
                assertFalse(server.isClosed)
                assertTrue(fixture.manager.stopIfOwnedBy(client))
                assertTrue(server.isClosed)
                assertFalse(fixture.state.value.active)
                Mockito.verify(client).unregisterMobileRtkRelay("capture")
            } finally { fixture.scope.cancel() }
        }
    }

    @Test
    fun `acquisition reusing monitor cannot acquire cleanup ownership`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)
                fixture.manager.prepareForMonitoring("capture", client).getOrThrow()
                val acquisition = fixture.manager.prepare("capture", client).getOrThrow()
                    as MobileRtkRelayManager.Preparation.Ready
                assertFalse(acquisition.newlyCreated)
                assertFalse(fixture.manager.stopIfPrepared(client, acquisition))
                assertFalse(fixture.manager.stopAfterAcquisition(client))
                assertFalse(server.isClosed)
                Mockito.verify(client, Mockito.never()).unregisterMobileRtkRelay(Mockito.anyString())
            } finally { fixture.scope.cancel() }
        }
    }

    @Test
    fun `acquisition completion releases unshared relay but never another client`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)
                assertFalse(fixture.manager.stopAfterAcquisition(Mockito.mock(LocalApiClient::class.java)))
                assertFalse(server.isClosed)
                assertTrue(fixture.manager.stopAfterAcquisition(client))
                assertTrue(server.isClosed)
            } finally { fixture.scope.cancel() }
        }
    }

    @Test
    fun `late heartbeat completion cannot change new session with same client and pipeline`() = runTest {
        for (failed in listOf(false, true)) {
            val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            ServerSocket().use { oldServer ->
                ServerSocket().use { newServer ->
                    val replacement = MobileRtkRelayState(active = true, pipelineId = "capture",
                        bytesFromCaster = 91, error = "replacement error")
                    val client = Mockito.mock(LocalApiClient::class.java, Mockito.withSettings().defaultAnswer { invocation ->
                        if (invocation.method.name.substringBefore('-') == "registerMobileRtkRelay") {
                            fixture.manager.call("releaseLocalResourcesLocked")
                            fixture.activate(invocation.mock as LocalApiClient, newServer)
                            fixture.state.value = replacement
                            if (failed) Class.forName("kotlin.ResultKt")
                                .getMethod("createFailure", Throwable::class.java).invoke(null, IOException("late heartbeat"))
                            else Unit
                        } else org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
                    })
                    try {
                        fixture.activate(client, oldServer)
                        val session = fixture.manager.field<Any>("currentSession")
                        fixture.manager.call("startHeartbeat", "capture", oldServer.localPort, client, session)
                        advanceTimeBy(10_000)
                        runCurrent()
                        Mockito.verify(client).registerMobileRtkRelay("capture", oldServer.localPort)
                        assertEquals(replacement, fixture.state.value)
                        assertFalse(newServer.isClosed)
                    } finally { fixture.scope.cancel() }
                }
            }
        }
    }

    @Test
    fun `successful heartbeat keeps caster error and belongs to cancellable session`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)
                val session = fixture.manager.field<Any>("currentSession")
                fixture.manager.call("dataError", session, "caster failed")
                fixture.manager.call("startHeartbeat", "capture", server.localPort, client, session)
                advanceTimeBy(10_000)
                runCurrent()
                Mockito.verify(client).registerMobileRtkRelay("capture", server.localPort)
                assertEquals("caster failed", fixture.state.value.error)
                fixture.manager.call("releaseLocalResourcesLocked")
                val replacement = fixture.state.value.copy(pipelineId = "replacement", error = "new error")
                fixture.state.value = replacement
                advanceTimeBy(20_000)
                runCurrent()
                Mockito.verifyNoMoreInteractions(client)
                assertEquals(replacement, fixture.state.value)
            } finally { fixture.scope.cancel() }
        }
    }

    private fun Any.call(name: String, vararg args: Any?): Any? = javaClass.declaredMethods
        .single { it.name == name }.apply { isAccessible = true }.invoke(this, *args)

    @Test
    fun `rewrites local relay host while preserving authorization and gga bytes`() {
        val request = (
            "GET /YANJ-RTCM31 HTTP/1.0\r\n" +
                "Host: 192.168.49.71\r\n" +
                "Authorization: Basic c2VjcmV0\r\n" +
                "\r\n" +
                "${'$'}GPGGA,relay-payload\r\n"
            ).toByteArray(Charsets.ISO_8859_1)

        val rewritten = rewriteNtripHostHeader(
            request,
            "www.gnssdata.or.kr",
            2101
        ).toString(Charsets.ISO_8859_1)

        assertTrue(rewritten.contains("Host: www.gnssdata.or.kr:2101\r\n"))
        assertTrue(rewritten.contains("Authorization: Basic c2VjcmV0\r\n"))
        assertTrue(rewritten.endsWith("${'$'}GPGGA,relay-payload\r\n"))
    }

    @Test
    fun `adds a missing host header without changing the request target`() {
        val request = "GET /MOUNT HTTP/1.0\nUser-Agent: test\n\n".toByteArray()

        val rewritten = rewriteNtripHostHeader(request, "caster.example", 80)
            .toString(Charsets.ISO_8859_1)

        assertEquals(
            "GET /MOUNT HTTP/1.0\nHost: caster.example\nUser-Agent: test\n\n",
            rewritten
        )
    }

    @Test
    fun `late cleanup for an old client cannot stop the new client relay`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val oldClient = Mockito.mock(LocalApiClient::class.java)
        val newClient = Mockito.mock(LocalApiClient::class.java)
        val server = ServerSocket()
        try {
            fixture.manager.setField("activeClient", newClient)
            fixture.manager.setField("serverSocket", server)
            fixture.state.value = MobileRtkRelayState(active = true, pipelineId = "capture")

            assertFalse(fixture.manager.stopIfOwnedBy(oldClient))

            assertFalse(server.isClosed)
            assertTrue(fixture.state.value.active)
            Mockito.verifyNoInteractions(oldClient)
        } finally {
            server.close()
            fixture.scope.cancel()
        }
    }

    @Test
    fun `same pipeline on a different client is not reused`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val oldClient = Mockito.mock(LocalApiClient::class.java)
        val newClient = Mockito.mock(LocalApiClient::class.java)
        val server = ServerSocket()
        try {
            fixture.manager.setField("activeClient", oldClient)
            fixture.manager.setField("serverSocket", server)
            fixture.state.value = MobileRtkRelayState(active = true, pipelineId = "capture")

            val prepared = fixture.manager.prepare("capture", newClient)

            assertTrue(prepared.isFailure)
            assertTrue("The old relay socket must be retired before preparing the new client", server.isClosed)
            Mockito.verify(newClient).getMobileRtkRelayConfig("capture")
            assertTrue(Mockito.mockingDetails(newClient).invocations.none {
                it.method.name.substringBefore('-') == "registerMobileRtkRelay"
            })
        } finally {
            server.close()
            fixture.scope.cancel()
        }
    }

    @Test
    fun `failed preparation unregisters the attempted relay and releases sockets`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        try {
            assertTrue(fixture.manager.prepare("capture", client).isFailure)
            Mockito.verify(client).unregisterMobileRtkRelay("capture")
            assertFalse(fixture.state.value.active)
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `cancelled preparation unregisters before propagating cancellation`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java, Mockito.withSettings().defaultAnswer { invocation ->
            if (invocation.method.name.substringBefore('-') == "getMobileRtkRelayConfig") {
                throw CancellationException("preparation cancelled")
            }
            org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
        })
        try {
            try {
                fixture.manager.prepare("capture", client)
                throw AssertionError("Cancellation must propagate")
            } catch (_: CancellationException) {
                Mockito.verify(client).unregisterMobileRtkRelay("capture")
                assertFalse(fixture.state.value.active)
            }
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `same client and pipeline reuse returns no new cleanup ownership`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)

                val prepared = fixture.manager.prepare("capture", client).getOrThrow()
                    as MobileRtkRelayManager.Preparation.Ready

                assertFalse(prepared.newlyCreated)
                assertTrue(prepared.server === server)
                assertTrue(fixture.state.value.active)
                assertFalse(server.isClosed)
                Mockito.verify(client).registerMobileRtkRelay("capture", server.localPort)
                Mockito.verifyNoMoreInteractions(client)
            } finally {
                fixture.scope.cancel()
            }
        }
    }

    @Test
    fun `reuse lease refresh failure keeps sockets state and registration ownership`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val failure = IOException("lease refresh failed")
        val client = Mockito.mock(LocalApiClient::class.java, Mockito.withSettings().defaultAnswer { invocation ->
            if (invocation.method.name.substringBefore('-') == "registerMobileRtkRelay") {
                Class.forName("kotlin.ResultKt").getMethod("createFailure", Throwable::class.java)
                    .invoke(null, failure)
            } else org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
        })
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)

                assertTrue(fixture.manager.prepare("capture", client).exceptionOrNull() === failure)

                assertFalse(server.isClosed)
                assertTrue(fixture.state.value.active)
                assertTrue(fixture.manager.field<LocalApiClient>("activeClient") === client)
                Mockito.verify(client).registerMobileRtkRelay("capture", server.localPort)
                Mockito.verifyNoMoreInteractions(client)
                Mockito.verify(fixture.appContext, Mockito.never()).stopService(Mockito.any())
            } finally {
                fixture.scope.cancel()
            }
        }
    }

    @Test
    fun `reuse refresh cancellation propagates without stopping running relay`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java, Mockito.withSettings().defaultAnswer { invocation ->
            if (invocation.method.name.substringBefore('-') == "registerMobileRtkRelay") {
                throw CancellationException("refresh cancelled")
            }
            org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)
        })
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)
                try {
                    fixture.manager.prepare("capture", client)
                    throw AssertionError("Cancellation must propagate")
                } catch (_: CancellationException) {
                    assertFalse(server.isClosed)
                    assertTrue(fixture.state.value.active)
                    Mockito.verify(client).registerMobileRtkRelay("capture", server.localPort)
                    Mockito.verifyNoMoreInteractions(client)
                    Mockito.verify(fixture.appContext, Mockito.never()).stopService(Mockito.any())
                }
            } finally {
                fixture.scope.cancel()
            }
        }
    }

    @Test
    fun `different pipeline on same client acquires independently and cleans only attempted registration`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        ServerSocket().use { server ->
            try {
                fixture.activate(client, server)

                assertTrue(fixture.manager.prepare("other-pipeline", client).isFailure)

                assertTrue(server.isClosed)
                Mockito.verify(client).getMobileRtkRelayConfig("other-pipeline")
                Mockito.verify(client).unregisterMobileRtkRelay("other-pipeline")
                Mockito.verifyNoMoreInteractions(client)
            } finally {
                fixture.scope.cancel()
            }
        }
    }

    @Test
    fun `old preparation token cannot stop a replacement relay even on same client and pipeline`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val client = Mockito.mock(LocalApiClient::class.java)
        ServerSocket().use { oldServer ->
            ServerSocket().use { newServer ->
                try {
                    val oldPreparation = MobileRtkRelayManager.Preparation.Ready(true, oldServer)
                    for (pipeline in listOf("capture", "other-pipeline")) {
                        fixture.activate(client, newServer, pipeline)

                        assertFalse(fixture.manager.stopIfPrepared(client, oldPreparation))

                        assertFalse(newServer.isClosed)
                        assertTrue(fixture.state.value.active)
                    }
                    Mockito.verifyNoInteractions(client)
                } finally {
                    fixture.scope.cancel()
                }
            }
        }
    }

    @Test
    fun `preparation cleanup also requires identical client ownership`() = runTest {
        val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val oldClient = Mockito.mock(LocalApiClient::class.java)
        val newClient = Mockito.mock(LocalApiClient::class.java)
        ServerSocket().use { server ->
            try {
                fixture.activate(newClient, server)
                val preparation = MobileRtkRelayManager.Preparation.Ready(true, server)

                assertFalse(fixture.manager.stopIfPrepared(oldClient, preparation))

                assertFalse(server.isClosed)
                assertTrue(fixture.state.value.active)
                Mockito.verifyNoInteractions(oldClient, newClient)
            } finally {
                fixture.scope.cancel()
            }
        }
    }

    @Test
    fun `matching preparation token releases new or reused terminal relay exactly once`() = runTest {
        for (newlyCreated in listOf(true, false)) {
            val fixture = managerFixture(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            val client = Mockito.mock(LocalApiClient::class.java)
            ServerSocket().use { server ->
                try {
                    fixture.activate(client, server)
                    val preparation = MobileRtkRelayManager.Preparation.Ready(newlyCreated, server)

                    assertTrue(fixture.manager.stopIfPrepared(client, preparation))
                    assertFalse(fixture.manager.stopIfPrepared(client, preparation))

                    assertTrue(server.isClosed)
                    assertFalse(fixture.state.value.active)
                    Mockito.verify(client).unregisterMobileRtkRelay("capture")
                    Mockito.verifyNoMoreInteractions(client)
                } finally {
                    fixture.scope.cancel()
                }
            }
        }
    }

    private fun managerFixture(scope: CoroutineScope): ManagerFixture {
        val context = Mockito.mock(Context::class.java)
        val appContext = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(appContext)
        Mockito.`when`(appContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(Mockito.mock(ConnectivityManager::class.java))
        val wifiDirect = Mockito.mock(WifiDirectManager::class.java)
        val manager = MobileRtkRelayManager(context, wifiDirect, scope)
        return ManagerFixture(
            manager,
            scope,
            manager.field<MutableStateFlow<MobileRtkRelayState>>("_state"),
            appContext
        )
    }

    private data class ManagerFixture(
        val manager: MobileRtkRelayManager,
        val scope: CoroutineScope,
        val state: MutableStateFlow<MobileRtkRelayState>,
        val appContext: Context
    )

    private fun ManagerFixture.activate(client: LocalApiClient, server: ServerSocket, pipelineId: String = "capture") {
        manager.javaClass.getDeclaredMethod("createSession").apply { isAccessible = true }.invoke(manager)
        manager.setField("activeClient", client)
        manager.setField("serverSocket", server)
        state.value = MobileRtkRelayState(active = true, pipelineId = pipelineId)
    }

    private fun Any.setField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.field(name: String): T =
        javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(this) as T
        }
}
