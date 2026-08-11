package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.model.*
import retrofit2.Response
import retrofit2.http.*

interface LocalControlApi {

    @GET("/v1/hello")
    suspend fun hello(): Response<HelloResponse>

    @GET("/v1/status")
    suspend fun getStatus(
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Request-Nonce") nonce: String,
        @Header("X-Signature") signature: String
    ): Response<JetsonStatus>

    @POST("/v1/commands/{command}")
    suspend fun sendCommand(
        @Path("command") command: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Request-Nonce") nonce: String,
        @Header("X-Signature") signature: String,
        @Body body: Map<String, Any>
    ): Response<Unit>

    @GET("/v1/fs/roots")
    suspend fun getRoots(
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Request-Nonce") nonce: String,
        @Header("X-Signature") signature: String
    ): Response<List<RemoteRoot>>

    @GET("/v1/fs/list")
    suspend fun listFiles(
        @Query("root") rootId: String,
        @Query("path") path: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Request-Nonce") nonce: String,
        @Header("X-Signature") signature: String
    ): Response<ListFilesResponse>

    @GET("/v1/upload/targets")
    suspend fun getUploadTargets(
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Request-Nonce") nonce: String,
        @Header("X-Signature") signature: String
    ): Response<List<UploadTarget>>

    @POST("/v1/uploads")
    suspend fun startUpload(
        @Body request: StartUploadRequest,
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Request-Nonce") nonce: String,
        @Header("X-Signature") signature: String
    ): Response<UploadJob>

    @GET("/v1/uploads")
    suspend fun getUploadJobs(
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Request-Nonce") nonce: String,
        @Header("X-Signature") signature: String
    ): Response<List<UploadJob>>

    @GET("/v1/uploads/{jobId}")
    suspend fun getUploadJob(
        @Path("jobId") jobId: String,
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Request-Nonce") nonce: String,
        @Header("X-Signature") signature: String
    ): Response<UploadJob>

    data class HelloResponse(
        val apiVersion: Int,
        val deviceId: String,
        val deviceName: String,
        val bootNonce: String
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
}
