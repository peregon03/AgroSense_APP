package com.example.agrosense.data.model

data class Sensor(
    val id: Int,
    val device_id: String,
    val name: String,
    val location: String?,
    val api_key: String?
)
