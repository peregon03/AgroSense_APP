package com.example.agrosense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lectura histórica almacenada localmente.
 * El campo [timestamp] viene del NTP del ESP32 en segundos Unix.
 * El índice único (deviceId + timestamp) evita duplicados al re-sincronizar el histórico por BLE.
 */
@Entity(
    tableName = "local_readings",
    indices = [Index(value = ["deviceId", "timestamp"], unique = true)]
)
data class LocalReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val timestamp: Long,            // Unix seconds (del NTP del ESP32)
    val temperature: Float?,
    val airHumidity: Float?,
    val co2: Float?,
    val methane: Float?
)
