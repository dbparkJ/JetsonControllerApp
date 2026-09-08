package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.google.gson.Gson
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsServer
import java.io.Closeable
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/** Runs the public client through Retrofit, OkHttp, TLS pinning and response HMAC. */
class LocalApiClientReplayTest {
    @Test
    fun `pipeline restart with damaged response is never executed twice`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.SIGNATURE
            val client = backend.connectedClient()

            val result = client.controlPipeline("test-pipeline", "restart")

            assertEquals("The server already applied the first restart", 1, backend.mutations.get())
            assertTrue("The original command result remains unknown", result.isFailure)
            assertEquals("Query current pipeline state instead of replaying restart", 1, backend.queries.get())
            val unknown = result.exceptionOrNull() as JetsonCommandResultUnknownException
            assertEquals("RESULT_UNKNOWN", unknown.resultCode)
            assertEquals(PipelineState.RUNNING, (unknown.stateQueryResult.getOrThrow() as ManagedPipeline).state)
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `lost upload response queries jobs without replaying upload`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.DROP_RESPONSE
            val client = backend.connectedClient()

            val result = client.startUpload("test-root", "test-folder", "test-target")

            assertEquals("The upload was accepted before its response was lost", 1, backend.mutations.get())
            assertTrue(result.isFailure)
            assertEquals("A missing job id requires a safe job-list query", 1, backend.queries.get())
            assertTrue(result.exceptionOrNull() is JetsonCommandResultUnknownException)
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `authenticated 503 retry-after zero does not replay POST or bodyless DELETE`() = runBlocking {
        for (delete in listOf(false, true)) {
            TestBackend().use { backend ->
                backend.damage = Damage.SIGNED_503
                val client = backend.connectedClient()
                val result = if (delete) client.removePipeline("test-pipeline")
                    else client.controlPipeline("test-pipeline", "restart")

                assertTrue(result.isFailure)
                assertEquals(1, backend.mutations.get())
                assertEquals("An authenticated error does not invoke result-unknown reconciliation", 0, backend.queries.get())
                backend.assertAuthenticatedRequests()
            }
        }
    }

    @Test
    fun `redirect never resends a mutation`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.SIGNED_307
            val result = backend.connectedClient().controlPipeline("test-pipeline", "restart")
            assertTrue(result.isFailure)
            assertEquals(1, backend.mutations.get())
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `unsigned mutation 401 is uncertain and only the state query may reauthenticate`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.UNSIGNED_401
            backend.readDamage = Damage.UNSIGNED_401
            val result = backend.connectedClient().controlPipeline("test-pipeline", "restart")

