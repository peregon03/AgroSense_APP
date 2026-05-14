package com.example.agrosense.data.model

data class SensorReading(
    val id: Int,
    val sensor_id: Int,
    val temperature:  Float?,
    val air_humidity: Float?,
    val co2:          Float?,
    val methane:      Float?,
    // Nodo 03 — sensor de suelo
    val soil_temp:    Float?,
    val soil_hum:     Float?,
    val ec:           Float?,
    val ph:           Float?,
    val nitrogen:     Float?,
    val phosphorus:   Float?,
    val potassium:    Float?,
    val created_at: String
)
