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

    @GET("/v1/capabilities")
    suspend fun getCapabilities(): Response<CapabilitiesResponse>

    @POST("/v1/commands/{command}")
    suspend fun sendCommand(
        @Path("command") command: String,
        @Body body: Map<String, Any>
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

    @GET("/v1/fs/workspaces")
    suspend fun getWorkspaceRoots(): Response<List<RemoteRoot>>

    @GET("/v1/fs/workspace/list")
    suspend fun listWorkspaceFiles(
        @Query("root") rootId: String,
        @Query("path") path: String
    ): Response<ListFilesResponse>

    @GET("/v1/upload/targets")
    suspend fun getUploadTargets(): Response<List<UploadTarget>>

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

    @POST("/v1/uploads/{jobId}/cancel")
    suspend fun cancelUpload(
        @Path("jobId") jobId: String
    ): Response<UploadJob>

    @POST("/v1/uploads/{jobId}/retry")
    suspend fun retryUpload(
        @Path("jobId") jobId: String
    ): Response<UploadJob>

    @GET("/v1/pipelines")
    suspend fun getPipelines(): Response<List<ManagedPipeline>>

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

    @GET("/v1/pipelines/{pipelineId}/config")
    suspend fun getPipelineConfig(
        @Path("pipelineId") pipelineId: String
    ): Response<PipelineConfigDocument>

    @PUT("/v1/pipelines/{pipelineId}/config")
    suspend fun updatePipelineConfig(
        @Path("pipelineId") pipelineId: String,
        @Body request: UpdatePipelineConfigRequest
    ): Response<PipelineConfigDocument>

    @POST("/v1/network/wifi")
    suspend fun configureWifi(
        @Body request: WifiProvisionRequest
    ): Response<WifiProvisionResponse>

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

    data class CapabilitiesResponse(
        val status: Boolean = true,
        val commands: List<String> = emptyList(),
        val systemControlConfigured: Boolean = false,
        val powerCommandsEnabled: Boolean = false,
        val fileBrowsing: Boolean = true,
        val uploads: Boolean = true,
        val wifiProvisioning: Boolean = true,
        val pipelines: Boolean = false
    )

    data class WifiProvisionResponse(
        val accepted: Boolean,
        val state: String,
        val ssid: String?
    )
}
