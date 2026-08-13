package com.example.jetsoncontroller.data.network

import android.annotation.SuppressLint
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.RegisterPipelineRequest
import com.example.jetsoncontroller.model.PipelineConfigDocument
import com.example.jetsoncontroller.model.PipelineLog
import com.example.jetsoncontroller.model.RemoteFileContent
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.model.UpdatePipelineConfigRequest
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.model.WifiProvisionRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class LocalApiClient(
    private val credentialStore: DeviceCredentialStore
) {
    private val gson = Gson()
    private val authInterceptor = HttpAuthInterceptor()
    private var currentBaseUrl: String? = null
    private var api: LocalControlApi? = null
    private var bootstrapTrustManager: HelloBootstrapTrustManager? = null
    private val sessionRefreshMutex = Mutex()

    @Volatile
    private var sessionRevision = 0L

    fun updateEndpoint(host: String, port: Int) {
        val normalizedHost = host.removePrefix("[").removeSuffix("]")
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(normalizedHost)
            .port(port)
            .addPathSegment("")
            .build()
            .toString()

        currentBaseUrl = url
        authInterceptor.clearSession()
        sessionRevision += 1
        bootstrapTrustManager = HelloBootstrapTrustManager()
        api = buildApi(bootstrapTrustManager ?: HelloBootstrapTrustManager())
    }

    @SuppressLint("BadHostnameVerifier")
    private fun buildApi(trustManager: X509TrustManager): LocalControlApi {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            // Device identity is the exact certificate pin, not a changing LAN IP.
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(authInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(currentBaseUrl ?: error("Jetson API 주소가 설정되지 않았습니다."))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(LocalControlApi::class.java)
    }

    suspend fun hello(): Result<LocalControlApi.HelloResponse> = suspendResult {
        val response = requireApi().hello()
        val body = requireBody(response, "장비 확인")
        require(body.authScheme == "JETSONHTTP2") {
            "지원하지 않는 Jetson API 인증 방식입니다."
        }
        val peerCertificate = response.raw().handshake
            ?.peerCertificates
            ?.firstOrNull()
        val peerFingerprint = if (peerCertificate != null) {
            certificateSha256(peerCertificate)
        } else {
            bootstrapTrustManager?.lastServerCertificateSha256
                ?: error("Jetson TLS 인증서를 확인할 수 없습니다.")
        }
        require(
            peerFingerprint.equals(body.tlsCertificateSha256, ignoreCase = true)
        ) {
            "Jetson TLS 인증서 정보가 일치하지 않습니다."
        }
        val secretHex = credentialStore.getSecret(body.deviceId)
        if (secretHex == null) {
            authInterceptor.clearSession()
        } else {
            val secret = hexToBytes(secretHex)
            require(
                HttpAuthSigner.verifyHello(
                    secret = secret,
                    apiVersion = body.apiVersion,
                    deviceId = body.deviceId,
                    deviceName = body.deviceName,
                    bootNonce = body.bootNonce,
                    serverTimeEpochSeconds = body.serverTimeEpochSeconds,
                    authScheme = body.authScheme,
                    tlsCertificateSha256 = body.tlsCertificateSha256,
                    receivedProof = body.helloProof
                )
            ) {
                "Jetson TLS 인증서 증명에 실패했습니다."
            }
            api = buildApi(PinnedCertificateTrustManager(peerFingerprint))
            authInterceptor.updateSession(
                deviceId = body.deviceId,
                bootNonce = body.bootNonce,
                secret = secret,
                serverTimeEpochSeconds = body.serverTimeEpochSeconds
            )
            sessionRevision += 1
        }
        body
    }

    suspend fun getStatus(): Result<JetsonStatus> =
        request("상태 조회") { requireApi().getStatus() }

    suspend fun getCapabilities(): Result<LocalControlApi.CapabilitiesResponse> =
        request("기능 조회") { requireApi().getCapabilities() }

    suspend fun sendCommand(
        command: String,
        body: Map<String, Any> = emptyMap()
    ): Result<Unit> = suspendResult {
        requireSuccess(
            withSessionRetry { requireApi().sendCommand(command, body) },
            "명령 전송"
        )
    }

    suspend fun getRoots(): Result<List<RemoteRoot>> =
        request("저장소 조회") { requireApi().getRoots() }

    suspend fun listFiles(
        rootId: String,
        path: String
    ): Result<LocalControlApi.ListFilesResponse> =
        request("파일 목록 조회") { requireApi().listFiles(rootId, path) }

    suspend fun getFile(rootId: String, path: String): Result<RemoteFileContent> =
        suspendResult {
            val response = withSessionRetry { requireApi().getFile(rootId, path) }
            val body = requireBody(response, "파일 열기")
            RemoteFileContent(
                name = path.substringAfterLast('/'),
                mimeType = body.contentType()?.toString() ?: "application/octet-stream",
                bytes = body.bytes()
            )
        }

    suspend fun getWorkspaceRoots(): Result<List<RemoteRoot>> =
        requestWithLegacyFallback(
            operation = "작업공간 조회",
            call = { requireApi().getWorkspaceRoots() },
            fallback = { requireApi().getRoots() }
        )

    suspend fun listWorkspaceFiles(
        rootId: String,
        path: String
    ): Result<LocalControlApi.ListFilesResponse> =
        requestWithLegacyFallback(
            operation = "작업공간 파일 목록 조회",
            call = { requireApi().listWorkspaceFiles(rootId, path) },
            fallback = { requireApi().listFiles(rootId, path) }
        )

    suspend fun getUploadTargets(): Result<List<UploadTarget>> =
        request("업로드 대상 조회") { requireApi().getUploadTargets() }

    suspend fun saveUploadTarget(
        targetId: String,
        label: String,
        baseUrl: String,
        token: String?
    ): Result<UploadTarget> = request("업로드 서버 저장") {
        requireApi().saveUploadTarget(
            targetId,
            LocalControlApi.SaveUploadTargetRequest(label, baseUrl, token)
        )
    }

    suspend fun deleteUploadTarget(targetId: String): Result<Unit> = suspendResult {
        requireSuccess(
            withSessionRetry { requireApi().deleteUploadTarget(targetId) },
            "업로드 서버 삭제"
        )
    }

    suspend fun startUpload(
        rootId: String,
        relativePath: String,
        targetId: String
    ): Result<UploadJob> = request("업로드 시작") {
        requireApi().startUpload(
            LocalControlApi.StartUploadRequest(rootId, relativePath, targetId)
        )
    }

    suspend fun getUploadJobs(activeOnly: Boolean = false): Result<List<UploadJob>> =
        request(if (activeOnly) "전송 큐 조회" else "업로드 작업 조회") {
            requireApi().getUploadJobs(activeOnly)
        }

    suspend fun getUploadJob(jobId: String): Result<UploadJob> =
        request("업로드 상태 조회") { requireApi().getUploadJob(jobId) }

    suspend fun cancelUpload(jobId: String): Result<UploadJob> =
        request("업로드 취소") { requireApi().cancelUpload(jobId) }

    suspend fun retryUpload(jobId: String): Result<UploadJob> =
        request("업로드 재시도") { requireApi().retryUpload(jobId) }

    suspend fun getPipelines(): Result<List<ManagedPipeline>> =
        request("자동 실행 작업 조회") { requireApi().getPipelines() }

    suspend fun registerPipeline(
        request: RegisterPipelineRequest
    ): Result<ManagedPipeline> =
        request("자동 실행 작업 등록") { requireApi().registerPipeline(request) }

    suspend fun controlPipeline(
        pipelineId: String,
        action: String
    ): Result<ManagedPipeline> =
        request("자동 실행 작업 제어") {
            requireApi().controlPipeline(pipelineId, action)
        }

    suspend fun removePipeline(pipelineId: String): Result<Unit> = suspendResult {
        requireSuccess(
            withSessionRetry { requireApi().removePipeline(pipelineId) },
            "자동 실행 작업 등록 해제"
        )
    }

    suspend fun getPipelineLogs(pipelineId: String): Result<PipelineLog> =
        request("실행 로그 조회") { requireApi().getPipelineLogs(pipelineId) }

    suspend fun getPipelineConfig(pipelineId: String): Result<PipelineConfigDocument> =
        request("YAML 설정 조회") { requireApi().getPipelineConfig(pipelineId) }

    suspend fun updatePipelineConfig(
        pipelineId: String,
        content: String
    ): Result<PipelineConfigDocument> =
        request("YAML 설정 저장") {
            requireApi().updatePipelineConfig(
                pipelineId,
                UpdatePipelineConfigRequest(content)
            )
        }

    suspend fun configureWifi(
        request: WifiProvisionRequest
    ): Result<LocalControlApi.WifiProvisionResponse> =
        request("Wi-Fi 설정") { requireApi().configureWifi(request) }

    private fun requireApi(): LocalControlApi =
        api ?: error("Jetson API 주소가 설정되지 않았습니다.")

    private suspend fun <T> request(
        operation: String,
        call: suspend () -> Response<T>
    ): Result<T> = suspendResult {
        requireBody(withSessionRetry(call), operation)
    }

    private suspend fun <T> requestWithLegacyFallback(
        operation: String,
        call: suspend () -> Response<T>,
        fallback: suspend () -> Response<T>
    ): Result<T> = suspendResult {
        val response = withLegacyEndpointFallback(
            call = { withSessionRetry(call) },
            fallback = { withSessionRetry(fallback) }
        )
        requireBody(response, operation)
    }

    private suspend fun <T> withSessionRetry(call: suspend () -> T): T {
        val attemptedRevision = sessionRevision
        return try {
            call()
        } catch (error: Exception) {
            if (
                error !is JetsonSessionExpiredException &&
                error !is JetsonResponseSignatureException
            ) {
                throw error
            }
            sessionRefreshMutex.withLock {
                if (sessionRevision == attemptedRevision) {
                    hello().getOrThrow()
                }
            }
            call()
        }
    }

    private suspend fun <T> suspendResult(
        block: suspend () -> T
    ): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun <T> requireBody(response: Response<T>, operation: String): T {
        requireSuccess(response, operation)
        return response.body() ?: error("$operation 응답이 비어 있습니다.")
    }

    private fun requireSuccess(response: Response<*>, operation: String) {
        if (response.isSuccessful) return
        val detail = response.errorBody()?.string()?.let { raw ->
            runCatching {
                gson.fromJson(raw, JsonObject::class.java)
                    ?.get("detail")
                    ?.asString
            }.getOrNull()
        }
        error(detail ?: "$operation 실패 (HTTP ${response.code()})")
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length == 64 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "저장된 장비 인증키가 올바르지 않습니다."
        }
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

internal suspend fun <T> withLegacyEndpointFallback(
    call: suspend () -> T,
    fallback: suspend () -> T
): T = try {
    call()
} catch (_: JetsonEndpointUnavailableException) {
    fallback()
}
