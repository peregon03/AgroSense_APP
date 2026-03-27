package com.example.agrosense.data.api

import com.example.agrosense.data.model.AlertsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT

interface AlertApi {

    @GET("alerts")
    suspend fun getAlerts(
        @Header("Authorization") token: String
    ): Response<AlertsResponse>

    @PUT("alerts/read-all")
    suspend fun markAllRead(
        @Header("Authorization") token: String
    ): Response<Unit>
}
