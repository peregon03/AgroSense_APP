package com.example.agrosense.data.model

data class SensorAlert(
    val id: Int,
    val sensor_id: Int,
    val sensor_name: String,
    val metric: String,      // "temperature" | "air_humidity" | "soil_humidity"
    val value: Float,
    val threshold: Float,
    val direction: String,   // "above" | "below"
    val read: Boolean,
    val created_at: String
)

data class AlertsResponse(
    val alerts: List<SensorAlert>,
    val unread_count: Int
)

data class ThresholdsRequest(
    val temp_min: Float?,
    val temp_max: Float?,
    val air_hum_min: Float?,
    val air_hum_max: Float?,
    val soil_hum_min: Float?,
    val soil_hum_max: Float?
)

data class ThresholdsResponse(
    val thresholds: ThresholdValues
)

data class ThresholdValues(
    val id: Int,
    val temp_min: Float?,
    val temp_max: Float?,
    val air_hum_min: Float?,
    val air_hum_max: Float?,
    val soil_hum_min: Float?,
    val soil_hum_max: Float?
)
