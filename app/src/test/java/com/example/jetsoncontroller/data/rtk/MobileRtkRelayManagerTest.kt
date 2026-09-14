package com.example.jetsoncontroller.data.rtk

import android.content.Context
import android.net.ConnectivityManager
import com.example.jetsoncontroller.data.network.LocalApiClient
import com.example.jetsoncontroller.data.network.WifiDirectManager
import com.example.jetsoncontroller.model.MobileRtkRelayState
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class MobileRtkRelayManagerTest {
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
            manager.field<MutableStateFlow<MobileRtkRelayState>>("_state")
        )
    }

    private data class ManagerFixture(
        val manager: MobileRtkRelayManager,
        val scope: CoroutineScope,
        val state: MutableStateFlow<MobileRtkRelayState>
    )

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
