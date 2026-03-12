package com.example.agrosense.data.model

data class SensorReading(
    val id: Int,
    val sensor_id: Int,
    val temperature: Float?,
    val air_humidity: Float?,
    val soil_humidity: Float?,
    val created_at: String
)

data class ReadingsResponse(
    val readings: List<SensorReading>
)