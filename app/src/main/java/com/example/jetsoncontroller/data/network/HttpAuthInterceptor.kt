package com.example.jetsoncontroller.data.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

class HttpAuthInterceptor : Interceptor {

    private data class Session(
        val deviceId: String,
        val bootNonce: String,
        val secret: ByteArray,
        val serverClockOffsetSeconds: Long
    )

    @Volatile
    private var session: Session? = null

    fun updateSession(
        deviceId: String,
        bootNonce: String,
        secret: ByteArray,
        serverTimeEpochSeconds: Long
    ) {
        session = Session(
            deviceId = deviceId.lowercase(),
            bootNonce = bootNonce,
            secret = secret.copyOf(),
            serverClockOffsetSeconds = serverTimeEpochSeconds -
                (System.currentTimeMillis() / 1000)
        )
    }

    fun clearSession() {
        session = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath == "/v1/hello") {
            return chain.proceed(request)
        }

        val currentSession = session
            ?: throw IOException("Jetson API 인증 세션이 준비되지 않았습니다.")
        val canonicalPath = buildString {
            append(request.url.encodedPath)
            request.url.encodedQuery?.let { query ->
                append('?')
                append(query)
            }
        }
        val body = Buffer().use { buffer ->
            request.body?.writeTo(buffer)
            buffer.readByteArray()
        }
        val headers = HttpAuthSigner.sign(
            secret = currentSession.secret,
            deviceId = currentSession.deviceId,
            bootNonce = currentSession.bootNonce,
            method = request.method,
            canonicalPath = canonicalPath,
            body = body,
            requestTimestampSeconds = System.currentTimeMillis() / 1000 +
                currentSession.serverClockOffsetSeconds
        )

        val signedRequest = request.newBuilder()
            .header("X-Device-Id", headers.deviceId)
            .header("X-Request-Nonce", headers.requestNonce)
            .header("X-Request-Timestamp", headers.requestTimestamp)
            .header("X-Signature", headers.signature)
            .build()
        val response = chain.proceed(signedRequest)
        val responseBody = response.body
        val contentType = responseBody?.contentType()
        val responseBytes = responseBody?.bytes() ?: byteArrayOf()
        val responseSignature = response.header("X-Response-Signature")

        if (responseSignature == null || !HttpAuthSigner.verifyResponse(
                secret = currentSession.secret,
                deviceId = headers.deviceId,
                bootNonce = currentSession.bootNonce,
                requestNonce = headers.requestNonce,
                requestTimestamp = headers.requestTimestamp,
                statusCode = response.code,
                body = responseBytes,
                receivedSignature = responseSignature
            )
        ) {
            response.close()
            throw IOException("Jetson 응답 인증에 실패했습니다.")
        }

        return response.newBuilder()
            .body(responseBody?.let { responseBytes.toResponseBody(contentType) })
            .build()
    }
}
