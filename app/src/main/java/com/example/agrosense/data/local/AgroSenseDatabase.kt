package com.example.agrosense.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.agrosense.data.local.dao.LocalReadingDao
import com.example.agrosense.data.local.dao.LocalSensorDao
import com.example.agrosense.data.local.dao.LocalUserDao
import com.example.agrosense.data.local.entity.LocalReadingEntity
import com.example.agrosense.data.local.entity.LocalSensorEntity
import com.example.agrosense.data.local.entity.LocalUserEntity

@Database(
    entities = [
        LocalUserEntity::class,
        LocalSensorEntity::class,
        LocalReadingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AgroSenseDatabase : RoomDatabase() {

    abstract fun localUserDao(): LocalUserDao
    abstract fun localSensorDao(): LocalSensorDao
    abstract fun localReadingDao(): LocalReadingDao

    companion object {
        @Volatile
        private var INSTANCE: AgroSenseDatabase? = null

        fun getInstance(context: Context): AgroSenseDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgroSenseDatabase::class.java,
                    "agrosense_local.db"
                ).build().also { INSTANCE = it }
            }
    }
}
