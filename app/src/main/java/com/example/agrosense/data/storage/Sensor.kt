package com.example.agrosense.data.model

data class Sensor(
    val id: Int,
    val device_id: String,
    val name: String,
    val location: String?,
    val api_key: String?,
    val temp_min: Float? = null,
    val temp_max: Float? = null,
    val air_hum_min: Float? = null,
    val air_hum_max: Float? = null,
    val soil_hum_min: Float? = null,
    val soil_hum_max: Float? = null
)
