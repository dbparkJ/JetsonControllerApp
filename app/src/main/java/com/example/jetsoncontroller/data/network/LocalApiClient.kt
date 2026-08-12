package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.*
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LocalApiClient(
    private val credentialStore: DeviceCredentialStore
) {
    private val gson = Gson()
    private var currentBaseUrl: String? = null
    private var api: LocalControlApi? = null
    private var bootNonce: String? = null
    private var deviceId: String? = null

    fun updateEndpoint(host: String, port: Int) {
        val url = "http://$host:$port/"
        if (currentBaseUrl == url) return
        
        currentBaseUrl = url
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LocalControlApi::class.java)
    }

    suspend fun hello(): Result<LocalControlApi.HelloResponse> {
        return runCatching {
            val response = api?.hello()
                ?: error("Local API endpoint is not configured")
            check(response.isSuccessful) {
                "Local API returned HTTP ${response.code()}"
            }
            val body = response.body()
                ?: error("Local API hello response was empty")
            bootNonce = body.bootNonce
            deviceId = body.deviceId
            body
        }
    }

    private suspend fun getSignedHeaders(method: String, path: String, body: ByteArray = byteArrayOf()): HttpAuthSigner.SignedHeaders? {
        val id = deviceId ?: return null
        val nonce = bootNonce ?: return null
        val secretHex = credentialStore.getSecret(id) ?: return null
        val secret = hexToBytes(secretHex)

        return HttpAuthSigner.sign(secret, id, nonce, method, path, body)
    }

    suspend fun getStatus(): Result<JetsonStatus> {
        return runCatching {
            val headers = getSignedHeaders("GET", "/v1/status")
                ?: error("API authentication is not initialized")
            val response = api?.getStatus(
                headers.deviceId,
                headers.requestNonce,
                headers.signature
            ) ?: error("Local API endpoint is not configured")

            check(response.isSuccessful) {
                "Local API returned HTTP ${response.code()}"
            }
            response.body() ?: error("Status response was empty")
        }
    }

    suspend fun sendCommand(command: String, body: Map<String, Any> = emptyMap()): Result<Unit> {
        return runCatching {
            val bodyBytes = gson.toJson(body).toByteArray(Charsets.UTF_8)
            val headers = getSignedHeaders(
                "POST",
                "/v1/commands/$command",
                bodyBytes
            ) ?: error("API authentication is not initialized")
            val response = api?.sendCommand(
                command,
                headers.deviceId,
                headers.requestNonce,
                headers.signature,
                body
            ) ?: error("Local API endpoint is not configured")

            check(response.isSuccessful) {
                "Local API returned HTTP ${response.code()}"
            }
        }
    }

    suspend fun getRoots(): Result<List<RemoteRoot>> {
        val headers = getSignedHeaders("GET", "/v1/fs/roots") ?: return Result.failure(Exception("Auth failed"))
        val response = api?.getRoots(headers.deviceId, headers.requestNonce, headers.signature)
            ?: return Result.failure(Exception("No API"))

        return if (response.isSuccessful) {
            Result.success(response.body() ?: emptyList())
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    }

    suspend fun listFiles(rootId: String, path: String): Result<LocalControlApi.ListFilesResponse> {
        val headers = getSignedHeaders("GET", "/v1/fs/list") ?: return Result.failure(Exception("Auth failed"))
        val response = api?.listFiles(rootId, path, headers.deviceId, headers.requestNonce, headers.signature)
            ?: return Result.failure(Exception("No API"))

        return if (response.isSuccessful) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    }

    suspend fun getUploadTargets(): Result<List<UploadTarget>> {
        val headers = getSignedHeaders("GET", "/v1/upload/targets") ?: return Result.failure(Exception("Auth failed"))
        val response = api?.getUploadTargets(headers.deviceId, headers.requestNonce, headers.signature)
            ?: return Result.failure(Exception("No API"))

        return if (response.isSuccessful) {
            Result.success(response.body() ?: emptyList())
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    }

    suspend fun startUpload(rootId: String, relativePath: String, targetId: String): Result<UploadJob> {
        val request = LocalControlApi.StartUploadRequest(rootId, relativePath, targetId)
        val bodyBytes = gson.toJson(request).toByteArray(Charsets.UTF_8)
        val headers = getSignedHeaders("POST", "/v1/uploads", bodyBytes) ?: return Result.failure(Exception("Auth failed"))
        val response = api?.startUpload(request, headers.deviceId, headers.requestNonce, headers.signature)
            ?: return Result.failure(Exception("No API"))

        return if (response.isSuccessful) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    }

    suspend fun getUploadJobs(): Result<List<UploadJob>> {
        val headers = getSignedHeaders("GET", "/v1/uploads") ?: return Result.failure(Exception("Auth failed"))
        val response = api?.getUploadJobs(headers.deviceId, headers.requestNonce, headers.signature)
            ?: return Result.failure(Exception("No API"))

        return if (response.isSuccessful) {
            Result.success(response.body() ?: emptyList())
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    }

    suspend fun getUploadJob(jobId: String): Result<UploadJob> {
        val headers = getSignedHeaders("GET", "/v1/uploads/$jobId") ?: return Result.failure(Exception("Auth failed"))
        val response = api?.getUploadJob(jobId, headers.deviceId, headers.requestNonce, headers.signature)
            ?: return Result.failure(Exception("No API"))

        return if (response.isSuccessful) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
