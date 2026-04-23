package com.example.agrosense.data.model

// Datos básicos del sensor guardados dentro del JSONB sensor_data
data class DeletedSensorData(
    val name: String,
    val device_id: String,
    val location: String?
)

// Respaldo completo de un sensor eliminado
data class DeletedSensorBackup(
    val id: Int,
    val original_id: Int,
    val sensor_data: DeletedSensorData,
    val readings_count: Int,
    val deleted_at: String,
    val expires_at: String
)

data class DeletedSensorsResponse(
    val backups: List<DeletedSensorBackup>
)

data class RestoreSensorResponse(
    val sensor: RestoredSensor
)

data class RestoredSensor(
    val id: Int,
    val device_id: String,
    val name: String,
    val location: String?,
    val api_key: String
)
