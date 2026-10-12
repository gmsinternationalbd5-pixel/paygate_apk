package com.example.data.remote

import com.example.data.remote.model.HeartbeatRequest
import com.example.data.remote.model.HeartbeatResponse
import com.example.data.remote.model.PairRequest
import com.example.data.remote.model.PairResponse
import com.example.data.remote.model.WebhookRequest
import com.example.data.remote.model.WebhookResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface PayGateApiService {

    @POST("v1/sms/webhook")
    suspend fun postSms(
        @Header("Authorization") bearerToken: String,
        @Body request: WebhookRequest
    ): Response<WebhookResponse>

    @POST("v1/devices/pair")
    suspend fun pairDevice(
        @Body request: PairRequest
    ): Response<PairResponse>

    @POST("v1/devices/heartbeat")
    suspend fun sendHeartbeat(
        @Header("Authorization") bearerToken: String,
        @Body request: HeartbeatRequest
    ): Response<HeartbeatResponse>
}
