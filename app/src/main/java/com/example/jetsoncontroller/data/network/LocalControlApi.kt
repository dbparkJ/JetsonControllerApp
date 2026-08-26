package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface LocalControlApi {

    @GET("/v1/hello")
    suspend fun hello(): Response<HelloResponse>

    @GET("/v1/status")
    suspend fun getStatus(): Response<JetsonStatus>

    @GET("/v1/camera/preview/frame")
    suspend fun getCameraPreviewFrame(): Response<ResponseBody>

    @GET("/v1/capabilities")
    suspend fun getCapabilities(): Response<CapabilitiesResponse>

    @POST("/v1/commands/{command}")
    suspend fun sendCommand(
        @Path("command") command: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @GET("/v1/fs/roots")
    suspend fun getRoots(): Response<List<RemoteRoot>>

    @GET("/v1/fs/list")
    suspend fun listFiles(
        @Query("root") rootId: String,
        @Query("path") path: String
    ): Response<ListFilesResponse>

    @GET("/v1/fs/file")
    suspend fun getFile(
        @Query("root") rootId: String,
        @Query("path") path: String
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", path = "/v1/fs/entry", hasBody = true)
    suspend fun deleteStorageEntry(
        @Query("root") rootId: String,
        @Query("path") path: String,
        @Body request: ConfirmDeletionRequest
    ): Response<DeviceStorageDeletion>

    @GET("/v1/fs/workspaces")
    suspend fun getWorkspaceRoots(): Response<List<RemoteRoot>>

    @GET("/v1/fs/workspace/list")
    suspend fun listWorkspaceFiles(
        @Query("root") rootId: String,
        @Query("path") path: String
    ): Response<ListFilesResponse>

    @GET("/v1/fs/workspace/file")
    suspend fun getWorkspaceFile(
        @Query("root") rootId: String,
        @Query("path") path: String
    ): Response<ResponseBody>

    @GET("/v1/upload/targets")
    suspend fun getUploadTargets(): Response<List<UploadTarget>>

    @GET("/v1/upload/library/sessions")
    suspend fun getUploadLibrarySessions(
        @Query("target") targetId: String,
        @Query("offset") offset: Int = 0
    ): Response<UploadLibrarySessionsResponse>

    @GET("/v1/upload/library/files")
    suspend fun getUploadLibraryFiles(
        @Query("target") targetId: String,
        @Query("session") sessionId: String,
        @Query("path") path: String
    ): Response<UploadLibraryFilesResponse>

    @GET("/v1/upload/library/file")
    suspend fun getUploadLibraryFile(
        @Query("target") targetId: String,
        @Query("session") sessionId: String,
        @Query("path") path: String
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", path = "/v1/upload/library/sessions/{sessionId}", hasBody = true)
    suspend fun deleteUploadLibrarySession(
        @Path("sessionId") sessionId: String,
        @Query("target") targetId: String,
        @Body request: ConfirmDeletionRequest
    ): Response<UploadDeletionResponse>

    @GET("/v1/upload/source-summary")
    suspend fun getUploadSourceSummary(
        @Query("root") rootId: String,
        @Query("path") path: String
    ): Response<UploadSourceSummary>

    @PUT("/v1/upload/targets/{targetId}")
    suspend fun saveUploadTarget(
        @Path("targetId") targetId: String,
        @Body request: SaveUploadTargetRequest
    ): Response<UploadTarget>

    @DELETE("/v1/upload/targets/{targetId}")
    suspend fun deleteUploadTarget(
        @Path("targetId") targetId: String
    ): Response<Unit>

    @POST("/v1/uploads")
    suspend fun startUpload(
        @Body request: StartUploadRequest
    ): Response<UploadJob>

    @GET("/v1/uploads")
    suspend fun getUploadJobs(
        @Query("active") activeOnly: Boolean = false
    ): Response<List<UploadJob>>

    @GET("/v1/uploads/{jobId}")
    suspend fun getUploadJob(
        @Path("jobId") jobId: String
    ): Response<UploadJob>

    @HTTP(method = "DELETE", path = "/v1/uploads/{jobId}", hasBody = true)
    suspend fun deleteUploadJob(
        @Path("jobId") jobId: String,
        @Body request: ConfirmDeletionRequest
    ): Response<Unit>

    @POST("/v1/uploads/{jobId}/cancel")
    suspend fun cancelUpload(
        @Path("jobId") jobId: String
    ): Response<UploadJob>

    @POST("/v1/uploads/{jobId}/retry")
    suspend fun retryUpload(
        @Path("jobId") jobId: String
    ): Response<UploadJob>

    @POST("/v1/uploads/{jobId}/verify")
    suspend fun verifyUploadSource(
        @Path("jobId") jobId: String
    ): Response<UploadVerification>

    @HTTP(method = "DELETE", path = "/v1/uploads/{jobId}/source", hasBody = true)
    suspend fun deleteUploadSource(
        @Path("jobId") jobId: String,
        @Body request: ConfirmDeletionRequest
    ): Response<UploadJob>

    @GET("/v1/pipelines")
    suspend fun getPipelines(): Response<List<ManagedPipeline>>

    @POST("/v1/pipelines/discover-folder")
    suspend fun discoverPipelineFolder(
        @Body request: DiscoverPipelineFolderRequest
    ): Response<PipelineFolderDiscovery>

    @POST("/v1/pipelines/register-folder")
    suspend fun registerPipelineFolder(
        @Body request: RegisterPipelineFolderRequest
    ): Response<ManagedPipeline>

    @POST("/v1/pipelines")
    suspend fun registerPipeline(
        @Body request: RegisterPipelineRequest
    ): Response<ManagedPipeline>

    @POST("/v1/pipelines/{pipelineId}/{action}")
    suspend fun controlPipeline(
        @Path("pipelineId") pipelineId: String,
        @Path("action") action: String
    ): Response<ManagedPipeline>

    @DELETE("/v1/pipelines/{pipelineId}")
    suspend fun removePipeline(
        @Path("pipelineId") pipelineId: String
    ): Response<Unit>

    @GET("/v1/pipelines/{pipelineId}/logs")
    suspend fun getPipelineLogs(
        @Path("pipelineId") pipelineId: String,
        @Query("lines") lines: Int = 300
    ): Response<PipelineLog>

    @GET("/v1/pipelines/{pipelineId}/log-files")
    suspend fun getPipelineLogFiles(
        @Path("pipelineId") pipelineId: String
    ): Response<PipelineLogFilesResponse>

    @GET("/v1/pipelines/{pipelineId}/log-files/{logId}")
    suspend fun getPipelineLogChunk(
        @Path("pipelineId") pipelineId: String,
        @Path("logId") logId: String,
        @Query("offset") offset: Long,
        @Query("limit") limit: Int
    ): Response<PipelineLogChunk>

    @GET("/v1/pipelines/{pipelineId}/config")
    suspend fun getPipelineConfig(
        @Path("pipelineId") pipelineId: String
    ): Response<PipelineConfigDocument>

    @PUT("/v1/pipelines/{pipelineId}/config")
    suspend fun updatePipelineConfig(
        @Path("pipelineId") pipelineId: String,
        @Body request: UpdatePipelineConfigRequest
    ): Response<PipelineConfigDocument>

    @GET("/v1/pipelines/{pipelineId}/config/fields")
    suspend fun getPipelineConfigFields(
        @Path("pipelineId") pipelineId: String
    ): Response<PipelineConfigFieldsDocument>

    @PATCH("/v1/pipelines/{pipelineId}/config/fields")
    suspend fun updatePipelineConfigFields(
        @Path("pipelineId") pipelineId: String,
        @Body request: UpdatePipelineConfigFieldsRequest
    ): Response<PipelineConfigFieldsDocument>

    @POST("/v1/network/wifi")
    suspend fun configureWifi(
        @Body request: WifiProvisionRequest
    ): Response<WifiProvisionResponse>

    @GET("/v1/network/wifi/status")
    suspend fun getWifiProvisionStatus(): Response<WifiProvisionStatus>

    @GET("/v1/system/time")
    suspend fun getSystemTime(): Response<SystemTimeStatus>

    @PUT("/v1/system/time")
    suspend fun synchronizeSystemTime(
        @Body request: SynchronizeSystemTimeRequest
    ): Response<SystemTimeStatus>

    @GET("/v1/system/fan")
    suspend fun getFanStatus(): Response<FanStatus>

    @PUT("/v1/system/fan")
    suspend fun setFan(
        @Body request: SetFanRequest
    ): Response<FanStatus>

    data class HelloResponse(
        val apiVersion: Int,
        val deviceId: String,
        val deviceName: String,
        val bootNonce: String,
        val serverTimeEpochSeconds: Long,
        val authScheme: String,
        val tlsCertificateSha256: String,
        val helloProof: String
    )

    data class ListFilesResponse(
        val root: String,
        val path: String,
        val entries: List<RemoteFileEntry>
    )

    data class StartUploadRequest(
        val rootId: String,
        val relativePath: String,
        val targetId: String
    )

    data class SaveUploadTargetRequest(
        val label: String,
        val baseUrl: String,
        val token: String? = null
    )

    data class ConfirmDeletionRequest(
        val confirmed: Boolean = true
    )

    data class SynchronizeSystemTimeRequest(
        val mobileTimeEpochMillis: Long
    )

    data class SetFanRequest(
        val mode: String,
        val percent: Int? = null
    )

    data class CapabilitiesResponse(
        val status: Boolean = true,
        val commands: List<String> = emptyList(),
        val systemControlConfigured: Boolean = false,
        val powerCommandsEnabled: Boolean = false,
        val fileBrowsing: Boolean = true,
        val uploads: Boolean = true,
        val wifiProvisioning: Boolean = true,
        val pipelines: Boolean = false,
        val pipelineFolderRegistration: Boolean = false,
        val mobileTimeSync: Boolean = false,
        val fanControl: Boolean = false
    )

    data class WifiProvisionResponse(
        val accepted: Boolean,
        val state: String,
        val ssid: String?
    )
}
