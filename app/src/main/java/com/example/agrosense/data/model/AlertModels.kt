package com.example.agrosense.data.model

data class ThresholdsRequest(
    val temp_min: Float?,
    val temp_max: Float?,
    val air_hum_min: Float?,
    val air_hum_max: Float?,
    val co2_min: Float?,
    val co2_max: Float?,
    val methane_min: Float?,
    val methane_max: Float?
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
    val co2_min: Float?,
    val co2_max: Float?,
    val methane_min: Float?,
    val methane_max: Float?
)
