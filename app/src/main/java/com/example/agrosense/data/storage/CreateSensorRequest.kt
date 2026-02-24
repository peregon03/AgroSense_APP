package com.example.agrosense.data.model

data class CreateSensorRequest(
    val device_id: String,
    val name: String,
    val location: String?
)
