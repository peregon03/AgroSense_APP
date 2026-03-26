package com.example.agrosense.data.api

import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorReading
import retrofit2.Response
import retrofit2.http.*

// ── Modelos de respuesta ───────────────────────────────────────────────────

data class RegisterSensorRequest(
    val device_id: String,
    val name: String,
    val location: String?
)

data class RegisterSensorResponse(
    val id: Int,
    val device_id: String,
    val name: String,
    val location: String,
    val api_key: String          // ← La app usará esto para enviarlo al ESP32
)

data class SensorsListResponse(
    val sensors: List<Sensor>
)

data class ReadingsResponse(
    val readings: List<SensorReading>,
    val range: String,
    val count: Int
)

// ── Interface Retrofit ─────────────────────────────────────────────────────

interface SensorApiService {

    @POST("sensors")
    suspend fun registerSensor(
        @Header("Authorization") token: String,
        @Body body: RegisterSensorRequest
    ): Response<RegisterSensorResponse>

    @GET("sensors")
    suspend fun getSensors(
        @Header("Authorization") token: String
    ): Response<SensorsListResponse>

    @DELETE("sensors/{id}")
    suspend fun deleteSensor(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int
    ): Response<Unit>

    // range: "today" | "week" | "month" | "quarter"
    @GET("sensors/{id}/readings")
    suspend fun getReadings(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Query("range") range: String = "today"
    ): Response<ReadingsResponse>
}