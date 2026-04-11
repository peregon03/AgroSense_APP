package com.example.agrosense.data.api

import com.example.agrosense.data.model.PumpOverrideRequest
import com.example.agrosense.data.model.PumpOverrideResponse
import com.example.agrosense.data.model.PumpSchedule
import com.example.agrosense.data.model.PumpScheduleResponse
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorReading
import com.example.agrosense.data.model.ThresholdsRequest
import com.example.agrosense.data.model.ThresholdsResponse
import retrofit2.Response
import retrofit2.http.*

// ── Modelos de respuesta ───────────────────────────────────────────────────

data class RegisterSensorRequest(
    val device_id: String,
    val name: String,
    val location: String?
)

// El servidor devuelve { sensor: { ... } }
data class RegisterSensorResponse(
    val sensor: RegisteredSensor
)

data class RegisteredSensor(
    val id: Int,
    val device_id: String,
    val name: String,
    val location: String?,
    val api_key: String
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

    @PUT("sensors/{id}/thresholds")
    suspend fun updateThresholds(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Body body: ThresholdsRequest
    ): Response<ThresholdsResponse>

    @GET("sensors/{id}/pump-schedule")
    suspend fun getPumpSchedule(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int
    ): Response<PumpScheduleResponse>

    @PUT("sensors/{id}/pump-override")
    suspend fun setPumpOverride(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Body body: PumpOverrideRequest
    ): Response<PumpOverrideResponse>

    @PUT("sensors/{id}/pump-schedule")
    suspend fun updatePumpSchedule(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Body body: PumpSchedule
    ): Response<PumpScheduleResponse>

    // range: "today" | "week" | "month" | "quarter"
    @GET("sensors/{id}/readings")
    suspend fun getReadings(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Query("range") range: String = "today"
    ): Response<ReadingsResponse>
}
