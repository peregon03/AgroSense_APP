package com.example.agrosense.data.local.dao

import androidx.room.*
import com.example.agrosense.data.local.entity.LocalUserEntity

@Dao
interface LocalUserDao {

    @Query("SELECT * FROM local_users LIMIT 1")
    suspend fun getUser(): LocalUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: LocalUserEntity)

    @Query("DELETE FROM local_users")
    suspend fun deleteUser()
}
