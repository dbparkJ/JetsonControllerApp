package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineControl
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
import kotlinx.coroutines.Dispatchers
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
    @Test fun `capture and terminal never replay after an unverifiable response`() = runBlocking {
        for (capture in listOf(true, false)) {
            TestBackend().use { backend ->
                backend.damage = Damage.SIGNATURE
                val client = backend.connectedClient()
                val result = if (capture) client.captureFrame() else client.terminal("pwd")
                assertTrue(result.isFailure)
                assertEquals(1, backend.mutations.get())
                backend.assertAuthenticatedRequests()
            }
        }
    }

    @Test
    fun `diagnostics correlate real signed requests and never promote rejected responses`() = runBlocking {
        val events = CopyOnWriteArrayList<Pair<String, Map<String, Any?>>>()
        ConnectionDiagnostics.setSinkForTests { event, fields, _ -> events += event to fields }
        try {
            TestBackend().use { backend ->
                val client = backend.connectedClient()
                listOf(37L to 91L, 38L to 92L).map { (session, sequence) ->
                    async(ApiDiagnosticContext(sessionId = session, requestSequence = sequence) + Dispatchers.Default) {
                        client.getStatus().getOrThrow()
                    }
                }.forEach { it.await() }
                val verifiedRows = events.filter { it.first == "api_authenticated" && it.second["route"] == "STATUS" }
                assertEquals(setOf(37L to 91L, 38L to 92L), verifiedRows.map { it.second["sessionId"] to it.second["requestSequence"] }.toSet())
                val verified = verifiedRows.single { it.second["sessionId"] == 37L }.second
                assertEquals(37L, verified["sessionId"])
                assertEquals(91L, verified["requestSequence"])
                assertTrue(backend.requestRefs.contains(verified["requestRef"]))
                assertTrue(events.any { it.first == "api_connection" && it.second["requestId"] == verified["requestId"] })
                val ended = events.single { it.first == "api_call_ended" && it.second["requestId"] == verified["requestId"] }.second
                // OkHttp may end the Call after the application interceptor returns.
                // Its callback order is not an authentication contract.
                assertTrue("Call end must not assert HMAC authentication", !ended.containsKey("authenticated"))
                assertEquals(true, verified["authenticated"])
                println("LAB diagnostic event order=" + events.filter {
                    it.second["requestId"] == verified["requestId"]
                }.map { it.first }.joinToString(","))

                events.clear()
                backend.readDamage = Damage.SIGNATURE
                backend.alwaysDamageReads = true
                assertTrue(client.getStatus().isFailure)
                assertTrue("HTTP 200 alone cannot promote a damaged signature", events.any {
                    it.first == "api_response" && it.second["httpStatus"] == 200
                })
                assertTrue(events.any { it.first == "api_auth" && it.second["authenticated"] == false })
                assertTrue(events.none { it.first == "api_authenticated" })
                assertTrue(events.none { it.second.values.any { value -> value == "127.0.0.1" || value == "test-boot" } })
                backend.assertAuthenticatedRequests()
            }
        } finally { ConnectionDiagnostics.setSinkForTests(null) }
    }

    @Test
    fun `diagnostic sink failure cannot break authenticated request or call release`() = runBlocking {
        ConnectionDiagnostics.setSinkForTests { _, _, _ -> throw IllegalStateException("storage unavailable") }
        try {
            TestBackend().use { backend ->
                val client = backend.connectedClient()
                client.getStatus().getOrThrow()
                backend.assertCallsReleased(client)
                backend.assertAuthenticatedRequests()
            }
        } finally { ConnectionDiagnostics.setSinkForTests(null) }
    }

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
    fun `exact stop fails closed when old backend lacks contextual endpoint`() = runBlocking {
        TestBackend().use { backend ->
            backend.damage = Damage.NONE
            backend.supportsContextualStop = false
            backend.pipelineState = PipelineState.RUNNING
            val client = backend.connectedClient()

            val result = client.controlPipeline(
                "test-pipeline",
                "stop",
                expectedRunId = "capture/run-0001"
            )

            val error = result.exceptionOrNull() as JetsonApiException
            assertEquals(404, error.statusCode)
            assertEquals(
                listOf("/v1/pipelines/test-pipeline/contextual-stop"),
                backend.mutationPaths.toList()
            )
            assertTrue(backend.mutationBodies.single().contains("\"expectedRunId\":\"capture/run-0001\""))
            assertEquals(PipelineState.RUNNING, backend.pipelineState)
            assertEquals(0, backend.pipelineCommandIssues.get())
            assertEquals(0, backend.queries.get())
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `lost start and stop responses requery actual state without replaying commands`() = runBlocking {
        for ((action, expectedState) in listOf(
            "start" to PipelineState.RUNNING,
            "stop" to PipelineState.STOPPED
        )) {
            TestBackend().use { backend ->
                backend.damage = Damage.DROP_RESPONSE
                val client = backend.connectedClient()

                val result = client.controlPipeline("test-pipeline", action)

                val unknown = result.exceptionOrNull() as JetsonCommandResultUnknownException
                val observed = unknown.stateQueryResult.getOrThrow() as ManagedPipeline
                assertEquals(expectedState, observed.state)
                assertEquals(1, backend.mutations.get())
                assertEquals(1, backend.queries.get())
                backend.assertAuthenticatedRequests()
            }
        }
    }

    @Test
    fun `duplicate start and stop responses expose one backend mutation`() = runBlocking {
        for ((action, initialState) in listOf(
            "start" to PipelineState.STOPPED,
            "stop" to PipelineState.RUNNING
        )) {
            TestBackend().use { backend ->
                backend.damage = Damage.NONE
                backend.pipelineState = initialState
                val client = backend.connectedClient()

                val first = client.controlPipeline("test-pipeline", action).getOrThrow()
                val duplicate = client.controlPipeline("test-pipeline", action).getOrThrow()

                assertEquals("COMMAND_COMPLETED", first.control!!.outcome)
                assertTrue(first.control!!.commandIssued)
                assertEquals("ALREADY_SATISFIED", duplicate.control!!.outcome)
                assertTrue(!duplicate.control!!.commandIssued)
                assertEquals(2, backend.mutations.get())
                assertEquals(1, backend.pipelineCommandIssues.get())
                backend.assertAuthenticatedRequests()
            }
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
    fun `late start and stop responses cannot cross an endpoint generation`() = runBlocking {
        for (action in listOf("start", "stop")) {
            TestBackend().use { backend ->
                backend.damage = Damage.HOLD_RESPONSE
                val client = backend.connectedClient()
                val command = async { client.controlPipeline("test-pipeline", action) }
                withTimeout(5_000) { backend.mutationReceived.await() }
                backend.resetEndpoint(client)
                backend.releaseResponse.countDown()

                val error = runCatching { command.await() }.exceptionOrNull()
                assertTrue("A same-URL reset still starts a new endpoint generation", error is CancellationException)
                assertEquals(1, backend.mutations.get())
                assertEquals(0, backend.queries.get())
                backend.assertCallsReleased(client)
            }
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

    @Test
    fun `reauthentication cannot silently adopt a different registered device sharing a certificate`() = runBlocking {
        TestBackend().use { backend ->
            val client = backend.connectedClient()
            backend.switchRegisteredDevice()
            val result = client.getStatus()

            assertTrue("An existing session must retain its authenticated device identity", result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("장비"))
            assertEquals("Reject the new hello identity before issuing a new authenticated read", 1, backend.queries.get())
            assertEquals(2, backend.hellos.get())
            assertEquals(0, backend.mutations.get())
            backend.assertAuthenticatedRequests()
        }
    }

    @Test
    fun `explicit endpoint reset may authenticate another registered device`() = runBlocking {
        TestBackend().use { backend ->
            val client = backend.connectedClient()
            backend.switchRegisteredDevice()
            backend.resetEndpoint(client)

            assertTrue(client.hello().isSuccess)
            assertTrue(client.getStatus().isSuccess)
            assertEquals(1, backend.queries.get())
            assertEquals(2, backend.hellos.get())
            backend.assertAuthenticatedRequests()
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
        var supportsContextualStop = true
        val mutations = AtomicInteger()
        val pipelineCommandIssues = AtomicInteger()
        val queries = AtomicInteger()
        val hellos = AtomicInteger()
        val requestRefs = CopyOnWriteArrayList<String>()
        val mutationPaths = CopyOnWriteArrayList<String>()
        val mutationBodies = CopyOnWriteArrayList<String>()
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
                headers.getFirst("X-Request-Nonce")?.let { nonce ->
                    requestRefs += MessageDigest.getInstance("SHA-256").digest(("STAB1:" + nonce).toByteArray())
                        .joinToString("") { "%02x".format(it) }.take(16)
                }
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
                    mutationPaths += path
                    mutationBodies += bytes.toString(Charsets.UTF_8)
                    if (path.endsWith("/contextual-stop") && !supportsContextualStop) {
                        respond(exchange, "{\"detail\":\"Unknown pipeline action\"}", code = 404)
                        return
                    }
                    val rawAction = exchange.requestURI.rawPath.substringAfterLast('/')
                    val action = if (rawAction == "contextual-stop") "stop" else rawAction
                    val satisfied = when (action) {
                        "start" -> pipelineState in setOf(PipelineState.RUNNING, PipelineState.STARTING)
                        "stop" -> pipelineState in setOf(PipelineState.STOPPED, PipelineState.FAILED)
                        else -> false
                    }
                    if (!satisfied && action in setOf("start", "stop", "restart")) {
                        pipelineCommandIssues.incrementAndGet()
                        pipelineState = if (action == "stop") PipelineState.STOPPED else PipelineState.RUNNING
                    }
                    val responsePipeline = pipeline().copy(
                        control = if (action in setOf("start", "stop", "restart")) {
                            PipelineControl(
                                action = action,
                                commandIssued = !satisfied,
                                outcome = if (satisfied) "ALREADY_SATISFIED" else "COMMAND_COMPLETED"
                            )
                        } else null
                    )
                    mutationReceived.complete(Unit)
                    respondDamaged(exchange, gson.toJson(responsePipeline), if (count == 1) damage else Damage.NONE)
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

        @Volatile var pipelineState = PipelineState.RUNNING

        private fun pipeline() = ManagedPipeline(
            id = "test-pipeline", label = "Test pipeline", state = pipelineState,
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
