package com.example.agrosense.data.local.dao

import androidx.room.*
import com.example.agrosense.data.local.entity.LocalReadingEntity

@Dao
interface LocalReadingDao {

    /**
     * Lecturas para un rango de tiempo. [from] y [to] son Unix seconds.
     */
    @Query("""
        SELECT * FROM local_readings
        WHERE deviceId = :deviceId AND timestamp >= :from AND timestamp <= :to
        ORDER BY timestamp ASC
    """)
    suspend fun getForRange(deviceId: String, from: Long, to: Long): List<LocalReadingEntity>

    /**
     * IGNORE evita duplicados al re-sincronizar histórico via BLE
     * (gracias al índice único deviceId+timestamp).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(readings: List<LocalReadingEntity>)

    /**
     * Elimina lecturas más antiguas que [cutoff] (Unix seconds).
     * Llamar periódicamente para no sobrepasar el límite de 30 días.
     */
    @Query("DELETE FROM local_readings WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM local_readings WHERE deviceId = :deviceId")
    suspend fun countForDevice(deviceId: String): Int
}
