package com.example.agrosense.data.local.dao

import androidx.room.*
import com.example.agrosense.data.local.entity.LocalSensorEntity

@Dao
interface LocalSensorDao {

    @Query("SELECT * FROM local_sensors ORDER BY name ASC")
    suspend fun getAll(): List<LocalSensorEntity>

    @Query("SELECT * FROM local_sensors WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): LocalSensorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(sensor: LocalSensorEntity)

    @Query("DELETE FROM local_sensors WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)
}
