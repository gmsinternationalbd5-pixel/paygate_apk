package com.paygate.smsfwd.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    var deviceIdProvider: () -> String = { "dev_default" }
    var mockServerEnabled: Boolean = true // Per user choice: simulated mock server default

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("X-Device-Id", deviceIdProvider())
            .header("X-App-Version", "3.0.0")
            .header("X-Payload-Version", "3")
            .build()
        chain.proceed(request)
    }

    private val mockInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.encodedPath

        if (mockServerEnabled || request.url.host.contains("mock")) {
            val responseBody = when {
                url.contains("webhook") -> {
                    """{"stored": true, "trx_id": "ACK_${System.currentTimeMillis() % 100000}"}"""
                }
                url.contains("pair") -> {
                    """{
                        "device_id": "dev_${(10000..99999).random()}",
                        "device_token": "sec_tok_${System.currentTimeMillis()}",
                        "allowed_senders": ["bKash", "NAGAD", "16216"],
                        "heartbeat_seconds": 120,
                        "wallets": [
                            {"slot": 1, "provider": "bkash", "number_masked": "017•••••78"},
                            {"slot": 2, "provider": "nagad", "number_masked": "018•••••44"}
                        ],
                        "server_time": ${System.currentTimeMillis() / 1000}
                    }""".trimIndent()
                }
                url.contains("heartbeat") -> {
                    """{
                        "server_time": ${System.currentTimeMillis() / 1000},
                        "allowed_senders": ["bKash", "NAGAD", "16216"],
                        "maintenance": false,
                        "commands": []
                    }""".trimIndent()
                }
                else -> """{"status": "ok"}"""
            }

            return@Interceptor Response.Builder()
                .code(if (url.contains("pair")) 201 else 200)
                .message("OK")
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .body(responseBody.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        chain.proceed(request)
    }

    private val baseOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(headerInterceptor)
        .addInterceptor(mockInterceptor)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var currentBaseUrl: String = ""
    @Volatile
    private var currentService: PayGateApiService? = null

    fun getService(baseUrl: String): PayGateApiService {
        val sanitized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (currentService != null && currentBaseUrl == sanitized) {
            return currentService!!
        }

        synchronized(this) {
            if (currentService != null && currentBaseUrl == sanitized) {
                return currentService!!
            }

            val retrofit = Retrofit.Builder()
                .baseUrl(sanitized)
                .client(baseOkHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            val service = retrofit.create(PayGateApiService::class.java)
            currentBaseUrl = sanitized
            currentService = service
            return service
        }
    }
}
