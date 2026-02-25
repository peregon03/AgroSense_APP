package com.example.agrosense.data.api

import com.example.agrosense.data.model.CreateSensorRequest
import com.example.agrosense.data.model.CreateSensorResponse
import com.example.agrosense.data.model.SensorsResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface SensorApi {

    @POST("sensors")
    suspend fun createSensor(
        @Header("Authorization") auth: String,
        @Body body: CreateSensorRequest
    ): CreateSensorResponse

    @GET("sensors")
    suspend fun listSensors(
        @Header("Authorization") auth: String
    ): SensorsResponse

    @DELETE("sensors/{id}")
    suspend fun deleteSensor(
        @Header("Authorization") auth: String,
        @Path("id") id: Int
    ): retrofit2.Response<Unit>
}