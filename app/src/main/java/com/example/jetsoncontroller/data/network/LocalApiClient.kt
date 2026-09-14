package com.example.jetsoncontroller.data.network

import android.annotation.SuppressLint
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.CameraPreviewFrame
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.DiscoverPipelineFolderRequest
import com.example.jetsoncontroller.model.FanStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.MobileRtkRelayConfig
import com.example.jetsoncontroller.model.MobileRtkRelayRegistration
import com.example.jetsoncontroller.model.PipelineFolderDiscovery
import com.example.jetsoncontroller.model.RegisterPipelineRequest
import com.example.jetsoncontroller.model.RegisterMobileRtkRelayRequest
import com.example.jetsoncontroller.model.RegisterPipelineFolderRequest
import com.example.jetsoncontroller.model.PipelineConfigDocument
import com.example.jetsoncontroller.model.PipelineConfigFieldsDocument
import com.example.jetsoncontroller.model.PipelineLog
import com.example.jetsoncontroller.model.PipelineLogChunk
import com.example.jetsoncontroller.model.PipelineLogFilesResponse
import com.example.jetsoncontroller.model.RemoteFileContent
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.model.UpdatePipelineConfigRequest
import com.example.jetsoncontroller.model.UpdatePipelineConfigFieldsRequest
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadDeletionResponse
import com.example.jetsoncontroller.model.UploadLibraryFilesResponse
import com.example.jetsoncontroller.model.UploadLibrarySessionsResponse
import com.example.jetsoncontroller.model.UploadSourceSummary
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.model.SystemTimeStatus
import com.example.jetsoncontroller.model.ContextualStartRequest
import com.example.jetsoncontroller.model.PipelinePreflight
import com.example.jetsoncontroller.model.PipelinePreflightRequest
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.PipelineRunPolicy
import com.example.jetsoncontroller.model.SurveyLabelMutationRequest
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveyProjectsResponse
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.model.SurveySectionsResponse
import com.example.jetsoncontroller.model.UpdatePipelineRunPolicyRequest
import com.example.jetsoncontroller.model.TrashEntry
import com.example.jetsoncontroller.model.TrashEntriesResponse
import com.example.jetsoncontroller.model.WifiProvisionRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class LocalApiClient(
    private val credentialStore: DeviceCredentialStore
) {
    private val gson = Gson()
    private val diagnosticClientId = ConnectionDiagnostics.newId()
    private val authInterceptor = HttpAuthInterceptor()
    private var currentBaseUrl: String? = null
    private var api: LocalControlApi? = null
    private var bootstrapTrustManager: HelloBootstrapTrustManager? = null
    private val sessionRefreshMutex = Mutex()
    private val endpointLock = Any()
    private val activeCalls = mutableSetOf<Call>()
    private var authenticatedDeviceId: String? = null

    @Volatile
    private var sessionRevision = 0L

    @Volatile
    private var endpointRevision = 0L

    fun updateEndpoint(host: String, port: Int) {
        val normalizedHost = host.removePrefix("[").removeSuffix("]")
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(normalizedHost)
            .port(port)
            .addPathSegment("")
            .build()
            .toString()

        val staleCalls = synchronized(endpointLock) {
            endpointRevision += 1
            currentBaseUrl = url
            authInterceptor.clearSession()
            authenticatedDeviceId = null
            sessionRevision += 1
            bootstrapTrustManager = HelloBootstrapTrustManager()
            api = buildApi(bootstrapTrustManager ?: HelloBootstrapTrustManager())
            activeCalls.toList()
        }
        staleCalls.forEach { it.cancel() }
        ConnectionDiagnostics.record("api_endpoint", mapOf(
            "clientId" to diagnosticClientId, "endpointGeneration" to endpointRevision,
            "endpointRef" to ConnectionDiagnostics.privateRef(url), "authRevision" to sessionRevision
        ))
    }

    @SuppressLint("BadHostnameVerifier")
    private fun buildApi(trustManager: X509TrustManager): LocalControlApi {
        val apiEndpointRevision = endpointRevision
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            // Device identity is the exact certificate pin, not a changing LAN IP.
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(authInterceptor)
            .eventListener(object : EventListener() {
                override fun callStart(call: Call) {
                    val stale = synchronized(endpointLock) {
                        if (endpointRevision == apiEndpointRevision) {
                            activeCalls.add(call)
                            false
                        } else true
                    }
                    // Covers a call created before reset but enqueued afterwards.
                    if (stale) call.cancel()
                    call.request().tag(ApiDiagnosticTrace::class.java)?.record(
                        "api_call_started", mapOf("stale" to stale)
                    )
                }

                override fun connectionAcquired(call: Call, connection: Connection) {
                    call.request().tag(ApiDiagnosticTrace::class.java)?.acquired(connection)
                }

                override fun requestHeadersEnd(call: Call, request: Request) {
                    // This is wire metadata only. Authentication is recorded after HMAC verification.
                    request.tag(ApiDiagnosticTrace::class.java)?.record("api_request_headers")
                }

                override fun callEnd(call: Call) {
                    synchronized(endpointLock) { activeCalls.remove(call) }
                    call.request().tag(ApiDiagnosticTrace::class.java)?.record("api_call_ended")
                }

                override fun callFailed(call: Call, ioe: IOException) {
                    synchronized(endpointLock) { activeCalls.remove(call) }
                    call.request().tag(ApiDiagnosticTrace::class.java)?.record(
                        "api_failure", mapOf("exceptionClass" to diagnosticFailure(ioe)), incident = true
                    )
                }
            })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

        val statusClient = client.newBuilder()
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        val commandClient = client.newBuilder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build()
        return Retrofit.Builder()
            .baseUrl(currentBaseUrl ?: error("Jetson API 주소가 설정되지 않았습니다."))
            .callFactory { original ->
                val request = original.newBuilder().tag(ApiDiagnosticTrace::class.java,
                    ApiDiagnosticTrace(original, diagnosticClientId, apiEndpointRevision)).build()
                if (request.method !in setOf("GET", "HEAD", "OPTIONS")) {
                    // OkHttp may also follow a 503 Retry-After: 0 even with IO retry
                    // disabled. Mark mutations one-shot, including bodyless DELETEs.
                    val body = request.body ?: byteArrayOf().toRequestBody()
                    val oneShotBody = object : RequestBody() {
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                        override fun isOneShot() = true
                    }
                    commandClient.newCall(request.newBuilder().method(request.method, oneShotBody).build())
                } else if (request.url.encodedPath in setOf("/v1/hello", "/v1/status", "/v1/capabilities")) {
                    statusClient.newCall(request)
                } else {
                    client.newCall(request)
                }
            }
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(LocalControlApi::class.java)
    }

    suspend fun hello(): Result<LocalControlApi.HelloResponse> = suspendResult {
        val helloEndpoint = endpointRevision
        val helloStartedUtcMs = System.currentTimeMillis()
        val helloStartedNanos = System.nanoTime()
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
        synchronized(endpointLock) {
            if (endpointRevision != helloEndpoint) {
                throw CancellationException("장비 주소가 변경되어 인증 결과를 폐기했습니다.")
            }
            val expectedDeviceId = authenticatedDeviceId
            if (expectedDeviceId != null && !expectedDeviceId.equals(body.deviceId, ignoreCase = true)) {
                authInterceptor.clearSession()
                throw IllegalArgumentException("인증된 장비와 응답의 장비 정보가 일치하지 않습니다.")
            }
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
                authenticatedDeviceId = body.deviceId.lowercase()
                sessionRevision += 1
                val elapsedMs = (System.nanoTime() - helloStartedNanos) / 1_000_000
                val helloEndedUtcMs = System.currentTimeMillis()
                val clockStepMs = kotlin.math.abs(helloEndedUtcMs - helloStartedUtcMs - elapsedMs)
                response.raw().request.tag(ApiDiagnosticTrace::class.java)?.record(
                    "api_hello_clock", mapOf(
                        "authenticated" to true, "authRevision" to sessionRevision,
                        "deviceRef" to ConnectionDiagnostics.privateRef(body.deviceId),
                        "offsetEstimateMs" to body.serverTimeEpochSeconds * 1000 - (helloEndedUtcMs - elapsedMs / 2),
                        // Server time has whole-second precision; estimate is not clock synchronization.
                        "uncertaintyMs" to elapsedMs / 2 + 1000 + clockStepMs
                    )
                )
            }
        }
        body
    }

    suspend fun getStatus(): Result<JetsonStatus> =
        request("상태 조회") { requireApi().getStatus() }

    suspend fun getCameraPreviewFrame(
        afterRevision: Long? = null
    ): Result<CameraPreviewFrame> = suspendResult {
        val response = withSessionRetry {
            requireApi().getCameraPreviewFrame(
                afterRevision = afterRevision,
                waitMillis = if (afterRevision == null) 0 else CAMERA_PREVIEW_WAIT_MILLIS
            )
        }
        CameraPreviewFrame(
            bytes = requireBody(response, "카메라 프리뷰").bytes(),
            revision = response.headers()["X-Preview-Revision"]?.toLongOrNull()
        )
    }

    suspend fun getCapabilities(): Result<LocalControlApi.CapabilitiesResponse> =
        request("기능 조회") { requireApi().getCapabilities() }

    suspend fun sendCommand(
        command: String,
        body: Map<String, Any> = emptyMap()
    ): Result<Unit> = commandUnit("명령 전송", query = { getStatus() }) {
        requireApi().sendCommand(command, body)
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

    suspend fun deleteStorageEntry(
        rootId: String,
        path: String
    ): Result<TrashEntry> = command(
        "장치 데이터 휴지통 이동",
        query = { observeTrashThen { listFiles(rootId, path.substringBeforeLast('/', "")) } },
        serverFailureMayBeApplied = true
    ) {
        requireApi().deleteStorageEntry(
            rootId,
            path,
            LocalControlApi.ConfirmDeletionRequest()
        )
    }

    suspend fun getTrash(includeRestored: Boolean = false): Result<TrashEntriesResponse> =
        request("장치 휴지통 조회") { requireApi().getTrash(includeRestored) }

    suspend fun restoreTrash(trashId: String): Result<TrashEntry> = command(
        "장치 휴지통 복원", query = { getTrash(includeRestored = true) }, serverFailureMayBeApplied = true
    ) {
        requireApi().restoreTrash(trashId, LocalControlApi.ConfirmDeletionRequest())
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

    suspend fun getWorkspaceFile(
        rootId: String,
        path: String
    ): Result<RemoteFileContent> = suspendResult {
        val response = withSessionRetry { requireApi().getWorkspaceFile(rootId, path) }
        val body = requireBody(response, "작업공간 파일 열기")
        RemoteFileContent(
            name = path.substringAfterLast('/'),
            mimeType = body.contentType()?.toString() ?: "application/octet-stream",
            bytes = body.bytes()
        )
    }


    suspend fun taskRuns(offset: Int) = request("작업 기록 조회") { requireApi().taskRuns(offset) }
    suspend fun deleteTaskRun(pipelineId: String, logId: String) = command("작업 이력 휴지통 이동", query = {
        observeTrashThen { taskRuns(0) }
    }, serverFailureMayBeApplied = true) {
        requireApi().deleteTaskRun(pipelineId, logId, LocalControlApi.ConfirmDeletionRequest())
    }

    private suspend fun observeTrashThen(next: suspend () -> Result<*>): Result<*> {
        val trash = getTrash()
        return if (trash.isSuccess) next() else Result.failure<Any>(trash.exceptionOrNull()!!)
    }
    suspend fun taskRoute(pipelineId: String, logId: String) = request("작업 경로 조회") { requireApi().taskRoute(pipelineId, logId) }
    suspend fun taskRunLog(pipelineId: String, logId: String) = request("저장 로그 조회") { requireApi().taskRunLog(pipelineId, logId) }
    suspend fun captureFrame() = command("카메라 캡처", query = { getStatus() }) { requireApi().captureFrame() }
    suspend fun terminal(command: String) = command("원격 명령 실행", query = { getStatus() }) { requireApi().terminal(com.example.jetsoncontroller.model.TerminalRequest(command)) }

    suspend fun getUploadTargets(): Result<List<UploadTarget>> =
        request("업로드 대상 조회") { requireApi().getUploadTargets() }

    suspend fun getUploadLibrarySessions(
        targetId: String,
        offset: Int = 0
    ): Result<UploadLibrarySessionsResponse> =
        request("서버 데이터 조회") {
            requireApi().getUploadLibrarySessions(targetId, offset)
        }

    suspend fun getUploadLibraryFiles(
        targetId: String,
        sessionId: String,
        path: String
    ): Result<UploadLibraryFilesResponse> =
        request("서버 파일 목록 조회") {
            requireApi().getUploadLibraryFiles(targetId, sessionId, path)
        }

    suspend fun getUploadLibraryFile(
        targetId: String,
        sessionId: String,
        path: String
    ): Result<RemoteFileContent> = suspendResult {
        val response = withSessionRetry {
            requireApi().getUploadLibraryFile(targetId, sessionId, path)
        }
        val body = requireBody(response, "서버 파일 열기")
        RemoteFileContent(
            name = path.substringAfterLast('/'),
            mimeType = body.contentType()?.toString() ?: "application/octet-stream",
            bytes = body.bytes()
        )
    }

    suspend fun deleteUploadLibrarySession(
        targetId: String,
        sessionId: String
    ): Result<UploadDeletionResponse> = command(
        "서버 데이터 삭제", query = { getUploadLibrarySessions(targetId) }
    ) {
        requireApi().deleteUploadLibrarySession(
            sessionId,
            targetId,
            LocalControlApi.ConfirmDeletionRequest()
        )
    }

    suspend fun getUploadSourceSummary(
        rootId: String,
        relativePath: String
    ): Result<UploadSourceSummary> = request("업로드 용량 계산") {
        requireApi().getUploadSourceSummary(rootId, relativePath)
    }

    suspend fun saveUploadTarget(
        targetId: String,
        label: String,
        baseUrl: String,
        token: String?
    ): Result<UploadTarget> = command("업로드 서버 저장", query = { getUploadTargets() }) {
        requireApi().saveUploadTarget(
            targetId,
            LocalControlApi.SaveUploadTargetRequest(label, baseUrl, token)
        )
    }

    suspend fun deleteUploadTarget(targetId: String): Result<Unit> =
        commandUnit("업로드 서버 삭제", query = { getUploadTargets() }) {
            requireApi().deleteUploadTarget(targetId)
        }

    suspend fun startUpload(
        rootId: String,
        relativePath: String,
        targetId: String,
        context: com.example.jetsoncontroller.model.UploadContext? = null
    ): Result<UploadJob> = command("업로드 시작", query = { getUploadJobs() }) {
        requireApi().startUpload(
            LocalControlApi.StartUploadRequest(rootId, relativePath, targetId, context)
        )
    }

    suspend fun getUploadJobs(activeOnly: Boolean = false): Result<List<UploadJob>> =
        request(if (activeOnly) "전송 큐 조회" else "업로드 작업 조회") {
            requireApi().getUploadJobs(activeOnly)
        }

    suspend fun getUploadJob(jobId: String): Result<UploadJob> =
        request("업로드 상태 조회") { requireApi().getUploadJob(jobId) }

    suspend fun deleteUploadJob(jobId: String): Result<Unit> =
        commandUnit("업로드 기록 삭제", query = { getUploadJobs() }) {
            requireApi().deleteUploadJob(jobId, LocalControlApi.ConfirmDeletionRequest())
        }

    suspend fun cancelUpload(jobId: String): Result<UploadJob> =
        command("업로드 취소", query = { getUploadJob(jobId) }) { requireApi().cancelUpload(jobId) }

    suspend fun retryUpload(jobId: String): Result<UploadJob> =
        command("업로드 재시도", query = { getUploadJob(jobId) }) { requireApi().retryUpload(jobId) }

    suspend fun verifyUploadSource(jobId: String): Result<UploadVerification> =
        command("업로드 데이터 검증", query = { getUploadJob(jobId) }) { requireApi().verifyUploadSource(jobId) }

    suspend fun deleteUploadSource(jobId: String): Result<UploadJob> =
        command("업로드 원본 휴지통 이동", query = { observeTrashThen { getUploadJob(jobId) } },
            serverFailureMayBeApplied = true) {
            requireApi().deleteUploadSource(
                jobId,
                LocalControlApi.ConfirmDeletionRequest()
            )
        }

    suspend fun getPipelines(): Result<List<ManagedPipeline>> =
        request("자동 실행 작업 조회") { requireApi().getPipelines() }

    suspend fun surveyProjects(): Result<List<SurveyProject>> =
        request("조사 프로젝트 조회") { requireApi().surveyProjects() }.map { it.projects }

    suspend fun createSurveyProject(request: SurveyLabelMutationRequest): Result<SurveyProject> =
        command("조사 프로젝트 생성", query = { surveyProjects() }) {
            requireApi().createSurveyProject(request)
        }

    suspend fun surveySections(surveyProjectId: String): Result<List<SurveySection>> =
        request("조사 구간 조회") { requireApi().surveySections(surveyProjectId) }.map { it.sections }

    suspend fun createSurveySection(
        surveyProjectId: String,
        request: SurveyLabelMutationRequest
    ): Result<SurveySection> = command("조사 구간 생성", query = { surveySections(surveyProjectId) }) {
        requireApi().createSurveySection(surveyProjectId, request)
    }

    suspend fun pipelineRunPolicy(pipelineId: String): Result<PipelineRunPolicy> =
        request("수집 정책 조회") { requireApi().pipelineRunPolicy(pipelineId) }

    suspend fun updatePipelineRunPolicy(
        pipelineId: String,
        request: UpdatePipelineRunPolicyRequest
    ): Result<PipelineRunPolicy> = command("수집 정책 저장", query = { pipelineRunPolicy(pipelineId) }) {
        requireApi().updatePipelineRunPolicy(pipelineId, request)
    }

    suspend fun pipelinePreflight(
        pipelineId: String,
        request: PipelinePreflightRequest
    ): Result<PipelinePreflight> = request("수집 시작 전 점검") {
        requireApi().pipelinePreflight(pipelineId, request)
    }

    suspend fun contextualStart(
        pipelineId: String,
        request: ContextualStartRequest
    ): Result<ManagedPipeline> = command("조사 수집 시작", query = { getPipelines() }) {
        requireApi().contextualStart(pipelineId, request)
    }

    suspend fun pipelineRun(runId: String): Result<PipelineRun> =
        request("수집 실행 확인") { requireApi().pipelineRun(runId) }

    suspend fun discoverPipelineFolder(
        rootId: String,
        path: String
    ): Result<PipelineFolderDiscovery> = request("작업 폴더 확인") {
        requireApi().discoverPipelineFolder(DiscoverPipelineFolderRequest(rootId, path))
    }

    suspend fun registerPipelineFolder(
        rootId: String,
        path: String,
        name: String,
        autostart: Boolean
    ): Result<ManagedPipeline> = command("작업 폴더 등록", query = { getPipelines() }) {
        requireApi().registerPipelineFolder(
            RegisterPipelineFolderRequest(rootId, path, name, autostart)
        )
    }

    suspend fun registerPipeline(
        request: RegisterPipelineRequest
    ): Result<ManagedPipeline> =
        command("자동 실행 작업 등록", query = { getPipelines() }) { requireApi().registerPipeline(request) }

    suspend fun controlPipeline(
        pipelineId: String,
        action: String
    ): Result<ManagedPipeline> =
        command("자동 실행 작업 제어", query = {
            getPipelines().mapCatching { pipelines ->
                pipelines.firstOrNull { it.id == pipelineId }
                    ?: error("현재 작업 상태를 확인할 수 없습니다.")
            }
        }) {
            requireApi().controlPipeline(pipelineId, action)
        }

    suspend fun removePipeline(pipelineId: String): Result<Unit> =
        commandUnit("자동 실행 작업 등록 해제", query = { getPipelines() }) {
            requireApi().removePipeline(pipelineId)
        }

    suspend fun getSystemTime(): Result<SystemTimeStatus> =
        request("장치 시간 조회") { requireApi().getSystemTime() }

    suspend fun synchronizeSystemTime(
        mobileTimeEpochMillis: Long
    ): Result<SystemTimeStatus> = suspendResult {
        val response = withCommandRecovery("장치 시간 동기화", query = { getSystemTime() }) {
            requireApi().synchronizeSystemTime(
                LocalControlApi.SynchronizeSystemTimeRequest(mobileTimeEpochMillis)
            )
        }
        val body = runCatching { requireBody(response, "장치 시간 동기화") }
        val refreshedSession = hello()
        if (body.isSuccess) refreshedSession.getOrThrow()
        body.getOrThrow()
    }

    suspend fun getFanStatus(): Result<FanStatus> =
        request("FAN 상태 조회") { requireApi().getFanStatus() }

    suspend fun setFan(mode: String, percent: Int? = null): Result<FanStatus> =
        command("FAN 제어", query = { getFanStatus() }) {
            requireApi().setFan(LocalControlApi.SetFanRequest(mode, percent))
        }

    suspend fun getPipelineLogs(pipelineId: String): Result<PipelineLog> =
        request("실행 로그 조회") { requireApi().getPipelineLogs(pipelineId) }

    suspend fun getPipelineLogFiles(pipelineId: String): Result<PipelineLogFilesResponse> =
        request("실행 로그 파일 조회") { requireApi().getPipelineLogFiles(pipelineId) }

    suspend fun getPipelineLogChunk(
        pipelineId: String,
        logId: String,
        offset: Long,
        limit: Int
    ): Result<PipelineLogChunk> =
        request("실행 로그 내용 조회") {
            requireApi().getPipelineLogChunk(pipelineId, logId, offset, limit)
        }

    suspend fun getPipelineConfig(pipelineId: String): Result<PipelineConfigDocument> =
        request("YAML 설정 조회") { requireApi().getPipelineConfig(pipelineId) }

    suspend fun updatePipelineConfig(
        pipelineId: String,
        content: String
    ): Result<PipelineConfigDocument> =
        command("YAML 설정 저장", query = { getPipelineConfig(pipelineId) }) {
            requireApi().updatePipelineConfig(
                pipelineId,
                UpdatePipelineConfigRequest(content)
            )
        }

    suspend fun getPipelineConfigFields(
        pipelineId: String
    ): Result<PipelineConfigFieldsDocument> =
        request("작업 설정 조회") { requireApi().getPipelineConfigFields(pipelineId) }

    suspend fun updatePipelineConfigFields(
        pipelineId: String,
        revision: String,
        values: Map<String, String>
    ): Result<PipelineConfigFieldsDocument> =
        command("작업 설정 저장", query = { getPipelineConfigFields(pipelineId) }) {
            requireApi().updatePipelineConfigFields(
                pipelineId,
                UpdatePipelineConfigFieldsRequest(revision, values)
            )
        }

    suspend fun getMobileRtkRelayConfig(
        pipelineId: String
    ): Result<MobileRtkRelayConfig> =
        request("모바일 RTK 설정 조회") {
            requireApi().getMobileRtkRelayConfig(pipelineId)
        }

    suspend fun registerMobileRtkRelay(
        pipelineId: String,
        port: Int
    ): Result<MobileRtkRelayRegistration> =
        command("모바일 RTK 중계 등록", query = { getStatus() }) {
            requireApi().registerMobileRtkRelay(
                RegisterMobileRtkRelayRequest(pipelineId, port)
            )
        }

    suspend fun unregisterMobileRtkRelay(pipelineId: String): Result<Unit> =
        commandUnit("모바일 RTK 중계 해제", query = { getStatus() }) {
            requireApi().unregisterMobileRtkRelay(pipelineId)
        }

    suspend fun configureWifi(
        request: WifiProvisionRequest
    ): Result<LocalControlApi.WifiProvisionResponse> =
        command("Wi-Fi 설정", query = { getStatus() }) { requireApi().configureWifi(request) }

    private fun requireApi(): LocalControlApi =
        api ?: error("Jetson API 주소가 설정되지 않았습니다.")

    private suspend fun <T> command(
        operation: String,
        query: suspend () -> Result<*>,
        serverFailureMayBeApplied: Boolean = false,
        call: suspend () -> Response<T>
    ): Result<T> = suspendResult {
        requireBody(withCommandRecovery(operation, query, serverFailureMayBeApplied = serverFailureMayBeApplied, call = call), operation)
    }

    private suspend fun commandUnit(
        operation: String,
        query: suspend () -> Result<*>,
        call: suspend () -> Response<Unit>
    ): Result<Unit> = suspendResult {
        requireSuccess(withCommandRecovery(operation, query, allowEmptyBody = true, call = call), operation)
    }

    private suspend fun <T> withCommandRecovery(
        operation: String,
        query: suspend () -> Result<*>,
        allowEmptyBody: Boolean = false,
        serverFailureMayBeApplied: Boolean = false,
        call: suspend () -> Response<T>
    ): Response<T> {
        val commandEndpoint = endpointRevision
        fun requireCurrentEndpoint() {
            if (endpointRevision != commandEndpoint) {
                throw CancellationException("장비 주소가 변경되어 명령 결과를 다시 확인해야 합니다.")
            }
        }
        return try {
            call().also { response ->
                requireCurrentEndpoint()
                if (serverFailureMayBeApplied && response.code() >= 500) {
                    val error = IOException("$operation HTTP ${response.code()} 이후 결과를 확인할 수 없습니다.")
                    val observation = withTimeoutOrNull(COMMAND_STATE_QUERY_TIMEOUT_MILLIS) { query() }
                        ?: Result.failure<Any>(IOException("현재 상태 조회 시간이 초과되었습니다."))
                    requireCurrentEndpoint()
                    throw JetsonCommandResultUnknownException(operation, observation, error)
                }
                if (response.isSuccessful && !allowEmptyBody && response.body() == null) {
                    throw IOException("$operation 응답이 비어 있어 실행 결과를 확인할 수 없습니다.")
                }
            }
        } catch (error: IOException) {
            requireCurrentEndpoint()
            // An unverified 401, invalid response signature or lost response cannot
            // prove that a mutation was not applied. Only safe reads may retry auth.
            // A successful state query is observation, not an operation receipt.
            val observation = withTimeoutOrNull(COMMAND_STATE_QUERY_TIMEOUT_MILLIS) { query() }
                ?: Result.failure<Any>(IOException("현재 상태 조회 시간이 초과되었습니다."))
            requireCurrentEndpoint()
            ConnectionDiagnostics.record("api_result_unknown", mapOf(
                "clientId" to diagnosticClientId, "endpointGeneration" to commandEndpoint,
                "exceptionClass" to diagnosticFailure(error), "success" to observation.isSuccess,
                "outcome" to "UNKNOWN"
            ), incident = true)
            throw JetsonCommandResultUnknownException(operation, observation, error)
        }
    }

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
        val attemptedEndpoint = endpointRevision
        fun requireCurrentEndpoint() {
            if (endpointRevision != attemptedEndpoint) {
                throw CancellationException("장비 주소가 변경되었습니다.")
            }
        }
        return try {
            call().also { requireCurrentEndpoint() }
        } catch (error: Exception) {
            requireCurrentEndpoint()
            if (
                error !is JetsonSessionExpiredException &&
                error !is JetsonResponseSignatureException
            ) {
                throw error
            }
            try {
                sessionRefreshMutex.withLock {
                    requireCurrentEndpoint()
                    if (sessionRevision == attemptedRevision) {
                        hello().getOrThrow()
                    }
                }
                requireCurrentEndpoint()
                call().also { requireCurrentEndpoint() }
            } catch (retryError: Exception) {
                requireCurrentEndpoint()
                throw authenticationRecoveryException(retryError)
            }
        }
    }

    private fun authenticationRecoveryException(error: Exception): Exception = when (error) {
        is JetsonResponseSignatureException,
        is JetsonUnsignedServerErrorException -> JetsonAuthenticationRecoveryException(
            "Jetson 백엔드가 현재 앱의 응답 인증 형식과 맞지 않습니다. " +
                "Jetson 백엔드를 업데이트한 뒤 다시 연결해 주세요.",
            error
        )
        is JetsonSessionExpiredException -> JetsonAuthenticationRecoveryException(
            "Jetson과 인증 정보를 동기화하지 못했습니다. " +
                "저장된 장비를 QR로 다시 등록해 주세요.",
            error
        )
        else -> error
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
        var code: String? = null
        var current: String? = null
        val detail = response.errorBody()?.string()?.let { raw ->
            runCatching {
                val node = gson.fromJson(raw, JsonObject::class.java)?.get("detail")
                when {
                    node == null -> null
                    node.isJsonPrimitive -> node.asString
                    node.isJsonObject -> node.asJsonObject.let { objectDetail ->
                        code = objectDetail.get("code")?.takeIf { it.isJsonPrimitive }?.asString
                        current = objectDetail.get("current")?.toString()
                        objectDetail.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: objectDetail.get("detail")?.takeIf { it.isJsonPrimitive }?.asString
                    }
                    else -> null
                }
            }.getOrNull()
        }
        throw JetsonApiException(
            statusCode = response.code(),
            errorCode = code,
            currentJson = current,
            message = detail ?: "$operation 실패 (HTTP ${response.code()})"
        )
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

class JetsonApiException(
    val statusCode: Int,
    val errorCode: String? = null,
    val currentJson: String? = null,
    message: String
) : IllegalStateException(message)

private const val CAMERA_PREVIEW_WAIT_MILLIS = 1_000
// Match the existing status call budget; never leave an uncertain command waiting
// through the general client's 45-second read timeout or an unbounded auth loop.
private const val COMMAND_STATE_QUERY_TIMEOUT_MILLIS = 8_000L

class JetsonCommandResultUnknownException(
    val operation: String,
    val stateQueryResult: Result<*>,
    cause: Throwable
) : IOException(
    "$operation 실행 결과를 확인하지 못했습니다. 명령은 다시 보내지 않았습니다. " +
        if (stateQueryResult.isSuccess) {
            "현재 상태를 조회했으므로 작업 상태를 확인한 뒤 다시 시도해 주세요."
        } else {
            "현재 상태도 조회하지 못했습니다. 연결 복구 후 작업 상태를 확인해 주세요."
        },
    cause
) {
    val resultCode: String = "RESULT_UNKNOWN"
}

internal suspend fun <T> withLegacyEndpointFallback(
    call: suspend () -> T,
    fallback: suspend () -> T
): T = try {
    call()
} catch (_: JetsonEndpointUnavailableException) {
    fallback()
}
