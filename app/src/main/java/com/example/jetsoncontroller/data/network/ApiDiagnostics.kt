package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import kotlinx.coroutines.ThreadContextElement
import okhttp3.Connection
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.WeakHashMap
import javax.net.ssl.SSLException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Diagnostic-only correlation follows the coroutine into Retrofit's call factory. */
internal class ApiDiagnosticContext(
    val sessionId: Long? = null,
    val requestSequence: Long? = null,
    val attemptId: Long? = null
) : ThreadContextElement<ApiDiagnosticContext?>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ApiDiagnosticContext> {
        private val current = ThreadLocal<ApiDiagnosticContext?>()
        fun snapshot(): ApiDiagnosticContext? = current.get()
    }
    override fun updateThreadContext(context: CoroutineContext): ApiDiagnosticContext? =
        current.get().also { current.set(this) }
    override fun restoreThreadContext(context: CoroutineContext, oldState: ApiDiagnosticContext?) {
        if (oldState == null) current.remove() else current.set(oldState)
    }
}

internal class ApiDiagnosticTrace(request: Request, clientId: String, endpointGeneration: Long) {
    private val startedNanos = System.nanoTime()
    private val context = ApiDiagnosticContext.snapshot()
    private val fields = mapOf(
        "requestId" to ConnectionDiagnostics.newId(), "clientId" to clientId,
        "endpointGeneration" to endpointGeneration, "route" to route(request.url.encodedPath),
        "method" to request.method, "sessionId" to context?.sessionId,
        "requestSequence" to context?.requestSequence, "attemptId" to context?.attemptId
    )
    @Volatile private var requestRef: String? = null

    fun signed(nonce: String) {
        // The existing random nonce is never persisted. Both peers use this public domain separator.
        try {
            requestRef = MessageDigest.getInstance("SHA-256")
                .digest(("STAB1:" + nonce).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(16)
            record("api_request")
        } catch (_: Exception) { /* Diagnostic failure cannot reject a signed request. */ }
    }

    fun record(event: String, extra: Map<String, Any?> = emptyMap(), incident: Boolean = false) {
        try {
            ConnectionDiagnostics.record(event, fields + mapOf(
                "requestRef" to requestRef,
                "durationMs" to (System.nanoTime() - startedNanos) / 1_000_000
            ) + extra, incident)
        } catch (_: Exception) { /* Includes test observers. Never changes request outcome. */ }
    }

    fun acquired(connection: Connection) {
        try {
            val socket = connection.socket()
            val (connectionId, reused) = synchronized(connections) {
                val previous = connections[connection]
                (previous ?: ConnectionDiagnostics.newId().also { connections[connection] = it }) to (previous != null)
            }
            record("api_connection", mapOf(
                "connectionId" to connectionId, "reused" to reused,
                "localAddressRef" to ConnectionDiagnostics.privateRef(socket.localAddress?.hostAddress),
                "remoteAddressRef" to ConnectionDiagnostics.privateRef(socket.inetAddress?.hostAddress),
                "localPort" to socket.localPort, "remotePort" to socket.port
            ))
        } catch (_: Exception) { /* A missing socket observation is UNKNOWN, not link loss. */ }
    }

    companion object {
        private val connections = WeakHashMap<Connection, String>()
        private fun route(path: String): String = when {
            path == "/v1/hello" -> "HELLO"
            path == "/v1/status" -> "STATUS"
            path == "/v1/capabilities" -> "CAPABILITIES"
            path.contains("preview") -> "PREVIEW"
            path.contains("rtk") -> "RTK"
            path.startsWith("/v1/pipeline") -> "PIPELINE"
            path.startsWith("/v1/upload") -> "UPLOAD"
            path.startsWith("/v1/storage") || path.startsWith("/v1/fs/") -> "STORAGE"
            path.startsWith("/v1/network/wifi") -> "WIFI"
            path.startsWith("/v1/system") -> "SYSTEM"
            else -> "OTHER"
        }
    }
}

internal fun diagnosticFailure(error: Throwable?): String = when (error) {
    is kotlinx.coroutines.CancellationException -> "CANCELED"
    is SocketTimeoutException -> "TIMEOUT"
    is SSLException -> "TLS"
    is JetsonSessionExpiredException -> "AUTH_EXPIRED"
    is JetsonResponseSignatureException -> "RESPONSE_SIGNATURE"
    is JetsonUnsignedServerErrorException -> "UNSIGNED_RESPONSE"
    is JetsonAuthenticationRecoveryException -> "AUTHENTICATION"
    is IOException -> "IO"
    else -> "OTHER"
}
