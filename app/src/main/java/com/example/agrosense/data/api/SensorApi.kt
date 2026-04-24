package com.example.agrosense.data.api

import com.example.agrosense.data.model.ActionLogsResponse
import com.example.agrosense.data.model.DeletedSensorsResponse
import com.example.agrosense.data.model.IrrigationScheduleRequest
import com.example.agrosense.data.model.SensorShareResponse
import com.example.agrosense.data.model.SensorSharesResponse
import com.example.agrosense.data.model.SharePermissionsRequest
import com.example.agrosense.data.model.ShareSensorRequest
import com.example.agrosense.data.model.SharedSensorsResponse
import com.example.agrosense.data.model.RestoreSensorResponse
import com.example.agrosense.data.model.IrrigationScheduleResponse
import com.example.agrosense.data.model.IrrigationSchedulesResponse
import com.example.agrosense.data.model.PumpAckStatusResponse
import com.example.agrosense.data.model.PumpOverrideRequest
import com.example.agrosense.data.model.PumpOverrideResponse
import com.example.agrosense.data.model.Sensor
import com.example.agrosense.data.model.SensorReading
import com.example.agrosense.data.model.ThresholdsRequest
import com.example.agrosense.data.model.ThresholdsResponse
import com.example.agrosense.data.model.ToggleScheduleRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

// ── Modelos de respuesta ───────────────────────────────────────────────────

data class RegisterSensorRequest(
    val device_id: String,
    val name: String,
    val location: String?
)

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

    // ── Programaciones de riego (múltiples) ───────────────────────────────

    @GET("sensors/{id}/pump-schedules")
    suspend fun getIrrigationSchedules(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int
    ): Response<IrrigationSchedulesResponse>

    @POST("sensors/{id}/pump-schedules")
    suspend fun createIrrigationSchedule(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Body body: IrrigationScheduleRequest
    ): Response<IrrigationScheduleResponse>

    @PUT("sensors/{id}/pump-schedules/{scheduleId}")
    suspend fun updateIrrigationSchedule(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Path("scheduleId") scheduleId: Int,
        @Body body: IrrigationScheduleRequest
    ): Response<IrrigationScheduleResponse>

    @PATCH("sensors/{id}/pump-schedules/{scheduleId}/toggle")
    suspend fun toggleIrrigationSchedule(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Path("scheduleId") scheduleId: Int,
        @Body body: ToggleScheduleRequest
    ): Response<IrrigationScheduleResponse>

    @DELETE("sensors/{id}/pump-schedules/{scheduleId}")
    suspend fun deleteIrrigationSchedule(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Path("scheduleId") scheduleId: Int
    ): Response<Unit>

    // ── Control manual de bomba ────────────────────────────────────────────

    @PUT("sensors/{id}/pump-override")
    suspend fun setPumpOverride(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Body body: PumpOverrideRequest
    ): Response<PumpOverrideResponse>

    @GET("sensors/{id}/pump-ack-status")
    suspend fun getPumpAckStatus(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int
    ): Response<PumpAckStatusResponse>

    // ── Compartir sensor ───────────────────────────────────────────────────

    @GET("sensors/shared-with-me")
    suspend fun getSharedWithMe(
        @Header("Authorization") token: String
    ): Response<SharedSensorsResponse>

    @GET("sensors/{id}/shares")
    suspend fun getSensorShares(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int
    ): Response<SensorSharesResponse>

    @POST("sensors/{id}/share")
    suspend fun shareSensor(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Body body: ShareSensorRequest
    ): Response<SensorShareResponse>

    @PUT("sensors/{id}/shares/{shareId}")
    suspend fun updateSharePermissions(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Path("shareId") shareId: Int,
        @Body body: SharePermissionsRequest
    ): Response<SensorShareResponse>

    @DELETE("sensors/{id}/shares/{shareId}")
    suspend fun revokeShare(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Path("shareId") shareId: Int
    ): Response<Unit>

    // ── Historial de acciones ──────────────────────────────────────────────

    @GET("sensors/{id}/logs")
    suspend fun getActionLogs(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<ActionLogsResponse>

    // ── Sensores eliminados / respaldos ───────────────────────────────────

    @GET("sensors/deleted")
    suspend fun getDeletedSensors(
        @Header("Authorization") token: String
    ): Response<DeletedSensorsResponse>

    @POST("sensors/deleted/{backupId}/restore")
    suspend fun restoreSensor(
        @Header("Authorization") token: String,
        @Path("backupId") backupId: Int
    ): Response<RestoreSensorResponse>

    @DELETE("sensors/deleted/{backupId}")
    suspend fun permanentlyDeleteBackup(
        @Header("Authorization") token: String,
        @Path("backupId") backupId: Int
    ): Response<Unit>

    // ── Informe PDF ────────────────────────────────────────────────────────

    @Streaming
    @GET("sensors/{id}/report")
    suspend fun downloadReport(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Query("from") from: String,
        @Query("to") to: String
    ): Response<ResponseBody>

    // ── Lecturas / gráficas ────────────────────────────────────────────────

    // range: "today" | "week" | "month" | "quarter"
    @GET("sensors/{id}/readings")
    suspend fun getReadings(
        @Header("Authorization") token: String,
        @Path("id") sensorId: Int,
        @Query("range") range: String = "today"
    ): Response<ReadingsResponse>
}
