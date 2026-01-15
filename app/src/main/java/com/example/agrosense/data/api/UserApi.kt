package com.example.agrosense.data.api

import com.example.agrosense.data.model.MeResponse
import retrofit2.http.GET
import retrofit2.http.Header

interface UserApi {
    @GET("me")
    suspend fun me(@Header("Authorization") authHeader: String): MeResponse
}