            assertTrue(result.exceptionOrNull() is JetsonCommandResultUnknownException)
            assertEquals(1, backend.mutations.get())
            assertEquals(2, backend.queries.get())
            assertEquals(2, backend.hellos.get())
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `authenticated 401 does not become an uncertain result or retry`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.SIGNED_401
            val result = backend.connectedClient().controlPipeline("test-pipeline", "restart")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() !is JetsonCommandResultUnknownException)
            assertEquals(1, backend.mutations.get())
            assertEquals(0, backend.queries.get())
            assertEquals(1, backend.hellos.get())
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `read signature error and unsigned 401 retain one authenticated recovery attempt`() = runBlocking {
        for (damage in listOf(Damage.SIGNATURE, Damage.UNSIGNED_401)) {
            TestBackend().use { backend ->
                backend.readDamage = damage
                val result = backend.connectedClient().getStatus()
                assertTrue(result.isSuccess)
                assertEquals(2, backend.queries.get())
                assertEquals(2, backend.hellos.get())
                assertEquals(0, backend.mutations.get())
                backend.assertAuthenticatedRequests()
            }
        }
    }

    @Test
    fun `persistent read integrity failure is bounded to two reads and one refresh`() = runBlocking {
        TestBackend().use { backend ->
            backend.readDamage = Damage.SIGNATURE
            backend.alwaysDamageReads = true
            val result = backend.connectedClient().getStatus()
            assertTrue(result.exceptionOrNull() is JetsonAuthenticationRecoveryException)
            assertEquals(2, backend.queries.get())
            assertEquals(2, backend.hellos.get())
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `failed reconciliation preserves unknown result and never replays mutation`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.DROP_RESPONSE
            backend.readDamage = Damage.SIGNATURE
            backend.alwaysDamageReads = true
            val result = backend.connectedClient().controlPipeline("test-pipeline", "restart")
            val unknown = result.exceptionOrNull() as JetsonCommandResultUnknownException
            assertTrue(unknown.stateQueryResult.isFailure)
            assertEquals(1, backend.mutations.get())
            assertEquals(2, backend.queries.get())
            assertEquals(2, backend.hellos.get())
        }
    }

    @Test
    fun `cancellation after command dispatch propagates without retry or state query`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.HOLD_RESPONSE
            val client = backend.connectedClient()
            val command = launch { client.controlPipeline("test-pipeline", "restart") }
            withTimeout(5_000) { backend.mutationReceived.await() }
            command.cancelAndJoin()

            assertTrue(command.isCancelled)
            assertEquals(1, backend.mutations.get())
            assertEquals(0, backend.queries.get())
            backend.releaseResponse.countDown()
            backend.assertCallsReleased(client)
        }
    }

    @Test
    fun `state reconciliation expires within its fixed eight second budget`() = runBlocking {
        TestBackend().use { backend ->
            backend.readDamage = Damage.HOLD_RESPONSE
            val result = withTimeout(12_000) {
                backend.connectedClient().controlPipeline("test-pipeline", "restart")
            }
            val unknown = result.exceptionOrNull() as JetsonCommandResultUnknownException
            assertTrue(unknown.stateQueryResult.isFailure)
            assertTrue(unknown.stateQueryResult.exceptionOrNull()?.message.orEmpty().contains("시간이 초과"))
            assertEquals(1, backend.mutations.get())
            assertEquals(1, backend.queries.get())
            backend.releaseResponse.countDown()
        }
    }

    @Test
    fun `hello certificate claim and proof mismatches prevent authenticated commands`() = runBlocking {
        for (certificateMismatch in listOf(false, true)) {
            TestBackend().use { backend ->
                backend.damageHelloProof = !certificateMismatch
                backend.damageHelloCertificate = certificateMismatch
                val failure = runCatching { backend.connectedClient() }.exceptionOrNull()
                assertTrue(failure is IllegalArgumentException)
                assertEquals(0, backend.mutations.get())
                assertEquals(0, backend.queries.get())
            }
        }
    }

    @Test
    fun `same endpoint reset blocks the old command reconciliation`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.HOLD_RESPONSE
            val client = backend.connectedClient()
            val command = async { client.controlPipeline("test-pipeline", "restart") }
            withTimeout(5_000) { backend.mutationReceived.await() }
            backend.resetEndpoint(client)
            backend.releaseResponse.countDown()

            val error = runCatching { command.await() }.exceptionOrNull()
            assertTrue("A same-URL reset still starts a new endpoint generation", error is CancellationException)
            assertEquals(0, backend.queries.get())
            backend.assertCallsReleased(client)
        }
    }

    @Test
    fun `endpoint reset during reconciliation discards the old observation`() = runBlocking {
        TestBackend().use { backend ->
            backend.readDamage = Damage.HOLD_RESPONSE
            val client = backend.connectedClient()
            val command = async { client.controlPipeline("test-pipeline", "restart") }
            withTimeout(5_000) { backend.queryReceived.await() }
            backend.resetEndpoint(client)
            backend.releaseResponse.countDown()

            val error = runCatching { command.await() }.exceptionOrNull()
            assertTrue("An old query cannot publish a result into a new endpoint generation", error is CancellationException)
            assertEquals(1, backend.mutations.get())
            assertEquals(1, backend.queries.get())
            backend.assertCallsReleased(client)
        }
    }

    @Test
    fun `authenticated null command body is unknown and requires state lookup`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.NULL_BODY
            val result = backend.connectedClient().controlPipeline("test-pipeline", "restart")
            assertTrue(result.exceptionOrNull() is JetsonCommandResultUnknownException)
            assertEquals(1, backend.mutations.get())
            assertEquals(1, backend.queries.get())
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `authenticated 204 remains a valid result for commands without a response body`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.SIGNED_204
            val client = backend.connectedClient()
            assertTrue(client.removePipeline("test-pipeline").isSuccess)
            assertEquals(1, backend.mutations.get())
            assertEquals(0, backend.queries.get())
            backend.assertAuthenticatedRequests()
            backend.assertCallsReleased(client)
        }
    }

    private enum class Damage {
        NONE, SIGNATURE, DROP_RESPONSE, SIGNED_503, SIGNED_307, SIGNED_401, UNSIGNED_401,
        HOLD_RESPONSE, NULL_BODY, SIGNED_204
    }

    private class TestBackend : Closeable {
        private val gson = Gson()
        private val firstDeviceId = "00000000-0000-0000-0000-000000000001"
        private val secondDeviceId = "00000000-0000-0000-0000-000000000002"
        private val firstSecret = ByteArray(32) { it.toByte() }
        private val secondSecret = ByteArray(32) { (it + 32).toByte() }
        @Volatile private var deviceId = firstDeviceId
        private val secret get() = if (deviceId == firstDeviceId) firstSecret else secondSecret
        private val bootNonce = "test-boot"
        private val executor = Executors.newCachedThreadPool()
        private val requestErrors = CopyOnWriteArrayList<String>()
        private val store = KeyStore.getInstance("PKCS12").apply {
            // Deliberately public, test-only key. No production credentials are used.
            requireNotNull(LocalApiClientReplayTest::class.java.getResourceAsStream("/local-api-test.p12")).use {
                load(it, "test-only-password".toCharArray())
            }
        }
        private val certificateHash = certificateSha256(store.getCertificate("localhost-test"))
        private val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(store, "test-only-password".toCharArray())
            }
            httpsConfigurator = HttpsConfigurator(SSLContext.getInstance("TLS").apply {
                init(keys.keyManagers, null, null)
            })
            executor = this@TestBackend.executor
            createContext("/") { raw -> handle(raw as HttpsExchange) }
            start()
        }
        var damage = Damage.SIGNATURE
        var readDamage = Damage.NONE
        var alwaysDamageReads = false
        var damageHelloProof = false
        var damageHelloCertificate = false
        val mutations = AtomicInteger()
        val queries = AtomicInteger()
        val hellos = AtomicInteger()
        val mutationReceived = CompletableDeferred<Unit>()
        val queryReceived = CompletableDeferred<Unit>()
        val releaseResponse = CountDownLatch(1)

        suspend fun connectedClient(): LocalApiClient {
            val credentials = Mockito.mock(DeviceCredentialStore::class.java)
            Mockito.`when`(credentials.getSecret(firstDeviceId)).thenReturn(hex(firstSecret))
            Mockito.`when`(credentials.getSecret(secondDeviceId)).thenReturn(hex(secondSecret))
            return LocalApiClient(credentials).also { client ->
                client.updateEndpoint("127.0.0.1", server.address.port)
                client.hello().getOrThrow()
            }
        }

        fun resetEndpoint(client: LocalApiClient) {
            client.updateEndpoint("127.0.0.1", server.address.port)
        }

        fun switchRegisteredDevice() {
            deviceId = secondDeviceId
        }

        fun assertAuthenticatedRequests() {
            assertEquals("Every mutation and query must pass request HMAC", emptyList<String>(), requestErrors.toList())
        }

        suspend fun assertCallsReleased(client: LocalApiClient) {
            val calls = LocalApiClient::class.java.getDeclaredField("activeCalls").apply { isAccessible = true }
            val lock = requireNotNull(
                LocalApiClient::class.java.getDeclaredField("endpointLock").apply { isAccessible = true }.get(client)
            )
            withTimeout(1_000) {
                while (synchronized(lock) { (calls.get(client) as Set<*>).isNotEmpty() }) delay(10)
            }
        }

        private fun handle(exchange: HttpsExchange) {
            exchange.use {
                val path = exchange.requestURI.rawPath
                if (path == "/v1/hello") {
                    hellos.incrementAndGet()
                    val now = System.currentTimeMillis() / 1000
                    val proof = hmac(listOf("JETSONHELLO1", "1", deviceId, "MMS-TEST", bootNonce,
                        now.toString(), "JETSONHTTP2", certificateHash).joinToString("\n"))
                    val body = LocalControlApi.HelloResponse(1, deviceId, "MMS-TEST", bootNonce,
                        now, "JETSONHTTP2", if (damageHelloCertificate) "0".repeat(64) else certificateHash,
                        if (damageHelloProof) "0".repeat(64) else proof)
                    respond(exchange, gson.toJson(body), signed = false)
                    return
                }
                val bytes = exchange.requestBody.readBytes()
                val headers = exchange.requestHeaders
                if (headers.getFirst("X-Device-Id") != deviceId) {
                    if (exchange.requestMethod == "GET") queries.incrementAndGet()
                    else mutations.incrementAndGet()
                    respond(exchange, "{}", signed = false, code = 401)
                    return
                }
                val signed = HttpAuthSigner.sign(secret, deviceId, bootNonce, exchange.requestMethod,
                    exchange.requestURI.toASCIIString(), bytes,
                    headers.getFirst("X-Request-Nonce"), headers.getFirst("X-Request-Timestamp").toLong())
                if (signed.signature != headers.getFirst("X-Signature")) {
                    requestErrors += "request HMAC mismatch"
                }
                if (exchange.requestMethod != "GET") {
                    val count = mutations.incrementAndGet()
                    mutationReceived.complete(Unit)
                    respondDamaged(exchange, gson.toJson(pipeline()), if (count == 1) damage else Damage.NONE)
                } else {
                    val count = queries.incrementAndGet()
                    queryReceived.complete(Unit)
                    respondDamaged(exchange, when (path) {
                        "/v1/pipelines" -> gson.toJson(listOf(pipeline()))
                        "/v1/uploads" -> "[]"
                        else -> gson.toJson(JetsonStatus())
                    }, if (count == 1 || alwaysDamageReads) readDamage else Damage.NONE)
                }
            }
        }

        private fun pipeline() = ManagedPipeline(
            id = "test-pipeline", label = "Test pipeline", state = PipelineState.RUNNING,
            entrypoint = "test.py", config = "test.yaml", virtualenv = "test-venv"
        )

        private fun respondDamaged(exchange: HttpsExchange, json: String, damage: Damage) {
            when (damage) {
                Damage.DROP_RESPONSE -> return
                Damage.HOLD_RESPONSE -> {
                    releaseResponse.await(15, TimeUnit.SECONDS)
                    return
                }
                Damage.SIGNED_503 -> {
                    exchange.responseHeaders.set("Retry-After", "0")
                    respond(exchange, "{\"detail\":\"test service unavailable\"}", code = 503)
                }
                Damage.SIGNED_307 -> {
                    exchange.responseHeaders.set("Location", exchange.requestURI.toString())
                    respond(exchange, "{\"detail\":\"test redirect\"}", code = 307)
                }
                Damage.UNSIGNED_401 -> respond(exchange, "{}", signed = false, code = 401)
                Damage.SIGNED_401 -> respond(exchange, "{}", code = 401)
                Damage.NULL_BODY -> respond(exchange, "null")
                Damage.SIGNED_204 -> respond(exchange, "", code = 204)
                else -> respond(exchange, json, damageSignature = damage == Damage.SIGNATURE)
            }
        }

        private fun respond(exchange: HttpsExchange, json: String, signed: Boolean = true,
            damageSignature: Boolean = false, code: Int = 200) {
            val bytes = json.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            if (signed) {
                val headers = exchange.requestHeaders
                val signature = hmac(listOf("JETSONHTTPRESP1", deviceId, bootNonce,
                    headers.getFirst("X-Request-Nonce"), headers.getFirst("X-Request-Timestamp"),
                    code.toString(), hex(MessageDigest.getInstance("SHA-256").digest(bytes))).joinToString("\n"))
                exchange.responseHeaders.set("X-Response-Signature", if (damageSignature) "0".repeat(64) else signature)
            }
            exchange.sendResponseHeaders(code, if (code == 204) -1 else bytes.size.toLong())
            if (code != 204) exchange.responseBody.write(bytes)
        }

        private fun hmac(text: String): String = hex(Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret, "HmacSHA256"))
            doFinal(text.toByteArray())
        })

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

        override fun close() {
            releaseResponse.countDown()
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
