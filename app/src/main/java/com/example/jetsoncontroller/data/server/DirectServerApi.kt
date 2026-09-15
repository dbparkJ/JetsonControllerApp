package com.example.jetsoncontroller.data.server

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DirectServerApi {
    @GET("v1/server/capabilities")
    suspend fun capabilities(): Response<ServerCapabilities>

    @GET("v1/server/jobs")
    suspend fun jobs(
        @Query("projectId") projectId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<ServerJobsResponse>

    @GET("v1/server/jobs/{sessionId}/files")
    suspend fun files(
        @Path("sessionId") sessionId: String,
        @Query("projectId") projectId: String,
        @Query("path") path: String = ""
    ): Response<ServerFilesResponse>

    @GET("v1/server/jobs/{sessionId}/preview")
    suspend fun preview(
        @Path("sessionId") sessionId: String,
        @Query("projectId") projectId: String,
        @Query("path") path: String
    ): Response<ResponseBody>

    @GET("v1/server/jobs/{sessionId}/receipt")
    suspend fun receipt(
        @Path("sessionId") sessionId: String,
        @Query("projectId") projectId: String
    ): Response<ServerReceipt>

    @GET("v1/server/trash")
    suspend fun trash(
        @Query("projectId") projectId: String,
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0
    ): Response<ServerTrashResponse>

    @POST("v1/server/trash/empty")
    suspend fun emptyTrash(
        @Query("projectId") projectId: String,
        @Body request: ServerEmptyTrashRequest
    ): Response<ServerEmptyTrashResponse>

    @DELETE("v1/server/jobs/{sessionId}")
    suspend fun moveToTrash(
        @Path("sessionId") sessionId: String,
        @Query("projectId") projectId: String
    ): Response<ServerLifecycleResponse>

    @POST("v1/server/trash/{sessionId}/restore")
    suspend fun restore(
        @Path("sessionId") sessionId: String,
        @Query("projectId") projectId: String
    ): Response<ServerLifecycleResponse>
}
