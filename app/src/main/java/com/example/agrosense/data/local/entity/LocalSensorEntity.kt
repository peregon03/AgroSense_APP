package com.example.agrosense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_sensors")
data class LocalSensorEntity(
    @PrimaryKey val deviceId: String,   // MAC address del ESP32
    val name: String,
    val location: String?,
    val apiKey: String?                 // null en modo local puro; puede tener valor si se registró en la nube alguna vez
)
