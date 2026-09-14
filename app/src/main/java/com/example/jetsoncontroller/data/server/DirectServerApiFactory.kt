package com.example.jetsoncontroller.data.server

import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object DirectServerApiFactory {
    fun create(profile: ServerEndpointProfile, employeeToken: String): DirectServerApi {
        val checked = profile.validated()
        require(employeeToken.startsWith("emp_") && employeeToken.none(Char::isWhitespace)) {
            "Invalid employee token"
        }
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $employeeToken")
                    .header("X-Expected-Server-Environment", checked.environment.wireValue)
                    .header("Accept", "application/json, image/*, video/*")
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(checked.baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(DirectServerApi::class.java)
    }
}
